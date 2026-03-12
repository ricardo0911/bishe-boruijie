const { get, post } = require("../../utils/request");
const { resolveImageUrl, getUserId } = require("../../utils/format");
const { requireLogin } = require("../../utils/auth");

function formatCollectedAt(value) {
  if (!value) return "";
  const text = String(value).trim().replace("T", " ").replace(/\.\d+Z?$/, "");
  return text.length > 16 ? text.slice(0, 16) : text;
}

function normalizeFavorite(item) {
  const productId = Number(item.productId || item.id || 0);
  return {
    productId,
    title: String(item.title || item.name || "").trim() || "商品已下架",
    coverImage: resolveImageUrl(item.coverImage || item.cover_image || ""),
    collectedAt: formatCollectedAt(item.updatedAt || item.updated_at || item.createdAt || item.created_at),
  };
}

Page({
  data: {
    loading: true,
    favorites: [],
    removingId: 0,
  },

  onShow() {
    if (!requireLogin()) return;
    this.loadFavorites();
  },

  onPullDownRefresh() {
    if (!requireLogin()) {
      wx.stopPullDownRefresh();
      return;
    }
    this.loadFavorites().finally(() => wx.stopPullDownRefresh());
  },

  async loadFavorites() {
    const userId = getUserId();
    if (!userId) {
      this.setData({ loading: false, favorites: [], removingId: 0 });
      return;
    }

    this.setData({ loading: true });
    try {
      const res = await get(`/users/${userId}/favorites`);
      if (res.success && Array.isArray(res.data)) {
        this.setData({
          loading: false,
          favorites: res.data.map(normalizeFavorite).filter((item) => item.productId > 0),
          removingId: 0,
        });
        return;
      }

      this.setData({ loading: false, favorites: [], removingId: 0 });
      wx.showToast({ title: res.message || "收藏记录加载失败", icon: "none" });
    } catch (err) {
      this.setData({ loading: false, favorites: [], removingId: 0 });
      wx.showToast({ title: "网络错误", icon: "none" });
    }
  },

  onTapFavorite(e) {
    const productId = Number(e.currentTarget.dataset.id || 0);
    if (!productId) return;
    wx.navigateTo({ url: `/pages/detail/detail?id=${productId}` });
  },

  onGoShopping() {
    wx.switchTab({ url: "/pages/home/home" });
  },

  onRemoveFavorite(e) {
    const productId = Number(e.currentTarget.dataset.id || 0);
    if (!productId || this.data.removingId) return;

    wx.showModal({
      title: "提示",
      content: "确定取消收藏该商品吗？",
      confirmColor: "#bf4d68",
      success: (result) => {
        if (result.confirm) {
          this.removeFavorite(productId);
        }
      },
    });
  },

  async removeFavorite(productId) {
    const userId = getUserId();
    if (!userId || !productId) return;

    this.setData({ removingId: productId });
    try {
      const res = await post(`/users/${userId}/favorites/remove`, { productId });
      if (!res.success) {
        wx.showToast({ title: res.message || "取消收藏失败", icon: "none" });
        this.setData({ removingId: 0 });
        return;
      }

      const favorites = this.data.favorites.filter((item) => item.productId !== productId);
      this.setData({ favorites, removingId: 0 });
      wx.showToast({ title: "已取消收藏", icon: "none" });
    } catch (err) {
      this.setData({ removingId: 0 });
      wx.showToast({ title: "网络错误", icon: "none" });
    }
  },
});
