const { get } = require("../../utils/request");
const { formatPrice, resolvePrice, resolveImageUrl, resolveMerchantName } = require("../../utils/format");
const { requireLogin } = require("../../utils/auth");

const SELECTED_MERCHANT_KEY = "selected_merchant";

const PAGE_SIZE = 10;

Page({
  data: {
    categories: [],
    currentCategoryId: null,
    currentCategoryName: "全部商品",
    selectedMerchantName: "全部花店",
    selectedMerchantAddress: "",
    products: [],
    page: 0,
    size: PAGE_SIZE,
    hasMore: true,
    loading: false,
    initialLoading: true,
  },

  onLoad(options) {
    if (!requireLogin("/pages/category/category")) return;
    this.refreshSelectedMerchant();
    this.loadCategories().then(() => {
      if (options && options.categoryId) {
        const categoryId = parseInt(options.categoryId, 10);
        this.switchCategory(Number.isNaN(categoryId) ? options.categoryId : categoryId);
      } else {
        this.loadProducts();
      }
    });
  },

  onShow() {
    if (!requireLogin("/pages/category/category")) return;
    const merchantChanged = this.refreshSelectedMerchant();
    const pendingCategoryId = wx.getStorageSync("categoryFilterId");
    if (pendingCategoryId) {
      wx.removeStorageSync("categoryFilterId");
      const categoryId = parseInt(pendingCategoryId, 10);
      this.switchCategory(Number.isNaN(categoryId) ? pendingCategoryId : categoryId);
      return;
    }

    if (merchantChanged && !this.data.loading) {
      this.refreshProducts();
    }
  },

  onPullDownRefresh() {
    if (!requireLogin("/pages/category/category")) {
      wx.stopPullDownRefresh();
      return;
    }
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
        const categories = res.data.map((item) => ({
          id: item.id,
          name: item.name,
          code: item.code || "",
          icon: item.icon || "",
          sort: item.sort || item.sortOrder || 0,
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

  refreshSelectedMerchant() {
    const selected = wx.getStorageSync(SELECTED_MERCHANT_KEY);
    const name = selected && selected.name ? String(selected.name) : "全部花店";
    const address = selected && selected.address ? String(selected.address) : "";
    const changed = name !== this.data.selectedMerchantName || address !== this.data.selectedMerchantAddress;
    this.setData({ selectedMerchantName: name, selectedMerchantAddress: address });
    return changed;
  },

  onPickMerchant() {
    wx.navigateTo({ url: "/pages/merchant-select/merchant-select" });
  },

  async loadProducts() {
    if (this.data.loading) return;

    this.setData({ loading: true, initialLoading: this.data.page === 0 });

    try {
      const { currentCategoryId, page, size, categories } = this.data;
      let url = `/products?page=${page}&size=${size}`;
      if (currentCategoryId) {
        const currentCategory = categories.find((item) => String(item.id) === String(currentCategoryId));
        const categoryValue = currentCategory?.code || currentCategory?.name || "";
        if (categoryValue) {
          url += `&category=${encodeURIComponent(categoryValue)}`;
        }
      }

      const res = await get(url);

      if (res.success && Array.isArray(res.data)) {
        const newProducts = res.data.map((item) => this.normalizeProduct(item));
        const selectedMerchant = wx.getStorageSync(SELECTED_MERCHANT_KEY);
        const selectedName = selectedMerchant && selectedMerchant.name ? String(selectedMerchant.name) : "";
        const pageProducts = selectedName
          ? newProducts.filter((item) => item.merchantName === selectedName)
          : newProducts;

        const products = page === 0 ? pageProducts : [...this.data.products, ...pageProducts];
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
      merchantName: resolveMerchantName(item),
      coverImage: resolveImageUrl(item.coverImage || item.cover_image || item.image || ""),
      categoryName: item.categoryName || item.category_name || item.category || "",
      sales: item.sales || 0,
    };
  },

  switchCategory(categoryId) {
    const normalizedCategoryId = Number.isNaN(parseInt(categoryId, 10)) ? categoryId : parseInt(categoryId, 10);
    const category = this.data.categories.find((item) => String(item.id) === String(normalizedCategoryId));
    const categoryName = category ? category.name : "全部商品";

    this.setData({
      currentCategoryId: normalizedCategoryId,
      currentCategoryName: categoryName,
      products: [],
      page: 0,
      hasMore: true,
    }, () => {
      this.loadProducts();
    });
  },

  onTapCategory(e) {
    const rawId = e.currentTarget.dataset.id;
    const id = Number.isNaN(parseInt(rawId, 10)) ? rawId : parseInt(rawId, 10);
    if (String(id) === String(this.data.currentCategoryId)) return;
    this.switchCategory(id);
  },

  onTapProduct(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: `/pages/detail/detail?id=${id}` });
  },
});
