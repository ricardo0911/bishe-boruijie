const { get, post, del } = require("../../utils/request");
const { formatPrice, resolvePrice, pickText, resolveImageUrl, resolveMerchantName, getUserId } = require("../../utils/format");
const { requireLogin } = require("../../utils/auth");

const CART_CACHE_KEY = "cart_cache";
const RECOMMEND_CACHE_KEY = "cart_recommend_cache";

Page({
  data: {
    loading: true,
    items: [],
    totalAmount: "0.00",
    selectedTotal: "0.00",
    selectedCount: 0,
    isAllSelected: false,
    recommendProducts: [],
    editing: false,
  },

  onShow() {
    if (!requireLogin("/pages/cart/cart")) return;
    this.loadCart();
    this.loadRecommendProducts();
  },

  onPullDownRefresh() {
    if (!requireLogin("/pages/cart/cart")) {
      wx.stopPullDownRefresh();
      return;
    }
    Promise.all([
      this.loadCart(),
      this.loadRecommendProducts(),
    ]).finally(() => wx.stopPullDownRefresh());
  },

  // 加载购物车数据
  async loadCart() {
    this.setData({ loading: true });
    const userId = getUserId();
    if (!userId) {
      this.setData({ loading: false, items: [] });
      return;
    }

    try {
      // 先读取本地缓存
      const cached = wx.getStorageSync(CART_CACHE_KEY);
      if (cached && cached.items && cached.items.length > 0) {
        this.setData({ items: cached.items });
        this.calcTotal();
      }

      const res = await get(`/cart/${userId}`);
      if (!res.success || !Array.isArray(res.data)) {
        this.setData({ loading: false });
        if (res.message) {
          wx.showToast({ title: res.message, icon: "none" });
        }
        return;
      }

      const items = res.data.map((item) => this.normalizeItem(item));
      // 合并选中状态（从缓存中恢复）
      const selectedMap = {};
      this.data.items.forEach((item) => {
        selectedMap[item.id] = item.selected;
      });
      items.forEach((item) => {
        if (selectedMap[item.id] !== undefined) {
          item.selected = selectedMap[item.id];
        }
      });

      this.setData({ loading: false, items }, () => {
        this.calcTotal();
        this.saveCartCache();
      });
    } catch (err) {
      // 网络失败时使用缓存
      const cached = wx.getStorageSync(CART_CACHE_KEY);
      if (cached && cached.items) {
        this.setData({ loading: false, items: cached.items });
        this.calcTotal();
      } else {
        this.setData({ loading: false, items: [] });
      }
      wx.showToast({ title: "网络错误，显示缓存数据", icon: "none" });
    }
  },

  // 加载推荐商品
  async loadRecommendProducts() {
    // 先读取缓存
    const cached = wx.getStorageSync(RECOMMEND_CACHE_KEY);
    if (cached && cached.products) {
      this.setData({ recommendProducts: cached.products });
    }

    try {
      const res = await get("/products/recommend/recent?days=30&limit=6");
      if (res.success && Array.isArray(res.data)) {
        const products = res.data.map((item) => this.normalizeProduct(item));
        this.setData({ recommendProducts: products });
        wx.setStorageSync(RECOMMEND_CACHE_KEY, { products, timestamp: Date.now() });
      }
    } catch (err) {
      // 使用缓存数据，静默失败
    }
  },

  // 标准化购物车项
  normalizeItem(item) {
    return {
      id: item.id,
      productId: Number(item.productId || item.product_id),
      productTitle: pickText(item, ["productTitle", "product_title", "title"]),
      spec: item.spec || item.specification || "",
      merchantName: resolveMerchantName(item),
      quantity: Number(item.quantity || 0),
      unitPrice: formatPrice(resolvePrice(item)),
      unitPriceNum: resolvePrice(item),
      coverImage: resolveImageUrl(item.coverImage || item.cover_image || item.image || ""),
      selected: item.selected !== false, // 默认选中
    };
  },

  // 标准化商品
  normalizeProduct(item) {
    return {
      id: item.id,
      title: item.title,
      merchantName: resolveMerchantName(item),
      unitPrice: formatPrice(resolvePrice(item)),
      unitPriceNum: resolvePrice(item),
      coverImage: resolveImageUrl(item.coverImage || item.cover_image || item.image || ""),
    };
  },

  // 计算合计金额
  calcTotal() {
    let total = 0;
    let selectedTotal = 0;
    let selectedCount = 0;
    let selectedAll = true;

    this.data.items.forEach((item) => {
      const itemTotal = item.unitPriceNum * item.quantity;
      total += itemTotal;
      if (item.selected) {
        selectedTotal += itemTotal;
        selectedCount += item.quantity;
      } else {
        selectedAll = false;
      }
    });

    const isAllSelected = this.data.items.length > 0 && selectedAll;

    this.setData({
      totalAmount: formatPrice(total),
      selectedTotal: formatPrice(selectedTotal),
      selectedCount,
      isAllSelected,
    });
  },

  // 保存购物车缓存
  saveCartCache() {
    const cacheData = {
      items: this.data.items,
      timestamp: Date.now(),
    };
    wx.setStorageSync(CART_CACHE_KEY, cacheData);
  },

  // 切换商品选中状态
  onToggleSelect(e) {
    const itemId = e.currentTarget.dataset.id;
    const items = this.data.items.map((item) => {
      if (item.id === itemId) {
        return { ...item, selected: !item.selected };
      }
      return item;
    });
    this.setData({ items }, () => {
      this.calcTotal();
      this.saveCartCache();
      this.syncSelectStatus(itemId, items.find((i) => i.id === itemId)?.selected);
    });
  },

  // 同步选中状态到服务器
  async syncSelectStatus() {
    // 后端暂无“选中状态”接口，选中态仅在本地维护。
    return;
  },

  // 全选/取消全选
  onToggleSelectAll() {
    const newSelected = !this.data.isAllSelected;
    const items = this.data.items.map((item) => ({
      ...item,
      selected: newSelected,
    }));
    this.setData({ items, isAllSelected: newSelected }, () => {
      this.calcTotal();
      this.saveCartCache();
    });
  },

  // 减少数量
  async onTapDecrease(e) {
    const itemId = e.currentTarget.dataset.itemId;
    const current = Number(e.currentTarget.dataset.qty);
    if (current <= 1) {
      // 数量为1时询问是否删除
      wx.showModal({
        title: "提示",
        content: "确定删除该商品吗？",
        success: (res) => {
          if (res.confirm) {
            this.deleteItem(itemId);
          }
        },
      });
      return;
    }
    await this.changeQty(itemId, current - 1);
  },

  // 增加数量
  async onTapIncrease(e) {
    const itemId = e.currentTarget.dataset.itemId;
    const current = Number(e.currentTarget.dataset.qty);
    await this.changeQty(itemId, current + 1);
  },

  // 修改数量
  async changeQty(itemId, quantity) {
    if (quantity < 1) return;
    const userId = getUserId();
    const target = this.data.items.find((item) => item.id === itemId);
    if (!target || !target.productId) {
      wx.showToast({ title: "商品信息异常", icon: "none" });
      this.loadCart();
      return;
    }

    // 乐观更新
    const items = this.data.items.map((item) => {
      if (item.id === itemId) {
        return { ...item, quantity };
      }
      return item;
    });
    this.setData({ items }, () => this.calcTotal());

    try {
      const res = await post("/cart", {
        userId,
        productId: target.productId,
        quantity,
      });
      if (!res.success) {
        wx.showToast({ title: res.message || "修改失败", icon: "none" });
        // 回滚
        this.loadCart();
      } else {
        this.saveCartCache();
      }
    } catch (err) {
      wx.showToast({ title: "网络错误", icon: "none" });
      this.loadCart();
    }
  },

  // 删除单个商品
  async onDeleteItem(e) {
    const itemId = e.currentTarget.dataset.itemId;
    wx.showModal({
      title: "提示",
      content: "确定删除该商品吗？",
      success: (res) => {
        if (res.confirm) {
          this.deleteItem(itemId);
        }
      },
    });
  },

  // 执行删除
  async deleteItem(itemId) {
    const userId = getUserId();
    const target = this.data.items.find((item) => item.id === itemId);
    if (!target || !target.productId) {
      wx.showToast({ title: "商品信息异常", icon: "none" });
      this.loadCart();
      return;
    }

    // 乐观更新
    const items = this.data.items.filter((item) => item.id !== itemId);
    this.setData({ items }, () => this.calcTotal());

    try {
      const res = await del(`/cart/${userId}/${target.productId}`);
      if (!res.success) {
        wx.showToast({ title: res.message || "删除失败", icon: "none" });
        this.loadCart();
      } else {
        this.saveCartCache();
        wx.showToast({ title: "已删除", icon: "success" });
      }
    } catch (err) {
      wx.showToast({ title: "删除失败", icon: "none" });
      this.loadCart();
    }
  },

  // 批量删除选中商品
  onBatchDelete() {
    const selectedItems = this.data.items.filter((item) => item.selected);
    if (selectedItems.length === 0) {
      wx.showToast({ title: "请选择要删除的商品", icon: "none" });
      return;
    }
    wx.showModal({
      title: "提示",
      content: `确定删除选中的 ${selectedItems.length} 件商品吗？`,
      success: (res) => {
        if (res.confirm) {
          this.batchDelete(selectedItems);
        }
      },
    });
  },

  // 执行批量删除
  async batchDelete(selectedItems) {
    const userId = getUserId();
    // 乐观更新
    const selectedIds = selectedItems.map((item) => item.id);
    const items = this.data.items.filter((item) => !selectedIds.includes(item.id));
    this.setData({ items }, () => this.calcTotal());

    try {
      // 逐个删除
      const promises = selectedItems
        .filter((item) => item.productId)
        .map((item) => del(`/cart/${userId}/${item.productId}`));
      await Promise.all(promises);
      this.saveCartCache();
      wx.showToast({ title: "已删除", icon: "success" });
    } catch (err) {
      wx.showToast({ title: "部分删除失败", icon: "none" });
      this.loadCart();
    }
  },

  // 清空购物车
  onClearCart() {
    if (this.data.items.length === 0) return;
    wx.showModal({
      title: "提示",
      content: "确定清空购物车吗？",
      confirmColor: "#bf4d68",
      success: (res) => {
        if (res.confirm) {
          this.clearCart();
        }
      },
    });
  },

  // 执行清空
  async clearCart() {
    const userId = getUserId();
    // 乐观更新
    this.setData({ items: [] }, () => this.calcTotal());

    try {
      const res = await del(`/cart/${userId}`);
      if (!res.success) {
        wx.showToast({ title: res.message || "清空失败", icon: "none" });
        this.loadCart();
      } else {
        wx.setStorageSync(CART_CACHE_KEY, { items: [], timestamp: Date.now() });
        wx.showToast({ title: "购物车已清空", icon: "success" });
      }
    } catch (err) {
      wx.showToast({ title: "清空失败", icon: "none" });
      this.loadCart();
    }
  },

  // 切换编辑模式
  onToggleEdit() {
    this.setData({ editing: !this.data.editing });
  },

  // 去购物
  onGoShopping() {
    wx.switchTab({ url: "/pages/home/home" });
  },

  // 去结算
  onCheckout() {
    const selectedItems = this.data.items.filter((item) => item.selected);
    if (selectedItems.length === 0) {
      wx.showToast({ title: "请选择要结算的商品", icon: "none" });
      return;
    }
    const items = selectedItems.map((item) => `${item.productId}:${item.quantity}`).join(",");
    wx.navigateTo({
      url: `/pages/checkout/checkout?items=${encodeURIComponent(items)}`,
    });
  },

  // 点击推荐商品
  onTapRecommend(e) {
    const { id } = e.currentTarget.dataset;
    wx.navigateTo({ url: `/pages/detail/detail?id=${id}` });
  },
});
