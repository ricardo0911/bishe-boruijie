const { get } = require("../../utils/request");
const { formatPrice, resolvePrice, resolveImageUrl } = require("../../utils/format");

const CATEGORY_LABEL_MAP = {
  VALENTINE: "情人节",
  DAILY: "日常鲜花",
  MOTHER_DAY: "母亲节",
  BUSINESS: "商务用花",
  BIRTHDAY: "生日祝福",
};

function normalizeCategory(rawCategory) {
  const category = String(rawCategory || "").trim().toUpperCase();
  return category || "OTHER";
}

function resolveCategoryLabel(category) {
  if (CATEGORY_LABEL_MAP[category]) {
    return CATEGORY_LABEL_MAP[category];
  }
  return category === "OTHER" ? "其他分类" : category;
}

function normalizeProduct(item) {
  const category = normalizeCategory(item.category);
  return {
    id: item.id,
    title: item.title || item.name || "",
    unitPrice: formatPrice(resolvePrice(item)),
    coverImage: resolveImageUrl(item.coverImage || item.cover_image || item.image || ""),
    category,
    categoryLabel: resolveCategoryLabel(category),
    shopName: item.merchantName || item.merchant_name || item.shopName || item.shop_name || "官方花店",
  };
}

function buildCategories(products) {
  const categories = [{ key: "ALL", label: "全部分类" }];
  const seen = new Set();
  products.forEach((item) => {
    if (!item.category || seen.has(item.category)) return;
    seen.add(item.category);
    categories.push({
      key: item.category,
      label: item.categoryLabel || resolveCategoryLabel(item.category),
    });
  });
  return categories;
}

Page({
  data: {
    keyword: "",
    autoFocus: true,
    loading: true,
    products: [],
    filteredProducts: [],
    categories: [],
    activeCategory: "ALL",
    activeCategoryLabel: "全部分类",
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
        this.setData({
          loading: false,
          products: [],
          filteredProducts: [],
          categories: [{ key: "ALL", label: "全部分类" }],
          activeCategory: "ALL",
          activeCategoryLabel: "全部分类",
        });
        return;
      }

      const products = res.data.map(normalizeProduct);
      const categories = buildCategories(products);
      const activeCategoryExists = categories.some((item) => item.key === this.data.activeCategory);
      const activeCategory = activeCategoryExists ? this.data.activeCategory : "ALL";
      const activeCategoryLabel = (categories.find((item) => item.key === activeCategory) || categories[0]).label;

      this.setData(
        {
          loading: false,
          products,
          categories,
          activeCategory,
          activeCategoryLabel,
        },
        () => this.applyFilter()
      );
    } catch (err) {
      this.setData({
        loading: false,
        products: [],
        filteredProducts: [],
        categories: [{ key: "ALL", label: "全部分类" }],
        activeCategory: "ALL",
        activeCategoryLabel: "全部分类",
      });
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

  onSelectCategory(e) {
    const selected = String((e && e.currentTarget && e.currentTarget.dataset && e.currentTarget.dataset.key) || "ALL");
    if (selected === this.data.activeCategory) return;
    const match = this.data.categories.find((item) => item.key === selected);
    this.setData(
      {
        activeCategory: selected,
        activeCategoryLabel: match ? match.label : "全部分类",
      },
      () => this.applyFilter()
    );
  },

  applyFilter() {
    const keyword = String(this.data.keyword || "").trim().toLowerCase();
    const category = this.data.activeCategory || "ALL";

    const filtered = this.data.products.filter((item) => {
      if (category !== "ALL" && item.category !== category) {
        return false;
      }
      if (!keyword) {
        return true;
      }
      const title = String(item.title || "").toLowerCase();
      const categoryCode = String(item.category || "").toLowerCase();
      const categoryLabel = String(item.categoryLabel || "").toLowerCase();
      const shopName = String(item.shopName || "").toLowerCase();
      return (
        title.includes(keyword) ||
        categoryCode.includes(keyword) ||
        categoryLabel.includes(keyword) ||
        shopName.includes(keyword)
      );
    });

    this.setData({ filteredProducts: filtered });
  },

  onTapProduct(e) {
    const id = e.currentTarget.dataset.id;
    if (!id) return;
    wx.navigateTo({ url: `/pages/detail/detail?id=${id}` });
  },
});
