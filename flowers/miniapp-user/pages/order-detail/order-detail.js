const { get, post } = require("../../utils/request");
const { formatPrice, resolveImageUrl } = require("../../utils/format");

const ORDER_STATUS = {
  LOCKED: "LOCKED",
  PENDING_PAY: "PENDING_PAY",
  PAID: "PAID",
  CONFIRMED: "CONFIRMED",
  SHIPPED: "SHIPPED",
  COMPLETED: "COMPLETED",
  CANCELLED: "CANCELLED",
  REFUNDED: "REFUNDED",
};

const STATUS_LABEL_MAP = {
  LOCKED: "待支付",
  PENDING_PAY: "待支付",
  PAID: "已支付",
  CONFIRMED: "待发货",
  SHIPPED: "待收货",
  COMPLETED: "已完成",
  CANCELLED: "已取消",
  REFUNDED: "已退款",
};

const STATUS_DESC_MAP = {
  LOCKED: "请在30分钟内完成支付，超时订单将自动取消",
  PENDING_PAY: "请在30分钟内完成支付，超时订单将自动取消",
  PAID: "商家正在准备您的订单，请耐心等待",
  CONFIRMED: "商家已确认订单，正在安排发货",
  SHIPPED: "商品已发出，请注意查收",
  COMPLETED: "订单已完成，感谢您的购买",
  CANCELLED: "订单已取消",
  REFUNDED: "订单已退款",
};

function normalizeOrderStatus(status) {
  return status === ORDER_STATUS.LOCKED ? ORDER_STATUS.PENDING_PAY : (status || "");
}

function resolveStatusLabel(status) {
  return STATUS_LABEL_MAP[status] || status || "未知";
}

function resolveStatusDesc(status) {
  return STATUS_DESC_MAP[status] || "";
}

function resolveStatusClass(status) {
  if (status === ORDER_STATUS.PAID || status === ORDER_STATUS.COMPLETED) return "success";
  if (status === ORDER_STATUS.CANCELLED || status === ORDER_STATUS.REFUNDED) return "danger";
  if (status === ORDER_STATUS.PENDING_PAY || status === ORDER_STATUS.LOCKED) return "warning";
  if (status === ORDER_STATUS.SHIPPED) return "info";
  return "default";
}

function resolveProductTitle(line) {
  if (!line) return "未命名商品";
  return line.productTitle || line.productName || line.title || (line.productId ? `商品#${line.productId}` : "未命名商品");
}

function calculateCountdown(createdAt, expireMinutes = 30) {
  if (!createdAt) return null;
  const createTime = new Date(createdAt).getTime();
  if (!Number.isFinite(createTime)) return null;
  const expireTime = createTime + expireMinutes * 60 * 1000;
  const diff = expireTime - Date.now();
  if (diff <= 0) return null;
  const minutes = Math.floor(diff / 60000);
  const seconds = Math.floor((diff % 60000) / 1000);
  return `${minutes.toString().padStart(2, "0")}:${seconds.toString().padStart(2, "0")}`;
}

