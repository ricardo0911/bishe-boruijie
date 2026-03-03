const { get, post } = require("../../utils/request");
const { formatPrice, getUserId, resolveImageUrl } = require("../../utils/format");

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

function resolveStatusLabel(status) {
  return STATUS_LABEL_MAP[status] || status || "未知";
}

function resolveStatusClass(status) {
  if (status === ORDER_STATUS.PAID || status === ORDER_STATUS.COMPLETED) return "status-success";
  if (status === ORDER_STATUS.CANCELLED || status === ORDER_STATUS.REFUNDED) return "status-danger";
  if (status === ORDER_STATUS.PENDING_PAY || status === ORDER_STATUS.LOCKED) return "status-warning";
  if (status === ORDER_STATUS.SHIPPED) return "status-info";
  return "status-default";
}

function normalizeOrderStatus(status) {
  return status === ORDER_STATUS.LOCKED ? ORDER_STATUS.PENDING_PAY : (status || "");
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
    currentStatus: "",
    tabs: [
      { key: "", label: "全部" },
      { key: "PENDING_PAY", label: "待支付" },
      { key: "PAID", label: "已支付" },
      { key: "SHIPPED", label: "待收货" },
      { key: "COMPLETED", label: "已完成" },
    ],
    allOrders: [],
    orders: [],
    actionOrderNo: "",
    countdownMap: {},
    productCoverMap: {},
  },

  countdownTimer: null,
  productCoverCache: null,

  onShow() {
    const preset = wx.getStorageSync("orders_status_filter");
    wx.removeStorageSync("orders_status_filter");
    if (typeof preset === "string") {
      this.setData({ currentStatus: preset });
    }
    this.loadOrders();
  },

  onHide() {
    this.clearCountdownTimer();
  },

  onUnload() {
    this.clearCountdownTimer();
  },

  onPullDownRefresh() {
    this.loadOrders().finally(() => wx.stopPullDownRefresh());
  },

  clearCountdownTimer() {
    if (this.countdownTimer) {
      clearInterval(this.countdownTimer);
      this.countdownTimer = null;
    }
  },

  startCountdownTimer() {
    this.clearCountdownTimer();
    const refresh = () => {
      const next = {};
      (this.data.orders || []).forEach((item) => {
        if (item.status === ORDER_STATUS.PENDING_PAY) {
          const cd = calculateCountdown(item.createdAt);
          if (cd) next[item.orderNo] = cd;
        }
      });
      this.setData({ countdownMap: next });
    };
    refresh();
    this.countdownTimer = setInterval(refresh, 1000);
  },

  async loadOrders() {
    this.setData({ loading: true });
    try {
      await this.ensureProductCoverMap();
      const userId = getUserId();
      const res = await get(`/orders/user/${userId}/details?limit=50`);
      if (!res.success || !Array.isArray(res.data)) {
        wx.showToast({ title: res.message || "加载失败", icon: "none" });
        this.setData({ loading: false, allOrders: [], orders: [] });
        this.clearCountdownTimer();
        return;
      }

      const allOrders = res.data.map((order) => this.normalizeOrder(order));
      this.setData({ loading: false, allOrders }, () => {
        this.applyFilter();
      });
    } catch (err) {
      wx.showToast({ title: "网络错误", icon: "none" });
      this.setData({ loading: false, allOrders: [], orders: [] });
      this.clearCountdownTimer();
    }
  },

  normalizeOrder(order) {
    const status = normalizeOrderStatus(order.status);
    const items = Array.isArray(order.items)
      ? order.items.map((line) => ({
          productId: line.productId,
          productTitle: resolveProductTitle(line),
          quantity: Number(line.quantity || 0),
          unitPrice: formatPrice(line.unitPrice || 0),
          coverImage: resolveImageUrl(line.coverImage || line.cover_image || this.resolveCoverByProductId(line.productId)),
        }))
      : [];

    const itemCount = items.reduce((sum, item) => sum + Number(item.quantity || 0), 0);

    return {
      orderNo: order.orderNo,
      status,
      statusLabel: resolveStatusLabel(status),
      statusClass: resolveStatusClass(status),
      totalAmount: formatPrice(order.totalAmount || 0),
      createdAt: order.createdAt || "",
      items: items.slice(0, 2),
      itemCount,
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

  applyFilter() {
    const key = this.data.currentStatus || "";
    const orders = key
      ? this.data.allOrders.filter((item) => item.status === key)
      : this.data.allOrders.slice();

    this.setData({ orders }, () => {
      if (orders.some((item) => item.status === ORDER_STATUS.PENDING_PAY)) {
        this.startCountdownTimer();
      } else {
        this.clearCountdownTimer();
        this.setData({ countdownMap: {} });
      }
    });
  },

  onTapTab(e) {
    const key = e.currentTarget.dataset.key || "";
    this.setData({ currentStatus: key }, () => this.applyFilter());
  },

  onTapOrder(e) {
    const orderNo = e.currentTarget.dataset.no;
    if (!orderNo) return;
    wx.navigateTo({
      url: `/pages/order-detail/order-detail?orderNo=${orderNo}`,
    });
  },

  onPay(e) {
    const orderNo = e.currentTarget.dataset.no;
    if (!orderNo || this.data.actionOrderNo) return;

    e.stopPropagation && e.stopPropagation();

    const amount = e.currentTarget.dataset.amount || "";
    let query = `orderNo=${encodeURIComponent(orderNo)}`;
    if (amount) {
      query += `&amount=${encodeURIComponent(amount)}`;
    }

    wx.navigateTo({
      url: `/pages/pay/pay?${query}`,
    });
  },

  async onCancel(e) {
    const orderNo = e.currentTarget.dataset.no;
    if (!orderNo || this.data.actionOrderNo) return;

    e.stopPropagation && e.stopPropagation();

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

    this.setData({ actionOrderNo: orderNo });
    try {
      const res = await post(`/orders/${orderNo}/cancel`);
      if (res.success) {
        wx.showToast({ title: "操作成功", icon: "success" });
        setTimeout(() => this.loadOrders(), 500);
      } else {
        wx.showToast({ title: res.message || "操作失败", icon: "none" });
      }
    } catch (err) {
      wx.showToast({ title: "网络错误", icon: "none" });
    } finally {
      this.setData({ actionOrderNo: "" });
    }
  },

  async onConfirmReceive(e) {
    const orderNo = e.currentTarget.dataset.no;
    if (!orderNo || this.data.actionOrderNo) return;

    e.stopPropagation && e.stopPropagation();

    this.setData({ actionOrderNo: orderNo });
    try {
      const res = await post(`/orders/${orderNo}/confirm`);
      if (res.success) {
        wx.showToast({ title: "确认成功", icon: "success" });
        setTimeout(() => this.loadOrders(), 500);
      } else {
        wx.showToast({ title: res.message || "操作失败", icon: "none" });
      }
    } catch (err) {
      wx.showToast({ title: "网络错误", icon: "none" });
    } finally {
      this.setData({ actionOrderNo: "" });
    }
  },

  onBuyAgain() {
    wx.switchTab({ url: "/pages/home/home" });
  },
});
