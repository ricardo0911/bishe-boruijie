const { get, post, del } = require("../../utils/request");
const { formatPrice, resolvePrice, pickText, resolveImageUrl, getUserId } = require("../../utils/format");

Page({
  data: {
    loading: true,
    items: [],
    totalAmount: "0.00",
  },

  onShow() {
    this.loadCart();
  },

  onPullDownRefresh() {
    this.loadCart().finally(() => wx.stopPullDownRefresh());
  },

  async loadCart() {
    this.setData({ loading: true });
    try {
      const res = await get(`/cart/${getUserId()}`);
      if (!res.success || !Array.isArray(res.data)) {
        this.setData({ loading: false, items: [], totalAmount: "0.00" });
        wx.showToast({ title: res.message || "加载失败", icon: "none" });
        return;
      }
      const items = res.data.map((item) => this.normalizeItem(item));
      this.setData({ loading: false, items }, () => this.calcTotal());
    } catch (err) {
      this.setData({ loading: false, items: [], totalAmount: "0.00" });
      wx.showToast({ title: "网络错误", icon: "none" });
    }
  },

  normalizeItem(item) {
    return {
      id: item.id,
      productId: Number(item.productId || item.product_id),
      productTitle: pickText(item, ["productTitle", "product_title"]),
      quantity: Number(item.quantity || 0),
      unitPrice: formatPrice(resolvePrice(item)),
      coverImage: resolveImageUrl(item.coverImage || item.cover_image || ""),
    };
  },

  calcTotal() {
    const total = this.data.items.reduce((sum, item) => {
      return sum + Number(item.unitPrice) * Number(item.quantity || 0);
    }, 0);
    this.setData({ totalAmount: formatPrice(total) });
  },

  async onTapDecrease(e) {
    const productId = Number(e.currentTarget.dataset.id);
    const current = Number(e.currentTarget.dataset.qty);
    await this.changeQty(productId, current - 1);
  },

  async onTapIncrease(e) {
    const productId = Number(e.currentTarget.dataset.id);
    const current = Number(e.currentTarget.dataset.qty);
    await this.changeQty(productId, current + 1);
  },

  async changeQty(productId, quantity) {
    if (quantity <= 0) {
      try {
        await del(`/cart/${getUserId()}/${productId}`);
        const items = this.data.items.filter((item) => item.productId !== productId);
        this.setData({ items }, () => this.calcTotal());
      } catch (err) {
        wx.showToast({ title: "操作失败", icon: "none" });
      }
      return;
    }

    try {
      const res = await post("/cart", {
        userId: getUserId(),
        productId,
        quantity,
      });
      if (!res.success) {
        wx.showToast({ title: res.message || "操作失败", icon: "none" });
        return;
      }
      const items = this.data.items.map((item) => {
        if (item.productId === productId) {
          return {
            id: item.id,
            productId: item.productId,
            productTitle: item.productTitle,
            quantity,
            unitPrice: item.unitPrice,
            coverImage: item.coverImage,
          };
        }
        return item;
      });
      this.setData({ items }, () => this.calcTotal());
    } catch (err) {
      wx.showToast({ title: "网络错误", icon: "none" });
    }
  },

  onGoShopping() {
    wx.switchTab({ url: "/pages/home/home" });
  },

  onCheckout() {
    if (!this.data.items.length) return;
    const items = this.data.items.map((item) => `${item.productId}:${item.quantity}`).join(",");
    wx.navigateTo({
      url: `/pages/checkout/checkout?items=${encodeURIComponent(items)}`,
    });
  },
});
