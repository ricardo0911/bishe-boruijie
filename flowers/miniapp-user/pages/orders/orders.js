const { get, post } = require("../../utils/request");
const {
  formatPrice,
  getStatusClass,
  getStatusLabel,
  getUserId,
} = require("../../utils/format");

Page({
  data: {
    loading: true,
    currentStatus: "",
    tabs: [
      { key: "", label: "全部" },
      { key: "LOCKED", label: "待支付" },
      { key: "PAID", label: "已支付" },
      { key: "COMPLETED", label: "已完成" },
      { key: "CANCELLED", label: "已取消" },
    ],
    allOrders: [],
    orders: [],
    actionOrderNo: "",
  },

  onShow() {
    this.loadOrders();
  },

  onPullDownRefresh() {
    this.loadOrders().finally(() => wx.stopPullDownRefresh());
  },

  async loadOrders() {
    this.setData({ loading: true });
    try {
      const res = await get(`/orders/user/${getUserId()}/details?limit=50`);
      if (!res.success || !Array.isArray(res.data)) {
        this.setData({ loading: false, allOrders: [], orders: [] });
        wx.showToast({ title: res.message || "加载失败", icon: "none" });
        return;
      }
      const allOrders = res.data.map((item) => this.normalizeOrder(item));
      this.setData({ loading: false, allOrders }, () => this.applyFilter());
    } catch (err) {
      this.setData({ loading: false, allOrders: [], orders: [] });
      wx.showToast({ title: "网络错误", icon: "none" });
    }
  },

  normalizeOrder(item) {
    const items = Array.isArray(item.items)
      ? item.items.map((line) => ({
          productTitle: line.productTitle,
          quantity: Number(line.quantity || 0),
          unitPrice: formatPrice(line.unitPrice),
        }))
      : [];
    return {
      orderNo: item.orderNo,
      status: item.status,
      statusLabel: getStatusLabel(item.status),
      statusClass: getStatusClass(item.status),
      totalAmount: formatPrice(item.totalAmount),
      items,
      createdAt: item.createdAt || "",
    };
  },

  applyFilter() {
    const status = this.data.currentStatus;
    let orders = this.data.allOrders;
    if (status) {
      orders = orders.filter((item) => {
        if (status === "CANCELLED") {
          return item.status === "CANCELLED" || item.status === "REFUNDED";
        }
        return item.status === status;
      });
    }
    this.setData({ orders });
  },

  onTapTab(e) {
    const key = e.currentTarget.dataset.key || "";
    this.setData({ currentStatus: key }, () => this.applyFilter());
  },

  async onPay(e) {
    const orderNo = e.currentTarget.dataset.no;
    if (!orderNo || this.data.actionOrderNo) return;

    const confirmRes = await new Promise((resolve) => {
      wx.showModal({
        title: "模拟支付",
        content: "这是演示版支付，不会真实扣款。确认后订单将变为已支付。",
        confirmText: "确认支付",
        cancelText: "取消",
        success: resolve,
        fail: () => resolve({ confirm: false }),
      });
    });

    if (!confirmRes.confirm) return;

    this.setData({ actionOrderNo: orderNo });
    try {
      const res = await post(`/orders/${orderNo}/pay`, {
        paymentChannel: "MOCK_WECHAT",
        paymentNo: `MOCK_${Date.now()}`,
      });
      if (res.success) {
        wx.showToast({ title: "模拟支付成功", icon: "success" });
        this.loadOrders();
      } else {
        wx.showToast({ title: res.message || "支付失败", icon: "none" });
      }
    } catch (err) {
      wx.showToast({ title: "网络错误", icon: "none" });
    } finally {
      this.setData({ actionOrderNo: "" });
    }
  },

  async onCancel(e) {
    const orderNo = e.currentTarget.dataset.no;
    if (!orderNo || this.data.actionOrderNo) return;
    this.setData({ actionOrderNo: orderNo });
    try {
      const res = await post(`/orders/${orderNo}/cancel`, { reason: "用户取消" });
      if (res.success) {
        wx.showToast({ title: "操作成功", icon: "success" });
        this.loadOrders();
      } else {
        wx.showToast({ title: res.message || "操作失败", icon: "none" });
      }
    } catch (err) {
      wx.showToast({ title: "网络错误", icon: "none" });
    } finally {
      this.setData({ actionOrderNo: "" });
    }
  },
});
