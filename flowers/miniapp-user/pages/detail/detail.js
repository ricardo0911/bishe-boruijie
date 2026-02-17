const { get, post } = require("../../utils/request");
const { formatPrice, resolvePrice, pickText, resolveImageUrl, getUserId } = require("../../utils/format");

const CATEGORY_META = {
  VALENTINE: { label: "情人节", short: "爱" },
  DAILY: { label: "日常", short: "日" },
  MOTHER_DAY: { label: "母亲节", short: "母" },
  BUSINESS: { label: "商务", short: "商" },
  BIRTHDAY: { label: "生日", short: "生" },
};

function getTypeLabel(type) {
  if (type === "BOUQUET") return "花束";
  if (type === "SINGLE") return "单枝";
  return "定制";
}

Page({
  data: {
    productId: null,
    loading: true,
    cartLoading: false,
    product: null,
    reviews: [],
  },

  onLoad(options) {
    const id = Number(options.id || 0);
    if (!id) {
      wx.showToast({ title: "参数错误", icon: "none" });
      return;
    }
    this.setData({ productId: id });
    this.loadAll();
  },

  onPullDownRefresh() {
    this.loadAll().finally(() => wx.stopPullDownRefresh());
  },

  async loadAll() {
    this.setData({ loading: true });
    await Promise.all([this.loadDetail(), this.loadReviews()]);
    this.setData({ loading: false });
  },

  async loadDetail() {
    try {
      const res = await get(`/products/${this.data.productId}`);
      if (!res.success || !res.data) {
        wx.showToast({ title: res.message || "加载失败", icon: "none" });
        return;
      }
      const item = res.data;
      const category = item.category || "DAILY";
      const meta = CATEGORY_META[category] || { label: "精选", short: "花" };

      const bomItems = Array.isArray(item.bomItems)
        ? item.bomItems.map((bom) => ({
            flowerName: bom.flowerName,
            dosage: bom.dosage,
            subtotal: formatPrice(bom.subtotal),
          }))
        : [];

      this.setData({
        product: {
          id: item.id,
          title: item.title,
          description: item.description || "",
          category,
          categoryLabel: meta.label,
          categoryShort: meta.short,
          typeLabel: getTypeLabel(item.type),
          unitPrice: formatPrice(resolvePrice(item)),
          coverImage: resolveImageUrl(item.coverImage || item.cover_image || ""),
          packagingFee: formatPrice(item.packagingFee),
          deliveryFee: formatPrice(item.deliveryFee),
          bomItems,
        },
      });
    } catch (err) {
      wx.showToast({ title: "网络错误", icon: "none" });
    }
  },

  async loadReviews() {
    try {
      const res = await get(`/reviews/product/${this.data.productId}`);
      if (!res.success || !Array.isArray(res.data)) {
        this.setData({ reviews: [] });
        return;
      }
      const reviews = res.data.map((item) => ({
        id: item.id,
        userName: pickText(item, ["userName", "user_name"]) || "用户",
        score: Number(item.score || 0),
        content: item.content || "",
      }));
      this.setData({ reviews });
    } catch (err) {
      this.setData({ reviews: [] });
    }
  },

  async onAddCart() {
    if (this.data.cartLoading || !this.data.productId) return;
    this.setData({ cartLoading: true });
    try {
      const res = await post("/cart", {
        userId: getUserId(),
        productId: this.data.productId,
        quantity: 1,
      });
      if (res.success) {
        wx.showToast({ title: "已加入购物车", icon: "none" });
      } else {
        wx.showToast({ title: res.message || "操作失败", icon: "none" });
      }
    } catch (err) {
      wx.showToast({ title: "网络错误", icon: "none" });
    } finally {
      this.setData({ cartLoading: false });
    }
  },

  onBuyNow() {
    if (!this.data.productId) return;
    wx.navigateTo({
      url: `/pages/checkout/checkout?productId=${this.data.productId}&quantity=1`,
    });
  },

  onGoCart() {
    wx.switchTab({ url: "/pages/cart/cart" });
  },
});
