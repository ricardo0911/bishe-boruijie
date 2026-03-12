const { get } = require("../../utils/request");
const { getUserId } = require("../../utils/format");
const { requireLogin } = require("../../utils/auth");
const { getMemberProfile, getMemberLevels } = require("../../utils/member");

Page({
  data: {
    loading: true,
    member: getMemberProfile(0),
    levelCards: getMemberLevels(0),
  },

  onShow() {
    if (!requireLogin("/pages/member-benefits/member-benefits")) {
      this.setData({ loading: false });
      return;
    }
    this.loadMemberInfo();
  },

  onPullDownRefresh() {
    if (!requireLogin("/pages/member-benefits/member-benefits")) {
      wx.stopPullDownRefresh();
      return;
    }
    this.loadMemberInfo().finally(() => wx.stopPullDownRefresh());
  },

  async loadMemberInfo() {
    this.setData({ loading: true });
    const userId = getUserId();
    if (!userId) {
      this.applyMemberPoints(0);
      this.setData({ loading: false });
      return;
    }

    try {
      const res = await get(`/users/${userId}`);
      const points = res.success && res.data ? Number(res.data.points || 0) : 0;
      this.applyMemberPoints(points);
    } catch (err) {
      this.applyMemberPoints(0);
      wx.showToast({ title: "会员信息加载失败", icon: "none" });
    } finally {
      this.setData({ loading: false });
    }
  },

  applyMemberPoints(points) {
    this.setData({
      member: getMemberProfile(points),
      levelCards: getMemberLevels(points),
    });
  },

  onGoShopping() {
    wx.switchTab({ url: "/pages/home/home" });
  },
});