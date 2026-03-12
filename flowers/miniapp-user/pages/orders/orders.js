const { get, post } = require("../../utils/request");
const { formatPrice, getUserId, resolveImageUrl } = require("../../utils/format");
const { requireLogin } = require("../../utils/auth");

const ORDER_STATUS = {
  LOCKED: "LOCKED",
  PENDING_PAY: "PENDING_PAY",
  PAID: "PAID",
  CONFIRMED: "CONFIRMED",
  REFUND_REQUESTED: "REFUND_REQUESTED",
  REFUNDING: "REFUNDING",
  REFUND_FAILED: "REFUND_FAILED",
  SHIPPED: "SHIPPED",
  COMPLETED: "COMPLETED",
  CANCELLED: "CANCELLED",
  REFUNDED: "REFUNDED",
};

const STATUS_LABEL_MAP = {
  LOCKED: "待支付",
  PENDING_PAY: "待支付",
  PAID: "待发货",
  CONFIRMED: "待收货",
  REFUND_REQUESTED: "退款审核中",
  REFUNDING: "退款处理中",
  REFUND_FAILED: "退款失败",
  SHIPPED: "待收货",
  COMPLETED: "已完成",
  CANCELLED: "已取消",
  REFUNDED: "已退款",
};

const TAB_FILTER_MAP = {
  PENDING_PAY: [ORDER_STATUS.PENDING_PAY],
  TO_SHIP: [ORDER_STATUS.PAID],
  SHIPPED: [ORDER_STATUS.CONFIRMED, ORDER_STATUS.SHIPPED],
  AFTER_SALE: [ORDER_STATUS.PAID, ORDER_STATUS.CONFIRMED, ORDER_STATUS.COMPLETED, ORDER_STATUS.REFUND_REQUESTED, ORDER_STATUS.REFUNDING, ORDER_STATUS.REFUND_FAILED, ORDER_STATUS.REFUNDED],
  COMPLETED: [ORDER_STATUS.COMPLETED],
  CANCELLED: [ORDER_STATUS.CANCELLED],
  REFUNDED: [ORDER_STATUS.REFUNDED],
};

function resolveStatusLabel(status) {
  return STATUS_LABEL_MAP[status] || status || "未知";
}

