const { get } = require("../../utils/request");
const { formatPrice, resolvePrice, resolveImageUrl } = require("../../utils/format");

const CATEGORY_META = {
  VALENTINE: { label: "情人节", short: "爱" },
  DAILY: { label: "日常", short: "日" },
  MOTHER_DAY: { label: "母亲节", short: "母" },
  BUSINESS: { label: "商务", short: "商" },
  BIRTHDAY: { label: "生日", short: "生" },
};

const SCENE_OPTIONS = [
  {
    key: "ROMANCE",
    label: "恋爱纪念",
    subtitle: "约会/告白",
    preferCategories: ["VALENTINE", "DAILY"],
    opener: "今天想把心动认真表达",
  },
  {
    key: "BIRTHDAY",
    label: "生日惊喜",
    subtitle: "朋友/恋人",
    preferCategories: ["BIRTHDAY", "DAILY", "VALENTINE"],
    opener: "愿你的生日被温柔包围",
  },
  {
    key: "MOTHER",
    label: "母亲节",
    subtitle: "感谢陪伴",
    preferCategories: ["MOTHER_DAY", "DAILY"],
    opener: "把感谢写进每一朵花里",
  },
  {
    key: "BUSINESS",
    label: "商务拜访",
    subtitle: "得体不失温度",
    preferCategories: ["BUSINESS", "DAILY"],
    opener: "礼貌得体，也有温度",
  },
];

const BUDGET_OPTIONS = [
  { key: "UNDER_99", label: "99以内", min: 0, max: 99 },
  { key: "100_159", label: "100-159", min: 100, max: 159 },
  { key: "160_PLUS", label: "160+", min: 160, max: 99999 },
];

const FLOWER_LANGUAGE = {
  VALENTINE: "玫瑰主调，适合表达爱意",
  DAILY: "日常清新，轻松不冒犯",
  MOTHER_DAY: "康乃馨系，温暖与感谢",
  BUSINESS: "风格克制，适合正式场景",
  BIRTHDAY: "色彩明快，生日仪式感更足",
};