Page({
  data: {
    loading: true,
    orderNo: "",
    order: null,
    countdown: null,
    actionLoading: false,
    productCoverMap: {},
  },

  countdownTimer: null,
  productCoverCache: null,

  onLoad(options) {
    const orderNo = options.orderNo || "";
    if (!orderNo) {
      wx.showToast({ title: "订单号错误", icon: "none" });
      wx.navigateBack();
      return;
    }
    this.setData({ orderNo });
    this.loadOrderDetail();
  },

  onShow() {
    if (this.data.orderNo && !this.data.loading) {
      this.loadOrderDetail();
    }
  },

  onHide() {
    this.clearCountdownTimer();
  },

  onUnload() {
    this.clearCountdownTimer();
  },

  clearCountdownTimer() {
    if (this.countdownTimer) {
      clearInterval(this.countdownTimer);
      this.countdownTimer = null;
    }
  },

  startCountdownTimer(createdAt) {
    this.clearCountdownTimer();
    const update = () => {
      const countdown = calculateCountdown(createdAt);
      if (countdown) {
        this.setData({ countdown });
      } else {
        this.setData({ countdown: null });
        this.clearCountdownTimer();
        this.loadOrderDetail();
      }
    };
    update();
    this.countdownTimer = setInterval(update, 1000);
  },

  async loadOrderDetail() {
    this.setData({ loading: true });
    try {
      await this.ensureProductCoverMap();
      const res = await get(`/orders/${this.data.orderNo}`);
      if (!res.success || !res.data) {
        wx.showToast({ title: res.message || "加载失败", icon: "none" });
        this.setData({ loading: false });
        return;
      }

      const order = this.normalizeOrder(res.data);
      this.setData({ loading: false, order });

      if (order.status === ORDER_STATUS.PENDING_PAY) {
        this.startCountdownTimer(order.createdAt);
      } else {
        this.clearCountdownTimer();
        this.setData({ countdown: null });
      }
    } catch (err) {
      wx.showToast({ title: "网络错误", icon: "none" });
      this.setData({ loading: false });
    }
  },

  normalizeOrder(item) {
    const status = normalizeOrderStatus(item.status);
    const items = Array.isArray(item.items)
      ? item.items.map((line) => ({
          productId: line.productId,
          productTitle: resolveProductTitle(line),
          quantity: Number(line.quantity || 0),
          unitPrice: formatPrice(line.unitPrice || 0),
          lineAmount: formatPrice(Number(line.unitPrice || 0) * Number(line.quantity || 0)),
          coverImage: resolveImageUrl(line.coverImage || line.cover_image || this.resolveCoverByProductId(line.productId)),
        }))
      : [];

    const goodsAmount = items.reduce((sum, it) => sum + Number(it.unitPrice || 0) * Number(it.quantity || 0), 0);
    const deliveryFee = Number(item.deliveryFee || 0);

    return {
      orderNo: item.orderNo,
      status,
      statusLabel: resolveStatusLabel(status),
      statusDesc: resolveStatusDesc(status),
      statusClass: resolveStatusClass(status),
      items,
      itemCount: items.reduce((sum, it) => sum + it.quantity, 0),
      goodsAmount: formatPrice(goodsAmount),
      deliveryFee: formatPrice(deliveryFee),
      totalAmount: formatPrice(item.totalAmount || 0),
      createdAt: item.createdAt || "",
      paidAt: item.paidAt || "",
      shippedAt: item.shippedAt || "",
      completedAt: item.completedAt || "",
      cancelledAt: item.cancelledAt || "",
      receiverName: item.receiverName || "",
      receiverPhone: item.receiverPhone || "",
      receiverAddress: item.receiverAddress || "",
      remark: item.remark || "",
      trackingNo: item.trackingNo || "",
      trackingCompany: item.trackingCompany || "",
    };
  },

  resolveCoverByProductId(productId) {
    const id = Number(productId || 0);
    if (!id || !this.productCoverCache) return "";
    return this.productCoverCache[id] || "";
  },

  async ensureProductCoverMap() {
    if (this.productCoverCache && Object.keys(this.productCoverCache).length > 0) {
      return this.productCoverCache;
    }
    try {
      const res = await get("/products");
      const list = Array.isArray(res && res.data) ? res.data : [];
      const map = {};
      list.forEach((item) => {
        const id = Number(item && item.id);
        if (!id) return;
        const src = item.coverImage || item.cover_image || "";
        if (src) map[id] = src;
      });
      this.productCoverCache = map;
      this.setData({ productCoverMap: map });
      return map;
    } catch (err) {
      this.productCoverCache = {};
      return {};
    }
  },

  onCopyOrderNo() {
    wx.setClipboardData({
      data: this.data.order.orderNo,
      success: () => wx.showToast({ title: "订单号已复制", icon: "success" }),
    });
  },

  onCopyTrackingNo() {
    const trackingNo = this.data.order.trackingNo;
    if (!trackingNo) return;
    wx.setClipboardData({
      data: trackingNo,
      success: () => wx.showToast({ title: "物流单号已复制", icon: "success" }),
    });
  },

  onCallPhone() {
    const phone = this.data.order.receiverPhone;
    if (phone) {
      wx.makePhoneCall({ phoneNumber: phone });
    }
  },

  onPay() {
    if (this.data.actionLoading) return;
    const orderNo = this.data.orderNo;
    const amount = this.data.order && this.data.order.totalAmount ? this.data.order.totalAmount : "";
    let query = `orderNo=${encodeURIComponent(orderNo)}`;
    if (amount) {
      query += `&amount=${encodeURIComponent(amount)}`;
    }
    wx.navigateTo({
      url: `/pages/pay/pay?${query}`,
    });
  },

  async onCancel() {
    if (this.data.actionLoading) return;

    const confirmRes = await new Promise((resolve) => {
      wx.showModal({
        title: "取消订单",
        content: "确定要取消该订单吗？",
        confirmText: "确定",
        cancelText: "再想想",
        confirmColor: "#cb4456",
        success: resolve,
        fail: () => resolve({ confirm: false }),
      });
    });

    if (!confirmRes.confirm) return;

    this.setData({ actionLoading: true });
    try {
      const res = await post(`/orders/${this.data.orderNo}/cancel`);
      if (res.success) {
        wx.showToast({ title: "取消成功", icon: "success" });
        setTimeout(() => this.loadOrderDetail(), 500);
      } else {
        wx.showToast({ title: res.message || "取消失败", icon: "none" });
      }
    } catch (err) {
      wx.showToast({ title: "网络错误", icon: "none" });
    } finally {
      this.setData({ actionLoading: false });
    }
  },

  async onConfirmReceive() {
    if (this.data.actionLoading) return;

    const confirmRes = await new Promise((resolve) => {
      wx.showModal({
        title: "确认收货",
        content: "确认已收到商品吗？",
        confirmText: "确认",
        cancelText: "取消",
        success: resolve,
        fail: () => resolve({ confirm: false }),
      });
    });

    if (!confirmRes.confirm) return;

    this.setData({ actionLoading: true });
    try {
      const res = await post(`/orders/${this.data.orderNo}/confirm`);
      if (res.success) {
        wx.showToast({ title: "确认成功", icon: "success" });
        setTimeout(() => this.loadOrderDetail(), 500);
      } else {
        wx.showToast({ title: res.message || "操作失败", icon: "none" });
      }
    } catch (err) {
      wx.showToast({ title: "网络错误", icon: "none" });
    } finally {
      this.setData({ actionLoading: false });
    }
  },

  async onRefund() {
    if (this.data.actionLoading) return;

    const confirmRes = await new Promise((resolve) => {
      wx.showModal({
        title: "申请退款",
        content: "确定要申请退款吗？",
        confirmText: "确定",
        cancelText: "取消",
        confirmColor: "#cb4456",
        success: resolve,
        fail: () => resolve({ confirm: false }),
      });
    });

    if (!confirmRes.confirm) return;

    this.setData({ actionLoading: true });
    try {
      const res = await post(`/orders/${this.data.orderNo}/cancel`);
      if (res.success) {
        wx.showToast({ title: "退款申请已提交", icon: "success" });
        setTimeout(() => this.loadOrderDetail(), 500);
      } else {
        wx.showToast({ title: res.message || "操作失败", icon: "none" });
      }
    } catch (err) {
      wx.showToast({ title: "网络错误", icon: "none" });
    } finally {
      this.setData({ actionLoading: false });
    }
  },

  onBuyAgain() {
    wx.switchTab({ url: "/pages/home/home" });
  },

  onTapProduct(e) {
    const productId = e.currentTarget.dataset.id;
    if (productId) {
      wx.navigateTo({ url: `/pages/detail/detail?id=${productId}` });
    }
  },

  onViewLogistics() {
    wx.showToast({ title: "物流功能开发中", icon: "none" });
  },
});