function resolveStatusClass(status) {
  if (status === ORDER_STATUS.PAID || status === ORDER_STATUS.COMPLETED) return "status-success";
  if (status === ORDER_STATUS.CANCELLED || status === ORDER_STATUS.REFUNDED) return "status-danger";
  if (status === ORDER_STATUS.PENDING_PAY || status === ORDER_STATUS.LOCKED) return "status-warning";
  if (status === ORDER_STATUS.REFUND_REQUESTED || status === ORDER_STATUS.REFUNDING || status === ORDER_STATUS.REFUND_FAILED) return "status-warning";
  if (status === ORDER_STATUS.CONFIRMED || status === ORDER_STATUS.SHIPPED) return "status-info";
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
      { key: "TO_SHIP", label: "待发货" },
      { key: "SHIPPED", label: "待收货" },
      { key: "PENDING_PAY", label: "待支付" },
      { key: "COMPLETED", label: "已完成" },
      { key: "CANCELLED", label: "已取消" },
      { key: "REFUNDED", label: "已退款" },
    ],
    allOrders: [],
    orders: [],
    actionOrderNo: "",
    refundPopupVisible: false,
    refundOrderNo: "",
    refundReason: "",
    countdownMap: {},
    productCoverMap: {},
  },

  countdownTimer: null,
  productCoverCache: null,

  onShow() {
    if (!requireLogin("/pages/orders/orders")) return;
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
    if (!requireLogin("/pages/orders/orders")) {
      wx.stopPullDownRefresh();
      return;
    }
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
      const [orderRes, reviewRes] = await Promise.all([
        get(`/orders/user/${userId}/details?limit=50`),
        get(`/reviews/user/${userId}`),
      ]);
      if (!orderRes.success || !Array.isArray(orderRes.data)) {
        wx.showToast({ title: orderRes.message || "\u52a0\u8f7d\u5931\u8d25", icon: "none" });
        this.setData({ loading: false, allOrders: [], orders: [] });
        this.clearCountdownTimer();
        return;
      }

      const reviewMap = this.buildReviewedProductMap(
        reviewRes.success && Array.isArray(reviewRes.data) ? reviewRes.data : []
      );
      const allOrders = orderRes.data.map((order) => this.normalizeOrder(order, reviewMap));
      this.setData({ loading: false, allOrders }, () => {
        this.applyFilter();
      });
    } catch (err) {
      wx.showToast({ title: "\u7f51\u7edc\u9519\u8bef", icon: "none" });
      this.setData({ loading: false, allOrders: [], orders: [] });
      this.clearCountdownTimer();
    }
  },

  normalizeOrder(order, reviewMap = {}) {
    const status = normalizeOrderStatus(order.status);
    const items = Array.isArray(order.items)
      ? order.items.map((line) => ({
          productId: Number(line.productId || line.product_id || 0),
          productTitle: resolveProductTitle(line),
          quantity: Number(line.quantity || 0),
          unitPrice: formatPrice(line.unitPrice || line.unit_price || 0),
          coverImage: resolveImageUrl(line.coverImage || line.cover_image || this.resolveCoverByProductId(line.productId || line.product_id)),
        }))
      : [];

    const itemCount = items.reduce((sum, item) => sum + Number(item.quantity || 0), 0);
    const reviewedProductMap = reviewMap[order.orderNo] || {};
    const reviewableItems = items.filter((item) => item.productId > 0);
    const reviewedCount = reviewableItems.filter((item) => reviewedProductMap[item.productId]).length;
    const fullyReviewed = reviewableItems.length > 0 && reviewedCount >= reviewableItems.length;

    return {
      orderNo: order.orderNo,
      status,
      statusLabel: resolveStatusLabel(status),
      statusClass: resolveStatusClass(status),
      totalAmount: formatPrice(order.totalAmount || 0),
      createdAt: order.createdAt || "",
      items: items.slice(0, 2),
      itemCount,
      reviewableCount: reviewableItems.length,
      reviewedCount,
      reviewActionText: fullyReviewed ? "\u5df2\u8bc4\u4ef7" : "\u53bb\u8bc4\u4ef7",
      reviewButtonClass: fullyReviewed ? "action-btn-done" : "",
      reviewSummary: reviewableItems.length ? `${reviewedCount}/${reviewableItems.length} \u4ef6\u5546\u54c1\u5df2\u8bc4\u4ef7` : "\u6682\u65e0\u53ef\u8bc4\u4ef7\u5546\u54c1",
    };
  },

  buildReviewedProductMap(reviewList) {
    return (Array.isArray(reviewList) ? reviewList : []).reduce((result, item) => {
      const orderNo = item.orderNo || item.order_no || "";
      const productId = Number(item.productId || item.product_id || 0);
      if (!orderNo || !productId) return result;
      if (!result[orderNo]) {
        result[orderNo] = {};
      }
      result[orderNo][productId] = true;
      return result;
    }, {});
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
    const filterStatuses = TAB_FILTER_MAP[key] || null;
    const orders = filterStatuses
      ? this.data.allOrders.filter((item) => filterStatuses.includes(item.status))
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

  onViewLogistics(e) {
    const orderNo = e.currentTarget.dataset.no;
    if (!orderNo) return;

    e.stopPropagation && e.stopPropagation();

    wx.navigateTo({
      url: `/pages/logistics/logistics?orderNo=${encodeURIComponent(orderNo)}`,
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

  async onRefund(e) {
    const orderNo = e.currentTarget.dataset.no;
    if (!orderNo || this.data.actionOrderNo) return;

    e.stopPropagation && e.stopPropagation();

    this.setData({
      refundPopupVisible: true,
      refundOrderNo: orderNo,
      refundReason: "",
    });
  },

  onInputRefundReason(e) {
    this.setData({ refundReason: e.detail.value || "" });
  },

  onCloseRefundPopup() {
    if (this.data.actionOrderNo) return;
    this.setData({
      refundPopupVisible: false,
      refundOrderNo: "",
      refundReason: "",
    });
  },

  async onSubmitRefund() {
    const orderNo = this.data.refundOrderNo;
    if (!orderNo || this.data.actionOrderNo) return;

    const reason = (this.data.refundReason || "").trim();
    if (!reason) {
      wx.showToast({ title: "请填写退款理由", icon: "none" });
      return;
    }

    const order = (this.data.allOrders || []).find((item) => item.orderNo === orderNo);

    this.setData({ actionOrderNo: orderNo });
    try {
      const res = await post('/after-sales', {
        orderNo,
        refundAmount: order?.totalAmount || '0.00',
        reason
      });
      if (res.success) {
        this.setData({
          refundPopupVisible: false,
          refundOrderNo: "",
          refundReason: "",
        });
        wx.showToast({ title: "退款申请已提交", icon: "success" });
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

  noop() {},

  async onConfirmReceive(e) {
    const orderNo = e.currentTarget.dataset.no;
    if (!orderNo || this.data.actionOrderNo) return;

    e.stopPropagation && e.stopPropagation();

    this.setData({ actionOrderNo: orderNo });
    try {
      const res = await post(`/orders/${orderNo}/complete`);
      if (res.success) {
        wx.showToast({ title: "收货成功", icon: "success" });
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

  onGoReview(e) {
    const orderNo = e.currentTarget.dataset.no;
    if (!orderNo) return;
    e.stopPropagation && e.stopPropagation();
    wx.navigateTo({
      url: `/pages/review-create/review-create?orderNo=${encodeURIComponent(orderNo)}`,
    });
  },

  onBuyAgain() {
    wx.switchTab({ url: "/pages/home/home" });
  },
});