Page({
  data: {
    banners: [
      {
        title: "春日花语 浪漫绽放",
        subTitle: "全场鲜花 新人专享8折",
        bg: "linear-gradient(135deg, #d85b75 0%, #be3f58 100%)",
      },
      {
        title: "母亲节温暖献礼",
        subTitle: "花束定制 + 2小时同城配送",
        bg: "linear-gradient(135deg, #e79b68 0%, #ca7d47 100%)",
      },
      {
        title: "每日鲜花 新鲜到家",
        subTitle: "节日订花 不错过每份心意",
        bg: "linear-gradient(135deg, #7aa98f 0%, #5f8d74 100%)",
      },
    ],
    categories: [
      { key: "VALENTINE", label: "情人节", short: "爱" },
      { key: "DAILY", label: "日常鲜花", short: "日" },
      { key: "MOTHER_DAY", label: "母亲节", short: "母" },
      { key: "BUSINESS", label: "商务用花", short: "商" },
      { key: "BIRTHDAY", label: "生日祝福", short: "生" },
    ],
    sceneOptions: SCENE_OPTIONS,
    budgetOptions: BUDGET_OPTIONS,
    selectedSceneKey: "ROMANCE",
    selectedBudgetKey: "100_159",
    inspirationProducts: [],
    inspirationSlogan: "",
    giftMessage: "",
    recommendProducts: [],
    hotProducts: [],
    loading: true,
  },

  onLoad() {
    this.loadProducts();
  },

  onPullDownRefresh() {
    this.loadProducts().finally(() => wx.stopPullDownRefresh());
  },

  async loadProducts() {
    this.setData({ loading: true });
    try {
      const [productsRes, recommendRes] = await Promise.all([
        get("/products"),
        get("/products/recommend/recent?days=30&limit=4").catch(() => null),
      ]);

      if (!productsRes.success || !Array.isArray(productsRes.data)) {
        wx.showToast({ title: productsRes.message || "加载失败", icon: "none" });
        this.setData({ loading: false });
        return;
      }

      const normalized = productsRes.data.map((item) => this.normalizeProduct(item));
      let recommendProducts = normalized.slice(0, 4);

      if (recommendRes && recommendRes.success && Array.isArray(recommendRes.data) && recommendRes.data.length) {
        recommendProducts = recommendRes.data.map((item) => this.normalizeProduct(item));
      }

      const inspirationProducts = this.buildInspirationProducts(normalized);
      const inspirationSlogan = this.buildInspirationSlogan();
      const giftMessage = this.buildGiftMessage(inspirationProducts[0]);

      this.setData({
        recommendProducts,
        hotProducts: normalized,
        inspirationProducts,
        inspirationSlogan,
        giftMessage,
        loading: false,
      });
    } catch (err) {
      this.setData({ loading: false });
      wx.showToast({ title: "网络错误", icon: "none" });
    }
  },

  normalizeProduct(item) {
    const category = item.category || "DAILY";
    const meta = CATEGORY_META[category] || { label: "精选", short: "花" };
    const priceNumber = resolvePrice(item);
    return {
      id: item.id,
      title: item.title,
      category,
      categoryLabel: meta.label,
      categoryShort: meta.short,
      unitPrice: formatPrice(priceNumber),
      priceNumber,
      recentSales: Number(item.recentSales || 0),
      coverImage: resolveImageUrl(item.coverImage || item.cover_image || ""),
    };
  },

  buildInspirationProducts(products) {
    if (!Array.isArray(products) || products.length === 0) {
      return [];
    }

    const scene = SCENE_OPTIONS.find((item) => item.key === this.data.selectedSceneKey) || SCENE_OPTIONS[0];
    const budget = BUDGET_OPTIONS.find((item) => item.key === this.data.selectedBudgetKey) || BUDGET_OPTIONS[1];

    const sorted = products
      .map((product) => {
        const inScene = scene.preferCategories.includes(product.category);
        const inBudget = product.priceNumber >= budget.min && product.priceNumber <= budget.max;
        const budgetDistance = this.getBudgetDistance(product.priceNumber, budget);

        let score = 0;
        score += inScene ? 55 : 18;
        score += inBudget ? 24 : Math.max(4, 20 - budgetDistance / 8);
        score += Math.min(18, product.recentSales * 2.2);
        if (product.coverImage) {
          score += 2;
        }

        return {
          ...product,
          matchScore: Math.round(score),
          reason: this.buildInspirationReason({ product, inScene, inBudget }),
        };
      })
      .sort((a, b) => {
        if (b.matchScore !== a.matchScore) {
          return b.matchScore - a.matchScore;
        }
        if (b.recentSales !== a.recentSales) {
          return b.recentSales - a.recentSales;
        }
        return a.priceNumber - b.priceNumber;
      });

    return sorted.slice(0, 3);
  },

  getBudgetDistance(price, budget) {
    if (price < budget.min) {
      return budget.min - price;
    }
    if (price > budget.max) {
      return price - budget.max;
    }
    return 0;
  },

  buildInspirationReason({ product, inScene, inBudget }) {
    const reasonParts = [];

    if (inScene) {
      reasonParts.push("场景契合");
    }
    if (product.recentSales >= 3) {
      reasonParts.push("近期热销");
    }
    if (inBudget) {
      reasonParts.push("预算友好");
    }
    if (reasonParts.length === 0) {
      reasonParts.push("风格百搭");
    }

    const flowerHint = FLOWER_LANGUAGE[product.category] || "综合搭配，送礼稳妥";
    return `${reasonParts.join(" · ")} | ${flowerHint}`;
  },

  buildInspirationSlogan() {
    const scene = SCENE_OPTIONS.find((item) => item.key === this.data.selectedSceneKey) || SCENE_OPTIONS[0];
    const budget = BUDGET_OPTIONS.find((item) => item.key === this.data.selectedBudgetKey) || BUDGET_OPTIONS[1];
    return `${scene.opener}，预算建议：${budget.label}`;
  },

  buildGiftMessage(topProduct) {
    if (!topProduct) {
      return "愿你今天收到的每一束花，都在替我说想说的话。";
    }
    const scene = SCENE_OPTIONS.find((item) => item.key === this.data.selectedSceneKey) || SCENE_OPTIONS[0];
    return `${scene.opener}。这束「${topProduct.title}」想送给你，愿今天的你被偏爱，也被好好珍惜。`;
  },

  onPickScene(e) {
    const { key } = e.currentTarget.dataset;
    if (!key || key === this.data.selectedSceneKey) {
      return;
    }
    this.setData({ selectedSceneKey: key }, () => {
      this.refreshInspiration();
    });
  },

  onPickBudget(e) {
    const { key } = e.currentTarget.dataset;
    if (!key || key === this.data.selectedBudgetKey) {
      return;
    }
    this.setData({ selectedBudgetKey: key }, () => {
      this.refreshInspiration();
    });
  },

  onRefreshInspiration() {
    this.refreshInspiration(true);
  },

  refreshInspiration(reshuffle = false) {
    const products = this.data.hotProducts || [];
    let inspirationProducts = this.buildInspirationProducts(products);
    if (reshuffle && inspirationProducts.length > 1) {
      inspirationProducts = [...inspirationProducts].sort(() => Math.random() - 0.5);
    }
    this.setData({
      inspirationProducts,
      inspirationSlogan: this.buildInspirationSlogan(),
      giftMessage: this.buildGiftMessage(inspirationProducts[0]),
    });
  },

  onCopyGiftMessage() {
    if (!this.data.giftMessage) {
      return;
    }
    wx.setClipboardData({
      data: this.data.giftMessage,
      success: () => {
        wx.showToast({ title: "文案已复制", icon: "none" });
      },
    });
  },

  goSearchPage() {
    wx.navigateTo({ url: "/pages/search/search?from=home" });
  },

  goCategoryPage() {
    wx.switchTab({ url: "/pages/category/category" });
  },

  onTapCategory(e) {
    const { key } = e.currentTarget.dataset;
    wx.setStorageSync("categoryFilter", key);
    wx.switchTab({ url: "/pages/category/category" });
  },

  onTapProduct(e) {
    const { id } = e.currentTarget.dataset;
    wx.navigateTo({ url: `/pages/detail/detail?id=${id}` });
  },
});
