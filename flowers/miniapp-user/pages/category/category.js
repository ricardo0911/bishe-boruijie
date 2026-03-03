const { get } = require("../../utils/request");
const { formatPrice, resolvePrice, resolveImageUrl } = require("../../utils/format");

const PAGE_SIZE = 10;

Page({
  data: {
    categories: [],
    currentCategoryId: null,
    currentCategoryName: "全部商品",
    products: [],
    page: 0,
    size: PAGE_SIZE,
    hasMore: true,
    loading: false,
    initialLoading: true,
  },

  onLoad(options) {
    this.loadCategories().then(() => {
      if (options && options.categoryId) {
        const categoryId = parseInt(options.categoryId, 10);
        this.switchCategory(categoryId);
      } else {
        this.loadProducts();
      }
    });
  },

  onShow() {
    const pendingCategoryId = wx.getStorageSync("categoryFilterId");
    if (pendingCategoryId) {
      wx.removeStorageSync("categoryFilterId");
      this.switchCategory(pendingCategoryId);
    }
  },

  onPullDownRefresh() {
    this.refreshProducts().finally(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) {
      this.loadMoreProducts();
    }
  },

  async loadCategories() {
    try {
      const res = await get("/categories");
      if (res.success && Array.isArray(res.data)) {
        const categories = res.data.map(item => ({
          id: item.id,
          name: item.name,
          icon: item.icon || "",
          sort: item.sort || 0,
        }));
        this.setData({ categories });
      } else {
        wx.showToast({ title: res.message || "加载分类失败", icon: "none" });
      }
    } catch (err) {
      wx.showToast({ title: "网络错误", icon: "none" });
    }
  },

  async refreshProducts() {
    this.setData({ page: 0, hasMore: true });
    await this.loadProducts();
  },

  async loadProducts() {
    if (this.data.loading) return;

    this.setData({ loading: true, initialLoading: this.data.page === 0 });

    try {
      const { currentCategoryId, page, size } = this.data;
      let url = `/products?page=${page}&size=${size}`;
      if (currentCategoryId) {
        url += `&categoryId=${currentCategoryId}`;
      }

      const res = await get(url);

      if (res.success && Array.isArray(res.data)) {
        const newProducts = res.data.map(item => this.normalizeProduct(item));
        const products = page === 0 ? newProducts : [...this.data.products, ...newProducts];
        const hasMore = newProducts.length === size;

        this.setData({
          products,
          hasMore,
          loading: false,
          initialLoading: false,
        });
      } else {
        this.setData({
          loading: false,
          initialLoading: false,
          hasMore: false,
        });
        if (page === 0) {
          wx.showToast({ title: res.message || "加载失败", icon: "none" });
        }
      }
    } catch (err) {
      this.setData({
        loading: false,
        initialLoading: false,
      });
      if (this.data.page === 0) {
        wx.showToast({ title: "网络错误", icon: "none" });
      }
    }
  },

  async loadMoreProducts() {
    if (!this.data.hasMore || this.data.loading) return;

    this.setData({ page: this.data.page + 1 }, () => {
      this.loadProducts();
    });
  },

  normalizeProduct(item) {
    return {
      id: item.id,
      title: item.title || item.name || "",
      unitPrice: formatPrice(resolvePrice(item)),
      coverImage: resolveImageUrl(item.coverImage || item.cover_image || item.image || ""),
      categoryName: item.categoryName || item.category_name || "",
      sales: item.sales || 0,
    };
  },

  switchCategory(categoryId) {
    const category = this.data.categories.find(c => c.id === categoryId);
    const categoryName = category ? category.name : "全部商品";

    this.setData({
      currentCategoryId: categoryId,
      currentCategoryName: categoryName,
      products: [],
      page: 0,
      hasMore: true,
    }, () => {
      this.loadProducts();
    });
  },

  onTapCategory(e) {
    const id = e.currentTarget.dataset.id;
    if (id === this.data.currentCategoryId) return;
    this.switchCategory(id);
  },

  onTapProduct(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: `/pages/detail/detail?id=${id}` });
  },
});
