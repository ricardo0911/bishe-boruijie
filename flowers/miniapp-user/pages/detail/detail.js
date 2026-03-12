const { requireLogin } = require("../../utils/auth");
const { get, post } = require("../../utils/request");
const { toNumber, formatPrice, resolvePrice, pickText, resolveImageUrl, resolveMerchantName, getUserId } = require("../../utils/format");

const CATEGORY_META = {
  VALENTINE: { label: "情人花", short: "爱" },
  DAILY: { label: "日常", short: "日" },
  MOTHER_DAY: { label: "母亲花", short: "母" },
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
    favoriteLoading: false,
    product: null,
    merchantAddress: "",
    reviews: [],
    reviewPage: 1,
    reviewPageSize: 10,
    hasMoreReviews: false,
    swiperCurrent: 0,
    selectedSpec: null,
    quantity: 1,
    isFavorite: false,
    favoriteSource: "remote",
    showSpecPopup: false,
    actionType: null,
  },

  onLoad(options) {
    if (!requireLogin()) return;
    const id = Number(options.id || 0);
    if (!id) {
      wx.showToast({ title: "参数错误", icon: "none" });
      return;
    }

    this.setData({ productId: id });
    this.loadAll();
    this.checkFavoriteStatus();
  },

  onPullDownRefresh() {
    this.loadAll().finally(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    if (this.data.hasMoreReviews) {
      this.loadMoreReviews();
    }
  },

  async loadAll() {
    this.setData({ loading: true, reviewPage: 1 });
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

      let images = [];
      if (item.coverImage) {
        images.push(resolveImageUrl(item.coverImage));
      }
      if (Array.isArray(item.images)) {
        images = images.concat(item.images.map((img) => resolveImageUrl(img)));
      }

      const bomItems = Array.isArray(item.bomItems)
        ? item.bomItems.map((bom) => ({
            flowerName: bom.flowerName,
            dosage: bom.dosage,
            subtotal: formatPrice(bom.subtotal),
          }))
        : [];

      const productStock = toNumber(item.stock, 0);
      const productSales = toNumber(item.sales, 0);
      const specs = Array.isArray(item.specs) && item.specs.length
        ? item.specs
        : [{ id: "default", name: "默认规格", price: resolvePrice(item), stock: productStock }];

      const specsWithPrice = specs.map((spec) => ({
        ...spec,
        price: formatPrice(spec.price),
        stock: toNumber(spec.stock, productStock),
      }));

      const merchantName = resolveMerchantName(item);

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
          merchantName,
          coverImage: resolveImageUrl(item.coverImage || item.cover_image || ""),
          images,
          packagingFee: formatPrice(item.packagingFee),
          deliveryFee: formatPrice(item.deliveryFee),
          bomItems,
          specs: specsWithPrice,
          stock: productStock,
          sales: productSales,
        },
        selectedSpec: specsWithPrice[0],
      });

      this.loadMerchantAddress(merchantName);
    } catch (err) {
      wx.showToast({ title: "网络错误", icon: "none" });
    }
  },

  async loadMerchantAddress(merchantName) {
    const name = String(merchantName || "").trim();
    if (!name) {
      this.setData({ merchantAddress: "" });
      return;
    }

    try {
      const res = await get("/merchants/public/list");
      if (res.success && Array.isArray(res.data)) {
        const match = res.data.find((item) => String((item && item.name) || "") === name);
        this.setData({ merchantAddress: match && match.address ? String(match.address) : "" });
        return;
      }
    } catch (err) {
      // ignore
    }

    this.setData({ merchantAddress: "" });
  },

  onPickMerchant() {
    wx.navigateTo({ url: "/pages/merchant-select/merchant-select" });
  },

  async loadReviews() {
    try {
      const { productId, reviewPage, reviewPageSize } = this.data;
      const res = await get(`/reviews/product/${productId}`);
      if (!res.success || !Array.isArray(res.data)) {
        this.setData({ reviews: [], hasMoreReviews: false });
        return;
      }

      const reviews = res.data.map((item) => ({
        id: item.id,
        userName: pickText(item, ["userName", "user_name", "nickname"]) || "用户",
        avatar: item.avatar || "",
        score: Number(item.score || 0),
        content: item.content || "",
        createTime: item.createTime || item.create_time || "",
        images: Array.isArray(item.images) ? item.images.map((img) => resolveImageUrl(img)) : [],
        reply: item.reply || "",
        replyTime: item.replyTime || item.reply_time || "",
      }));

      const start = (reviewPage - 1) * reviewPageSize;
      const end = start + reviewPageSize;
      const pageReviews = reviews.slice(start, end);
      const hasMore = end < reviews.length;

      this.setData({
        reviews: reviewPage === 1 ? pageReviews : this.data.reviews.concat(pageReviews),
        hasMoreReviews: hasMore,
      });
    } catch (err) {
      this.setData({ reviews: [], hasMoreReviews: false });
    }
  },

  loadMoreReviews() {
    const { reviewPage, hasMoreReviews } = this.data;
    if (!hasMoreReviews) return;
    this.setData({ reviewPage: reviewPage + 1 }, () => {
      this.loadReviews();
    });
  },

  async checkFavoriteStatus() {
    const userId = getUserId();
    if (!userId) {
      this.setData({ isFavorite: false, favoriteSource: "remote" });
      return;
    }

    try {
      const res = await get(`/users/${userId}/favorites`);
      if (res.success && Array.isArray(res.data)) {
        const isFavorite = res.data.some(
          (item) => Number(item.productId || item.id) === Number(this.data.productId)
        );
        this.setData({ isFavorite, favoriteSource: "remote" });
        return;
      }
    } catch (err) {
      // keep default false
    }

    this.setData({ isFavorite: false, favoriteSource: "remote" });
  },

  onSwiperChange(e) {
    this.setData({ swiperCurrent: e.detail.current });
  },

  onPreviewImage(e) {
    const { index } = e.currentTarget.dataset;
    const { images } = this.data.product;
    wx.previewImage({
      current: images[index],
      urls: images,
    });
  },

  showSpecPopup(e) {
    const actionType = e.currentTarget.dataset.type || "cart";
    this.setData({ showSpecPopup: true, actionType });
  },

  closeSpecPopup() {
    this.setData({ showSpecPopup: false, actionType: null });
  },

  onSelectSpec(e) {
    const { spec } = e.currentTarget.dataset;
    this.setData({ selectedSpec: spec });
  },

  onQuantityMinus() {
    const { quantity } = this.data;
    if (quantity > 1) {
      this.setData({ quantity: quantity - 1 });
    }
  },

  onQuantityPlus() {
    const { quantity, selectedSpec, product } = this.data;
    const maxStock = selectedSpec ? selectedSpec.stock : product.stock;
    if (quantity < maxStock) {
      this.setData({ quantity: quantity + 1 });
    } else {
      wx.showToast({ title: "已达到最大库存", icon: "none" });
    }
  },

  onQuantityInput(e) {
    let value = parseInt(e.detail.value, 10);
    const { selectedSpec, product } = this.data;
    const maxStock = selectedSpec ? selectedSpec.stock : product.stock;

    if (Number.isNaN(value) || value < 1) {
      value = 1;
    } else if (value > maxStock) {
      value = maxStock;
      wx.showToast({ title: "已达到最大库存", icon: "none" });
    }

    this.setData({ quantity: value });
  },

  async onAddCart() {
    if (!requireLogin()) return;
    if (this.data.cartLoading || !this.data.productId) return;

    const { selectedSpec, quantity } = this.data;
    if (!selectedSpec) {
      wx.showToast({ title: "请选择规格", icon: "none" });
      return;
    }

    this.setData({ cartLoading: true });
    try {
      const res = await post("/cart", {
        userId: getUserId(),
        productId: this.data.productId,
        quantity,
        specId: selectedSpec.id,
      });
      if (res.success) {
        wx.showToast({ title: "已加入购物车", icon: "success" });
        this.closeSpecPopup();
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
    if (!requireLogin()) return;
    const { productId, selectedSpec, quantity } = this.data;
    if (!selectedSpec) {
      wx.showToast({ title: "请选择规格", icon: "none" });
      return;
    }

    this.closeSpecPopup();
    wx.navigateTo({
      url: `/pages/checkout/checkout?productId=${productId}&quantity=${quantity}&specId=${selectedSpec.id}`,
    });
  },

  onAddCartClick() {
    this.setData({ showSpecPopup: true, actionType: "cart" });
  },

  onBuyNowClick() {
    this.setData({ showSpecPopup: true, actionType: "buy" });
  },

  onSpecConfirm() {
    const { actionType } = this.data;
    if (actionType === "cart") {
      this.onAddCart();
    } else if (actionType === "buy") {
      this.onBuyNow();
    }
  },

  async onToggleFavorite() {
    const userId = getUserId();
    if (!userId) {
      requireLogin();
      return;
    }

    if (this.data.favoriteLoading) return;
    this.setData({ favoriteLoading: true });

    try {
      const { isFavorite, productId } = this.data;
      let res;
      if (isFavorite) {
        res = await post(`/users/${userId}/favorites/remove`, { productId });
      } else {
        res = await post(`/users/${userId}/favorites`, { productId });
      }

      if (!res.success) {
        wx.showToast({ title: res.message || "操作失败", icon: "none" });
        return;
      }

      const nextFavorite = !isFavorite;
      this.setData({ isFavorite: nextFavorite, favoriteSource: "remote" });
      wx.showToast({
        title: nextFavorite ? "已收藏" : "已取消收藏",
        icon: nextFavorite ? "success" : "none",
      });
    } catch (err) {
      wx.showToast({ title: "网络错误", icon: "none" });
    } finally {
      this.setData({ favoriteLoading: false });
    }
  },

  onGoCart() {
    wx.switchTab({ url: "/pages/cart/cart" });
  },

  onGoHome() {
    wx.switchTab({ url: "/pages/index/index" });
  },
});
