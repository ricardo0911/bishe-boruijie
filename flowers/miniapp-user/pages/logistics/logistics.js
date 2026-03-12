const { get, post } = require("../../utils/request");
const { getStatusLabel } = require("../../utils/format");
const { requireLogin } = require("../../utils/auth");
const { resolveDeliveryModeLabel, resolveDeliverySlotLabel, stripLegacyDeliveryRemark } = require("../../utils/delivery");

const REFUNDABLE_STATUSES = ["PAID", "CONFIRMED", "COMPLETED"];
const SHIPPED_LIKE_STATUSES = ["CONFIRMED", "SHIPPED", "COMPLETED", "REFUND_REQUESTED", "REFUNDING", "REFUND_FAILED", "REFUNDED"];

function normalizeOrderStatus(status) {
  return status === "LOCKED" ? "PENDING_PAY" : (status || "");
}

function formatDateTime(value) {
  if (!value) return "";
  const text = String(value).replace("T", " ");
  return text.length > 19 ? text.slice(0, 19) : text;
}

function buildTimeline(order) {
  const shipped = Boolean(order.shippedAt) || ["CONFIRMED", "SHIPPED", "COMPLETED"].includes(order.status);
  const completed = order.status === "COMPLETED";
  return [
    {
      key: "paid",
      title: "订单支付",
      time: order.payTime || order.createdAt,
      desc: "订单已支付，商家正在备货。",
      active: true,
      current: !shipped,
    },
    {
      key: "ship",
      title: "商家发货",
      time: order.shippedAt,
      desc: shipped
        ? (order.trackingNo ? `${order.trackingCompany}｜${order.trackingNo}` : "商家已发货，暂未录入运单号")
        : "订单仍在备货中，请耐心等待。",
      active: shipped,
      current: shipped && !completed,
    },
    {
      key: "done",
      title: "订单完成",
      time: order.completedAt,
      desc: completed ? "订单已完成，感谢您的购买。" : "确认收货后，这里会更新为已完成。",
      active: completed,
      current: completed,
    },
  ];
}

function normalizeOrder(data) {
  const status = normalizeOrderStatus(data.status);
  const deliveryModeLabel = resolveDeliveryModeLabel(data.deliveryMode, data.remark);
  const deliverySlotLabel = resolveDeliverySlotLabel(data.deliverySlot, data.remark);
  return {
    orderNo: data.orderNo || "",
    status,
    statusLabel: getStatusLabel(status),
    trackingCompany: data.trackingCompany || "同城配送",
    trackingNo: data.trackingNo || "",
    totalAmount: data.totalAmount || "0.00",
    paymentAmount: data.paymentAmount || data.totalAmount || "0.00",
    shippedAt: formatDateTime(data.shippedAt),
    payTime: formatDateTime(data.payTime || data.paidAt),
    completedAt: formatDateTime(data.completedAt),
    createdAt: formatDateTime(data.createdAt),
    receiverName: data.receiverName || "",
    receiverPhone: data.receiverPhone || "",
    receiverAddress: data.receiverAddress || "",
    deliveryMode: data.deliveryMode || "",
    deliverySlot: data.deliverySlot || "",
    deliveryModeLabel,
    deliverySlotLabel,
    remark: stripLegacyDeliveryRemark(data.remark || ""),
    canRefund: REFUNDABLE_STATUSES.includes(status),
  };
}

Page({
  data: {
    loading: true,
    orderNo: "",
    order: null,
    timeline: [],
    actionLoading: false,
    showRefundDialog: false,
    refundReason: "",
  },

  onLoad(options) {
    const orderNo = options.orderNo || "";
    if (!requireLogin(`/pages/logistics/logistics?orderNo=${encodeURIComponent(orderNo)}`)) {
      return;
    }
    if (!orderNo) {
      wx.showToast({ title: "订单号缺失", icon: "none" });
      wx.navigateBack();
      return;
    }
    this.setData({ orderNo });
    this.loadLogistics();
  },

  onPullDownRefresh() {
    this.loadLogistics().finally(() => wx.stopPullDownRefresh());
  },

  async loadLogistics() {
    this.setData({ loading: true });
    try {
      const res = await get(`/orders/${this.data.orderNo}`);
      if (!res.success || !res.data) {
        wx.showToast({ title: res.message || "加载失败", icon: "none" });
        this.setData({
          loading: false,
          order: null,
          timeline: [],
          actionLoading: false,
          showRefundDialog: false,
          refundReason: "",
        });
        return;
      }

      const order = normalizeOrder(res.data);
      this.setData({
        loading: false,
        order,
        timeline: buildTimeline(order),
        actionLoading: false,
      });
    } catch (err) {
      wx.showToast({ title: "网络错误", icon: "none" });
      this.setData({
        loading: false,
        order: null,
        timeline: [],
        actionLoading: false,
      });
    }
  },

  onCopyTrackingNo() {
    const trackingNo = this.data.order && this.data.order.trackingNo;
    if (!trackingNo) {
      wx.showToast({ title: "暂无运单号", icon: "none" });
      return;
    }
    wx.setClipboardData({
      data: trackingNo,
      success: () => wx.showToast({ title: "运单号已复制", icon: "success" }),
    });
  },

  onRefund() {
    if (this.data.actionLoading || !this.data.order || !this.data.order.canRefund) return;
    this.setData({
      showRefundDialog: true,
      refundReason: "",
    });
  },

  onInputRefundReason(e) {
    this.setData({ refundReason: e.detail.value || "" });
  },

  onCloseRefundDialog() {
    if (this.data.actionLoading) return;
    this.setData({
      showRefundDialog: false,
      refundReason: "",
    });
  },

  async onSubmitRefund() {
    if (this.data.actionLoading || !this.data.order || !this.data.order.canRefund) return;

    const reason = (this.data.refundReason || "").trim();
    if (!reason) {
      wx.showToast({ title: "请填写退款理由", icon: "none" });
      return;
    }

    this.setData({ actionLoading: true });
    try {
      const res = await post("/after-sales", {
        orderNo: this.data.orderNo,
        refundAmount: this.data.order.paymentAmount || this.data.order.totalAmount || "0.00",
        reason,
      });

      if (res.success) {
        this.setData({
          showRefundDialog: false,
          refundReason: "",
        });
        wx.showToast({ title: "退款申请已提交", icon: "success" });
        setTimeout(() => this.loadLogistics(), 500);
      } else {
        wx.showToast({ title: res.message || "操作失败", icon: "none" });
      }
    } catch (err) {
      wx.showToast({ title: "网络错误", icon: "none" });
    } finally {
      this.setData({ actionLoading: false });
    }
  },

  noop() {},
});