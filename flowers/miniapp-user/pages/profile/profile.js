const { get } = require("../../utils/request");
const { getUserId } = require("../../utils/format");
const { clearUserSession, requireLogin } = require("../../utils/auth");
const { getMemberProfile } = require("../../utils/member");

const I18N = {
  loading: "\u6b63\u5728\u540c\u6b65\u4f60\u7684\u82b1\u793c\u6863\u6848...",
  copyName: "\u590d\u5236\u6635\u79f0",
  accountPrefix: "\u8d26\u53f7\uff1a",
  noAccount: "\u672a\u8bbe\u7f6e\u767b\u5f55\u8d26\u53f7",
  syncedPrefix: "\u6700\u8fd1\u540c\u6b65\uff1a",
  ordersAll: "\u5168\u90e8\u8ba2\u5355",
  ordersPending: "\u5f85\u652f\u4ed8",
  ordersShipped: "\u5f85\u6536\u8d27",
  cartItems: "\u8d2d\u7269\u8f66\u4ef6\u6570",
  growthTitle: "\u4f1a\u5458\u6210\u957f",
  pointsPrefix: "\u79ef\u5206",
  favoriteTitle: "\u6536\u85cf\u8bb0\u5f55",
  favoriteDesc: "\u5355\u72ec\u67e5\u770b\u5df2\u6536\u85cf\u7684\u9c9c\u82b1\u5546\u54c1",
  favoriteUnit: "\u9879",
  reviewTitle: "\u6211\u7684\u8bc4\u4ef7",
  reviewDesc: "\u67e5\u770b\u5df2\u53d1\u5e03\u7684\u5546\u54c1\u8bc4\u4ef7\u4e0e\u8bc4\u5206",
  feedbackTitle: "\u6211\u7684\u53cd\u9988",
  feedbackDesc: "查看已提交的客服工单与处理进度",
  refundTitle: "退款/售后",
  refundDesc: "查看可退款订单与退款进度",
  addressTitle: "\u5730\u5740\u7ba1\u7406",
  addressDesc: "\u7ef4\u62a4\u6536\u8d27\u5730\u5740\u4e0e\u9ed8\u8ba4\u5730\u5740",
  passwordTitle: "\u4fee\u6539\u5bc6\u7801",
  passwordDesc: "\u4fee\u6539\u5f53\u524d\u767b\u5f55\u5bc6\u7801",
  serviceTitle: "\u5ba2\u670d\u4e0e\u5e2e\u52a9",
  serviceDesc: "\u5e38\u89c1\u95ee\u9898\u4e0e\u552e\u540e\u652f\u6301",
  logoutTitle: "\u9000\u51fa\u767b\u5f55",
  logoutDesc: "\u9000\u51fa\u540e\u9700\u8981\u91cd\u65b0\u8f93\u5165\u8d26\u53f7\u5bc6\u7801\u767b\u5f55",
  loginNotReady: "\u767b\u5f55\u4fe1\u606f\u672a\u51c6\u5907\u597d",
  serviceDeveloping: "\u5ba2\u670d\u529f\u80fd\u5f00\u53d1\u4e2d",
  logoutModalTitle: "\u9000\u51fa\u767b\u5f55",
  logoutModalContent: "\u9000\u51fa\u540e\u9700\u8981\u91cd\u65b0\u8f93\u5165\u8d26\u53f7\u5bc6\u7801\u767b\u5f55\u3002",
  defaultUserName: "\u5fae\u4fe1\u7528\u6237",
  defaultUserInitial: "\u82b1",
  copyFallbackName: "\u82b1\u793c\u7528\u6237",
  copySuccess: "\u6635\u79f0\u5df2\u590d\u5236",
  memberMaster: "\u82b1\u8bed\u5927\u5e08",
  memberCraft: "\u82b1\u5320\u4f1a\u5458",
  memberSprout: "\u65b0\u82bd\u4f1a\u5458"
};

const ORDER_STATUS = {
  CREATED: "CREATED",
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
  REFUNDED: "REFUNDED"
};

