const { get } = require("../../utils/request");
const { pickText, getUserId } = require("../../utils/format");

Page({
  data: {
    loading: true,
    user: {
      name: "微信用户",
      phone: "",
      points: 0,
      preferenceTags: "",
    },
  },

  onShow() {
    this.loadProfile();
  },

  onPullDownRefresh() {
    this.loadProfile().finally(() => wx.stopPullDownRefresh());
  },

  async loadProfile() {
    this.setData({ loading: true });
    try {
      const res = await get(`/users/${getUserId()}`);
      if (!res.success || !res.data) {
        this.setData({ loading: false });
        wx.showToast({ title: res.message || "加载失败", icon: "none" });
        return;
      }
      const user = {
        name: res.data.name || "微信用户",
        phone: res.data.phone || "",
        points: Number(res.data.points || 0),
        preferenceTags: pickText(res.data, ["preferenceTags", "preference_tags"]) || "",
      };
      this.setData({ loading: false, user });
    } catch (err) {
      this.setData({ loading: false });
      wx.showToast({ title: "网络错误", icon: "none" });
    }
  },

  onEditProfile() {
    wx.navigateTo({ url: "/pages/profile-edit/profile-edit" });
  },

  onGoOrders() {
    wx.switchTab({ url: "/pages/orders/orders" });
  },

  onGoCart() {
    wx.switchTab({ url: "/pages/cart/cart" });
  },

  onGoCategory() {
    wx.switchTab({ url: "/pages/category/category" });
  },
});
