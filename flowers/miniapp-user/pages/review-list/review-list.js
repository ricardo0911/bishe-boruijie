const { get } = require("../../utils/request");
const { getUserId, resolveImageUrl } = require("../../utils/format");
const { requireLogin } = require("../../utils/auth");

function normalizeReview(item) {
  return {
    id: Number(item.id || 0),
    orderNo: item.orderNo || item.order_no || "",
    productId: Number(item.productId || item.product_id || 0),
    productTitle: item.productTitle || item.product_title || "\u672a\u547d\u540d\u5546\u54c1",
    productImage: resolveImageUrl(item.productImage || item.product_image || ""),
    score: Number(item.score || item.rating || 0),
    content: item.content || "",
    reply: item.reply || "",
    replyTime: item.replyTime || item.reply_time || "",
    images: Array.isArray(item.images) ? item.images.map((img) => resolveImageUrl(img)) : [],
    createTime: item.createTime || item.create_time || "",
  };
}

Page({
  data: {
    loading: true,
    reviews: [],
  },

  onShow() {
    if (!requireLogin("/pages/review-list/review-list")) {
      return;
    }
    this.loadReviews();
  },

  async loadReviews() {
    this.setData({ loading: true });
    try {
      const userId = getUserId();
      const res = await get(`/reviews/user/${userId}`);
      if (!res.success || !Array.isArray(res.data)) {
        wx.showToast({ title: res.message || "\u52a0\u8f7d\u5931\u8d25", icon: "none" });
        this.setData({ loading: false, reviews: [] });
        return;
      }
      this.setData({
        loading: false,
        reviews: res.data.map((item) => normalizeReview(item)),
      });
    } catch (err) {
      wx.showToast({ title: "\u7f51\u7edc\u9519\u8bef", icon: "none" });
      this.setData({ loading: false, reviews: [] });
    }
  },

  onGoProduct(e) {
    const productId = Number(e.currentTarget.dataset.productId || 0);
    if (!productId) return;
    wx.navigateTo({ url: `/pages/detail/detail?id=${productId}` });
  },

  onGoOrder(e) {
    const orderNo = e.currentTarget.dataset.orderNo || "";
    if (!orderNo) return;
    wx.navigateTo({ url: `/pages/order-detail/order-detail?orderNo=${encodeURIComponent(orderNo)}` });
  },

  onPreviewImages(e) {
    const reviewId = Number(e.currentTarget.dataset.reviewId || 0);
    const index = Number(e.currentTarget.dataset.index || 0);
    const currentReview = (this.data.reviews || []).find((item) => item.id === reviewId);
    const urls = currentReview && Array.isArray(currentReview.images) ? currentReview.images : [];
    if (!urls.length) return;
    wx.previewImage({ current: urls[index] || urls[0], urls });
  },
});