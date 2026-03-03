const { get } = require("../../utils/request");
const { formatPrice, resolvePrice, resolveImageUrl } = require("../../utils/format");

function normalizeProduct(item) {
  return {
    id: item.id,
    title: item.title || item.name || "",
    unitPrice: formatPrice(resolvePrice(item)),
    coverImage: resolveImageUrl(item.coverImage || item.cover_image || item.image || ""),
    category: (item.category || "").toUpperCase(),
  };
}

Page({
  data: {
    keyword: "",
    autoFocus: true,
    loading: true,
    products: [],
    filteredProducts: [],
  },

  onLoad(options) {
    const keyword = options && options.q ? decodeURIComponent(options.q) : "";
    this.setData({ keyword: keyword.trim() });
    this.loadProducts();
  },

  onPullDownRefresh() {
    this.loadProducts().finally(() => wx.stopPullDownRefresh());
  },

  async loadProducts() {
    this.setData({ loading: true });
    try {
      const res = await get("/products");
      if (!res.success || !Array.isArray(res.data)) {
        wx.showToast({ title: res.message || "加载失败", icon: "none" });
        this.setData({ loading: false, products: [], filteredProducts: [] });
        return;
      }

      const products = res.data.map(normalizeProduct);
      this.setData(
        {
          loading: false,
          products,
        },
        () => this.applyFilter()
      );
    } catch (err) {
      this.setData({ loading: false, products: [], filteredProducts: [] });
      wx.showToast({ title: "网络错误", icon: "none" });
    }
  },

  onInputKeyword(e) {
    const keyword = String((e && e.detail && e.detail.value) || "").trim();
    this.setData({ keyword }, () => this.applyFilter());
  },

  onConfirmSearch() {
    this.applyFilter();
  },

  onClearKeyword() {
    this.setData({ keyword: "", autoFocus: true }, () => this.applyFilter());
  },

  applyFilter() {
    const keyword = String(this.data.keyword || "").trim().toLowerCase();
    if (!keyword) {
      this.setData({ filteredProducts: [...this.data.products] });
      return;
    }

    const filtered = this.data.products.filter((item) => {
      const title = String(item.title || "").toLowerCase();
      const category = String(item.category || "").toLowerCase();
      return title.includes(keyword) || category.includes(keyword);
    });
    this.setData({ filteredProducts: filtered });
  },

  onTapProduct(e) {
    const id = e.currentTarget.dataset.id;
    if (!id) return;
    wx.navigateTo({ url: `/pages/detail/detail?id=${id}` });
  },
});
