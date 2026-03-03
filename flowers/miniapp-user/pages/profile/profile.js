const { get } = require("../../utils/request");
const { getUserId } = require("../../utils/format");

const ORDER_STATUS = {
  CREATED: "CREATED",
  LOCKED: "LOCKED",
  PENDING_PAY: "PENDING_PAY",
  PAID: "PAID",
  CONFIRMED: "CONFIRMED",
  SHIPPED: "SHIPPED",
  COMPLETED: "COMPLETED",
  CANCELLED: "CANCELLED",
  REFUNDED: "REFUNDED",
};

function wait(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function normalizeUser(data) {
  return {
    name: data.name || "微信用户",
    phone: data.phone || "",
    points: Number(data.points || 0),
  };
}

function buildInitial(name) {
  const text = String(name || "").trim();
  if (!text) return "花";
  return text.slice(0, 1).toUpperCase();
}

function buildOrderStats(orders) {
  const stats = {
    orderTotal: 0,
    pendingPay: 0,
    shipped: 0,
    completed: 0,
  };
  if (!Array.isArray(orders)) {
    return stats;
  }

  stats.orderTotal = orders.length;
  orders.forEach((item) => {
    const status = item && item.status ? String(item.status) : "";
    if (
      status === ORDER_STATUS.CREATED ||
      status === ORDER_STATUS.LOCKED ||
      status === ORDER_STATUS.PENDING_PAY
    ) {
      stats.pendingPay += 1;
    }
    if (status === ORDER_STATUS.SHIPPED) {
      stats.shipped += 1;
    }
    if (
      status === ORDER_STATUS.COMPLETED ||
      status === ORDER_STATUS.CANCELLED ||
      status === ORDER_STATUS.REFUNDED
    ) {
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
  const score = Number(points || 0);
  if (score >= 2000) {
    return {
      levelName: "花语大师",
      progress: 100,
      toNextText: "已达到最高等级，继续保持你的花礼审美",
    };
  }
  if (score >= 1000) {
    const gap = 2000 - score;
    const progress = Math.round(((score - 1000) / 1000) * 100);
    return {
      levelName: "花匠会员",
      progress,
      toNextText: `再获得 ${gap} 积分可升级为 花语大师`,
    };
  }
  if (score >= 300) {
    const gap = 1000 - score;
    const progress = Math.round(((score - 300) / 700) * 100);
    return {
      levelName: "花芽会员",
      progress,
      toNextText: `再获得 ${gap} 积分可升级为 花匠会员`,
    };
  }
  const gap = 300 - score;
  const progress = Math.round((score / 300) * 100);
  return {
    levelName: "新芽会员",
    progress,
    toNextText: `再获得 ${gap} 积分可升级为 花芽会员`,
  };
}

function buildProfileCompletion(user, addressCount) {
  let done = 0;
  const total = 3;
  if (user.name) done += 1;
  if (user.phone) done += 1;
  if (addressCount > 0) done += 1;
  return Math.round((done / total) * 100);
}

function buildSyncText(date = new Date()) {
  const hour = `${date.getHours()}`.padStart(2, "0");
  const minute = `${date.getMinutes()}`.padStart(2, "0");
  return `${hour}:${minute}`;
}

Page({
  data: {
    loading: true,
    syncText: "",
    userInitial: "花",
    profileCompletion: 25,
    completionActionText: "去完善",
    user: {
      name: "微信用户",
      phone: "",
      points: 0,
    },
    member: {
      levelName: "新芽会员",
      progress: 0,
      toNextText: "再获得 300 积分可升级为 花芽会员",
    },
    stats: {
      orderTotal: 0,
      pendingPay: 0,
      shipped: 0,
      completed: 0,
      cartItems: 0,
      favoriteCount: 0,
      addressCount: 0,
    },
  },

  onShow() {
    this.loadProfileDashboard();
  },

  onPullDownRefresh() {
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
      wx.showToast({ title: "登录信息未准备好", icon: "none" });
      return;
    }

    this.setData({ loading: true });

    const [userResult, orderResult, cartResult, favoriteResult, addressResult] = await Promise.allSettled([
      get(`/users/${userId}`),
      get(`/orders/user/${userId}/details?limit=50`),
      get(`/cart/${userId}`),
      get(`/users/${userId}/favorites`),
      get(`/users/${userId}/addresses`),
    ]);

    let user = this.data.user;
    if (
      userResult.status === "fulfilled" &&
      userResult.value &&
      userResult.value.success &&
      userResult.value.data
    ) {
      user = normalizeUser(userResult.value.data);
    }

    let orders = [];
    if (
      orderResult.status === "fulfilled" &&
      orderResult.value &&
      orderResult.value.success &&
      Array.isArray(orderResult.value.data)
    ) {
      orders = orderResult.value.data;
    }

    let cartItems = [];
    if (
      cartResult.status === "fulfilled" &&
      cartResult.value &&
      cartResult.value.success &&
      Array.isArray(cartResult.value.data)
    ) {
      cartItems = cartResult.value.data;
    }

    let favoriteCount = 0;
    if (
      favoriteResult.status === "fulfilled" &&
      favoriteResult.value &&
      favoriteResult.value.success &&
      Array.isArray(favoriteResult.value.data)
    ) {
      favoriteCount = favoriteResult.value.data.length;
    }

    let addressCount = 0;
    if (
      addressResult.status === "fulfilled" &&
      addressResult.value &&
      addressResult.value.success &&
      Array.isArray(addressResult.value.data)
    ) {
      addressCount = addressResult.value.data.length;
    }

    const orderStats = buildOrderStats(orders);
    const stats = {
      ...orderStats,
      cartItems: buildCartCount(cartItems),
      favoriteCount,
      addressCount,
    };
    const member = buildMember(user.points);
    const profileCompletion = buildProfileCompletion(user, addressCount);
    const completionActionText = profileCompletion >= 100 ? "已完成" : "去完善";

    this.setData({
      loading: false,
      user,
      stats,
      member,
      profileCompletion,
      completionActionText,
      userInitial: buildInitial(user.name),
      syncText: buildSyncText(),
    });
  },

  onTapAction(e) {
    const action = e.currentTarget.dataset.action || "";
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
    if (action === "category") {
      this.onGoCategory();
      return;
    }
    if (action === "address") {
      this.onGoAddressList();
      return;
    }
    if (action === "edit") {
      this.onEditProfile();
      return;
    }
    if (action === "favorite") {
      wx.showToast({ title: "可在商品详情中收藏", icon: "none" });
      return;
    }
    if (action === "service") {
      wx.showToast({ title: "客服服务即将上线", icon: "none" });
    }
  },

  onGoOrdersWithStatus(status) {
    wx.setStorageSync("orders_status_filter", status || "");
    wx.switchTab({ url: "/pages/orders/orders" });
  },

  onEditProfile() {
    wx.navigateTo({ url: "/pages/profile-edit/profile-edit" });
  },

  onGoAddressList() {
    wx.navigateTo({ url: "/pages/address-list/address-list" });
  },

  onGoCart() {
    wx.switchTab({ url: "/pages/cart/cart" });
  },

  onGoCategory() {
    wx.switchTab({ url: "/pages/category/category" });
  },

  onCopyUserName() {
    const text = this.data.user && this.data.user.name ? this.data.user.name : "花礼用户";
    wx.setClipboardData({
      data: text,
      success: () => wx.showToast({ title: "昵称已复制", icon: "none" }),
    });
  },
});
