const { get } = require("../../utils/request");
const { formatPrice, resolvePrice, resolveImageUrl } = require("../../utils/format");

const CATEGORY_META = {
  VALENTINE: { label: "情人节", short: "爱" },
  DAILY: { label: "日常", short: "日" },
  MOTHER_DAY: { label: "母亲节", short: "母" },
  BUSINESS: { label: "商务", short: "商" },
  BIRTHDAY: { label: "生日", short: "生" },
};

Page({
  data: {
    categories: [
      { key: "", label: "全部" },
      { key: "VALENTINE", label: "情人节" },
      { key: "DAILY", label: "日常鲜花" },
      { key: "MOTHER_DAY", label: "母亲节" },
      { key: "BUSINESS", label: "商务用花" },
      { key: "BIRTHDAY", label: "生日祝福" },
    ],
    currentCategory: "",
    keyword: "",
    allProducts: [],
    products: [],
    loading: true,
  },

  onLoad(options) {
    if (options && options.category) {
      this.setData({ currentCategory: options.category });
    }
    this.loadProducts();
  },

  onShow() {
    const pendingCategory = wx.getStorageSync("categoryFilter");
    if (pendingCategory) {
      wx.removeStorageSync("categoryFilter");
      this.setData({ currentCategory: pendingCategory }, () => {
        this.applyFilter();
      });
    }
  },

  onPullDownRefresh() {
    this.loadProducts().finally(() => wx.stopPullDownRefresh());
  },

  async loadProducts() {
    this.setData({ loading: true });
    try {
      const res = await get("/products");
      if (!res.success || !Array.isArray(res.data)) {
        this.setData({ loading: false, allProducts: [], products: [] });
        wx.showToast({ title: res.message || "加载失败", icon: "none" });
        return;
      }
      const allProducts = res.data.map((item) => this.normalizeProduct(item));
      this.setData({ allProducts, loading: false }, () => {
        this.applyFilter();
      });
    } catch (err) {
      this.setData({ loading: false, allProducts: [], products: [] });
      wx.showToast({ title: "网络错误", icon: "none" });
    }
  },

  normalizeProduct(item) {
    const category = item.category || "DAILY";
    const meta = CATEGORY_META[category] || { label: "精选", short: "花" };
    return {
      id: item.id,
      title: item.title,
      category,
      categoryLabel: meta.label,
      categoryShort: meta.short,
      unitPrice: formatPrice(resolvePrice(item)),
      coverImage: resolveImageUrl(item.coverImage || item.cover_image || ""),
    };
  },

  applyFilter() {
    const currentCategory = this.data.currentCategory;
    const keyword = (this.data.keyword || "").trim().toLowerCase();
    let filtered = this.data.allProducts;

    if (currentCategory) {
      filtered = filtered.filter((item) => item.category === currentCategory);
    }
    if (keyword) {
      filtered = filtered.filter((item) =>
        String(item.title || "").toLowerCase().includes(keyword)
      );
    }

    this.setData({ products: filtered });
  },

  onInputKeyword(e) {
    this.setData({ keyword: e.detail.value || "" }, () => {
      this.applyFilter();
    });
  },

  onTapTab(e) {
    const key = e.currentTarget.dataset.key || "";
    this.setData({ currentCategory: key }, () => {
      this.applyFilter();
    });
  },

  onTapProduct(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: `/pages/detail/detail?id=${id}` });
  },
});