function wait(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function normalizeUser(data) {
  return {
    account: data.account || "",
    name: data.name || I18N.defaultUserName,
    phone: data.phone || "",
    points: Number(data.points || 0)
  };
}

function buildInitial(name) {
  const text = String(name || "").trim();
  if (!text) return I18N.defaultUserInitial;
  return text.slice(0, 1).toUpperCase();
}

function buildOrderStats(orders) {
  const stats = {
    orderTotal: 0,
    pendingPay: 0,
    shipped: 0,
    completed: 0,
    refundCount: 0
  };
  if (!Array.isArray(orders)) {
    return stats;
  }

  stats.orderTotal = orders.length;
  orders.forEach((item) => {
    const status = item && item.status ? String(item.status) : "";
    if (status === ORDER_STATUS.CREATED || status === ORDER_STATUS.LOCKED || status === ORDER_STATUS.PENDING_PAY) {
      stats.pendingPay += 1;
    }
    if (status === ORDER_STATUS.SHIPPED) {
      stats.shipped += 1;
    }
    if (
      status === ORDER_STATUS.PAID ||
      status === ORDER_STATUS.CONFIRMED ||
      status === ORDER_STATUS.COMPLETED ||
      status === ORDER_STATUS.REFUND_REQUESTED ||
      status === ORDER_STATUS.REFUNDING ||
      status === ORDER_STATUS.REFUND_FAILED ||
      status === ORDER_STATUS.REFUNDED
    ) {
      stats.refundCount += 1;
    }
    if (status === ORDER_STATUS.COMPLETED || status === ORDER_STATUS.CANCELLED || status === ORDER_STATUS.REFUNDED) {
      stats.completed += 1;
    }
  });
  return stats;
}

function buildCartCount(cartItems) {
  if (!Array.isArray(cartItems)) return 0;
  return cartItems.reduce((sum, item) => sum + Number(item.quantity || 0), 0);
}

function buildMember(points) {
  return getMemberProfile(points);
}

function buildSyncText(date = new Date()) {
  const hour = `${date.getHours()}`.padStart(2, "0");
  const minute = `${date.getMinutes()}`.padStart(2, "0");
  return `${hour}:${minute}`;
}

Page({
  data: {
    i18n: I18N,
    loading: true,
    syncText: "",
    userInitial: I18N.defaultUserInitial,
    user: {
      account: "",
      name: I18N.defaultUserName,
      phone: "",
      points: 0
    },
    member: getMemberProfile(0),
    stats: {
      orderTotal: 0,
      pendingPay: 0,
      shipped: 0,
      completed: 0,
      refundCount: 0,
      cartItems: 0,
      favoriteCount: 0,
      addressCount: 0
    }
  },

  onShow() {
    if (!requireLogin("/pages/profile/profile")) {
      this.setData({ loading: false });
      return;
    }
    this.loadProfileDashboard();
  },

  onPullDownRefresh() {
    if (!requireLogin("/pages/profile/profile")) {
      wx.stopPullDownRefresh();
      return;
    }
    this.loadProfileDashboard().finally(() => wx.stopPullDownRefresh());
  },

  async resolveUserId() {
    let uid = getUserId();
    if (uid) return uid;
    for (let i = 0; i < 10; i += 1) {
      await wait(120);
      uid = getUserId();
      if (uid) return uid;
    }
    return 0;
  },

  async loadProfileDashboard() {
    const userId = await this.resolveUserId();
    if (!userId) {
      this.setData({ loading: false });
      wx.showToast({ title: I18N.loginNotReady, icon: "none" });
      return;
    }

    this.setData({ loading: true });

    const [userResult, orderResult, cartResult, favoriteResult, addressResult] = await Promise.allSettled([
      get(`/users/${userId}`),
      get(`/orders/user/${userId}/details?limit=50`),
      get(`/cart/${userId}`),
      get(`/users/${userId}/favorites`),
      get(`/users/${userId}/addresses`)
    ]);

    let user = this.data.user;
    if (userResult.status === "fulfilled" && userResult.value && userResult.value.success && userResult.value.data) {
      user = normalizeUser(userResult.value.data);
    }

    let orders = [];
    if (orderResult.status === "fulfilled" && orderResult.value && orderResult.value.success && Array.isArray(orderResult.value.data)) {
      orders = orderResult.value.data;
    }

    let cartItems = [];
    if (cartResult.status === "fulfilled" && cartResult.value && cartResult.value.success && Array.isArray(cartResult.value.data)) {
      cartItems = cartResult.value.data;
    }

    let favoriteCount = 0;
    if (favoriteResult.status === "fulfilled" && favoriteResult.value && favoriteResult.value.success && Array.isArray(favoriteResult.value.data)) {
      favoriteCount = favoriteResult.value.data.length;
    }

    let addressCount = 0;
    if (addressResult.status === "fulfilled" && addressResult.value && addressResult.value.success && Array.isArray(addressResult.value.data)) {
      addressCount = addressResult.value.data.length;
    }

    const orderStats = buildOrderStats(orders);
    const stats = {
      ...orderStats,
      cartItems: buildCartCount(cartItems),
      favoriteCount,
      addressCount
    };
    const member = buildMember(user.points);

    this.setData({
      loading: false,
      user,
      stats,
      member,
      userInitial: buildInitial(user.name),
      syncText: buildSyncText()
    });
  },

  onTapAction(e) {
    const action = e.currentTarget.dataset.action || "";
    if (action === "member") {
      this.onOpenMemberPage();
      return;
    }
    if (action === "orders_all") {
      this.onGoOrdersWithStatus("");
      return;
    }
    if (action === "orders_pending") {
      this.onGoOrdersWithStatus("PENDING_PAY");
      return;
    }
    if (action === "orders_shipped") {
      this.onGoOrdersWithStatus("SHIPPED");
      return;
    }
    if (action === "orders_completed") {
      this.onGoOrdersWithStatus("COMPLETED");
      return;
    }
    if (action === "cart") {
      this.onGoCart();
      return;
    }
    if (action === "address") {
      this.onGoAddressList();
      return;
    }
    if (action === "favorite") {
      wx.navigateTo({ url: "/pages/favorites/favorites" });
      return;
    }
    if (action === "reviews") {
      wx.navigateTo({ url: "/pages/review-list/review-list" });
      return;
    }
    if (action === "feedback") {
      wx.navigateTo({ url: "/pages/support-ticket-list/support-ticket-list" });
      return;
    }
    if (action === "refund") {
      this.onGoOrdersWithStatus("AFTER_SALE");
      return;
    }
    if (action === "change_password") {
      this.onChangePassword();
      return;
    }
    if (action === "service") {
      wx.navigateTo({ url: "/pages/service/service" });
      return;
    }
    if (action === "logout") {
      this.onLogout();
    }
  },

  onOpenMemberPage() {
    wx.navigateTo({ url: "/pages/member-benefits/member-benefits" });
  },

  onGoOrdersWithStatus(status) {
    wx.setStorageSync("orders_status_filter", status || "");
    wx.switchTab({ url: "/pages/orders/orders" });
  },

  onGoAddressList() {
    wx.navigateTo({ url: "/pages/address-list/address-list" });
  },

  onGoCart() {
    wx.switchTab({ url: "/pages/cart/cart" });
  },

  onChangePassword() {
    wx.navigateTo({ url: "/pages/change-password/change-password" });
  },

  onLogout() {
    wx.showModal({
      title: I18N.logoutModalTitle,
      content: I18N.logoutModalContent,
      confirmColor: "#d14d72",
      success: (res) => {
        if (!res.confirm) {
          return;
        }
        clearUserSession();
        wx.reLaunch({ url: "/pages/login/login" });
      }
    });
  },

  onCopyUserName() {
    const text = this.data.user && this.data.user.name ? this.data.user.name : I18N.copyFallbackName;
    wx.setClipboardData({
      data: text,
      success: () => wx.showToast({ title: I18N.copySuccess, icon: "none" })
    });
  }
});
