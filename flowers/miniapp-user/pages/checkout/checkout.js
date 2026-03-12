const { get, post, del } = require("../../utils/request");
const { formatPrice, resolvePrice, resolveImageUrl, resolveMerchantName, getUserId } = require("../../utils/format");
const { requireLogin } = require("../../utils/auth");
const { resolveMemberDiscount } = require("../../utils/member");

const CATEGORY_META = {
  VALENTINE: { short: "爱" },
  DAILY: { short: "日" },
  MOTHER_DAY: { short: "母" },
  BUSINESS: { short: "商" },
  BIRTHDAY: { short: "生" },
};

const DELIVERY_MODES = [
  { key: "STANDARD", label: "标准同城", desc: "当日送达", fee: 8, eta: "预计 4 小时内送达" },
  { key: "COURIER", label: "同城快送", desc: "骑手专送", fee: 10, eta: "预计 3 小时内送达" },
  { key: "EXPRESS", label: "鲜花急送", desc: "2小时极速", fee: 18, eta: "预计 2 小时内送达" },
  { key: "TIMED", label: "定时配送", desc: "按时段送达", fee: 12, eta: "按预约时段送达" },
];

const DELIVERY_SLOTS = [
  { key: "ASAP", label: "尽快送达" },
  { key: "MORNING", label: "10:00-12:00" },
  { key: "AFTERNOON", label: "14:00-18:00" },
  { key: "EVENING", label: "19:00-21:00" },
];

Page({
  data: {
    loading: true,
    submitting: false,
    paying: false,
    fromCart: false,
    orderLines: [],
    orderProducts: [],
    memberPoints: 0,
    memberLevelName: "",
    memberDiscountText: "",
    hasMemberDiscount: false,
    memberDiscountAmount: "0.00",
    totalAmount: "0.00",
    deliveryModes: DELIVERY_MODES,
    deliverySlots: DELIVERY_SLOTS,
    selectedDeliveryMode: "STANDARD",
    selectedDeliverySlot: "ASAP",
    deliveryFee: "0.00",
    discountedGoodsAmount: "0.00",
    payableAmount: "0.00",
    deliveryEta: "预计 4 小时内送达",
    deliveryBenefit: "满199元免标准同城配送费",
    address: null,
    hasDefaultAddress: false,
    addressSource: "remote",
    receiverName: "",
    receiverPhone: "",
    receiverAddress: "",
    remark: "",
  },

  async onLoad(options) {
    if (!requireLogin()) return;
    const lines = this.parseOrderLines(options || {});
    if (!lines.length) {
      wx.showToast({ title: "参数错误", icon: "none" });
      this.setData({ loading: false });
      return;
    }

    this.setData({
      orderLines: lines,
      fromCart: Boolean(options && options.items),
    });

    await Promise.all([this.loadDefaultAddress(), this.loadMemberProfile()]);
    await this.loadOrderProducts();
    this.setData({ loading: false });
  },

  onShow() {
    if (!requireLogin()) return;
    const selectedAddress = wx.getStorageSync("checkout_selected_address");
    if (selectedAddress) {
      this.setAddress(selectedAddress);
      wx.removeStorageSync("checkout_selected_address");
    }
  },

  parseOrderLines(options) {
    if (options.productId) {
      const id = Number(options.productId);
      const quantity = Number(options.quantity || 1);
      if (!id || quantity <= 0) return [];
      return [{ productId: id, quantity }];
    }

    if (options.items) {
      const text = decodeURIComponent(options.items);
      return text
        .split(",")
        .map((part) => part.trim())
        .filter(Boolean)
        .map((part) => {
          const [id, qty] = part.split(":");
          return { productId: Number(id), quantity: Number(qty || 1) };
        })
        .filter((line) => line.productId > 0 && line.quantity > 0);
    }

    return [];
  },

  async loadDefaultAddress() {
    const userId = getUserId();
    if (!userId) {
      this.clearAddress();
      return;
    }

    try {
      const res = await get(`/users/${userId}/addresses/default`);
      if (res.success && res.data) {
        this.setAddress(res.data);
        this.setData({ hasDefaultAddress: true, addressSource: "remote" });
        return;
      }
    } catch (err) {
      // ignore and fallback to list endpoint
    }

    await this.loadFirstAddress();
  },

  async loadFirstAddress() {
    const userId = getUserId();
    if (!userId) {
      this.clearAddress();
      return;
    }

    try {
      const res = await get(`/users/${userId}/addresses`);
      if (res.success && Array.isArray(res.data) && res.data.length > 0) {
        this.setAddress(res.data[0]);
        this.setData({
          hasDefaultAddress: Boolean(res.data[0].isDefault || res.data[0].is_default),
          addressSource: "remote",
        });
        return;
      }
    } catch (err) {
      // keep empty state
    }

    this.clearAddress();
  },

  clearAddress() {
    this.setData({
      address: null,
      hasDefaultAddress: false,
      receiverName: "",
      receiverPhone: "",
      receiverAddress: "",
      addressSource: "remote",
    });
  },

  setAddress(address) {
    if (!address) return;
    this.setData({
      address,
      receiverName: address.receiverName || address.name || "",
      receiverPhone: address.receiverPhone || address.phone || "",
      receiverAddress: this.formatFullAddress(address),
    });
  },

  formatFullAddress(address) {
    const parts = [];
    if (address.province) parts.push(address.province);
    if (address.city) parts.push(address.city);
    if (address.district) parts.push(address.district);
    if (address.detail) parts.push(address.detail);
    if (address.address) parts.push(address.address);
    return parts.join(" ") || address.fullAddress || "";
  },

  onSelectAddress() {
    wx.navigateTo({ url: "/pages/address-list/address-list?from=checkout" });
  },

  async loadOrderProducts() {
    try {
      const res = await get("/products");
      if (!res.success || !Array.isArray(res.data)) {
        wx.showToast({ title: res.message || "商品加载失败", icon: "none" });
        this.setData({ orderProducts: [], totalAmount: "0.00", discountedGoodsAmount: "0.00" });
        this.refreshDeliverySummary(0);
        return;
      }

      const map = {};
      res.data.forEach((item) => {
        map[item.id] = item;
      });

      const orderProducts = [];
      let total = 0;
      this.data.orderLines.forEach((line) => {
        const item = map[line.productId];
        if (!item) return;
        const unitPrice = resolvePrice(item);
        const lineAmount = unitPrice * line.quantity;
        const meta = CATEGORY_META[item.category] || { short: "花" };

        orderProducts.push({
          id: item.id,
          title: item.title || "未命名商品",
          quantity: line.quantity,
          merchantName: resolveMerchantName(item),
          categoryShort: meta.short,
          unitPrice: formatPrice(unitPrice),
          lineAmount: formatPrice(lineAmount),
          coverImage: resolveImageUrl(item.coverImage || item.cover_image || ""),
        });
        total += lineAmount;
      });

      this.setData({ orderProducts, totalAmount: formatPrice(total) });
      this.refreshDeliverySummary(total);
    } catch (err) {
      wx.showToast({ title: "网络错误", icon: "none" });
      this.setData({ orderProducts: [], totalAmount: "0.00", discountedGoodsAmount: "0.00" });
      this.refreshDeliverySummary(0);
    }
  },

  async loadMemberProfile() {
    const userId = getUserId();
    if (!userId) {
      this.setData({ memberPoints: 0 });
      return;
    }

    try {
      const res = await get(`/users/${userId}`);
      if (res.success && res.data) {
        this.setData({ memberPoints: Number(res.data.points || 0) });
        return;
      }
    } catch (err) {
      // ignore member preview errors
    }

    this.setData({ memberPoints: 0 });
  },

  getSelectedDeliveryMode() {
    return DELIVERY_MODES.find((item) => item.key === this.data.selectedDeliveryMode) || DELIVERY_MODES[0];
  },

  getSelectedDeliverySlot() {
    return DELIVERY_SLOTS.find((item) => item.key === this.data.selectedDeliverySlot) || DELIVERY_SLOTS[0];
  },

  calculateDeliveryFee(goodsAmount, mode) {
    if (!mode) return 0;
    if (mode.key === "STANDARD" && goodsAmount >= 199) return 0;
    return Number(mode.fee || 0);
  },

  buildDeliveryBenefit(mode, goodsAmount) {
    if (!mode) return "";
    if (mode.key === "STANDARD") {
      return goodsAmount >= 199 ? "已享满199元标准同城免配送" : "满199元免标准同城配送费";
    }
    if (mode.key === "COURIER") return "同城快送优先派单";
    if (mode.key === "EXPRESS") return "鲜花急送优先调度";
    return "建议至少提前2小时下单";
  },

  refreshDeliverySummary(goodsAmountInput) {
    const goodsAmount = Number.isFinite(goodsAmountInput) ? goodsAmountInput : Number(this.data.totalAmount || 0);
    const mode = this.getSelectedDeliveryMode();
    const memberDiscount = resolveMemberDiscount(this.data.memberPoints, goodsAmount);
    const fee = this.calculateDeliveryFee(goodsAmount, mode);
    const payable = Math.max(0, memberDiscount.discountedGoodsAmountValue + fee);

    this.setData({
      memberLevelName: memberDiscount.levelName,
      memberDiscountText: memberDiscount.discountText,
      hasMemberDiscount: memberDiscount.hasDiscount,
      memberDiscountAmount: memberDiscount.discountAmount,
      discountedGoodsAmount: memberDiscount.discountedGoodsAmount,
      deliveryFee: formatPrice(fee),
      payableAmount: formatPrice(payable),
      deliveryEta: mode.eta,
      deliveryBenefit: this.buildDeliveryBenefit(mode, goodsAmount),
    });
  },

  onInputReceiverName(e) {
    this.setData({ receiverName: e.detail.value || "" });
  },

  onInputReceiverPhone(e) {
    this.setData({ receiverPhone: e.detail.value || "" });
  },

  onInputReceiverAddress(e) {
    this.setData({ receiverAddress: e.detail.value || "" });
  },

  onInputRemark(e) {
    this.setData({ remark: e.detail.value || "" });
  },

  onPickDeliveryMode(e) {
    const { key } = e.currentTarget.dataset;
    if (!key || key === this.data.selectedDeliveryMode) return;
    this.setData({ selectedDeliveryMode: key }, () => this.refreshDeliverySummary());
  },

  onPickDeliverySlot(e) {
    const { key } = e.currentTarget.dataset;
    if (!key || key === this.data.selectedDeliverySlot) return;
    this.setData({ selectedDeliverySlot: key });
  },

  buildOrderRemark() {
    return (this.data.remark || "").trim();
  },

  validateForm() {
    const receiverName = (this.data.receiverName || "").trim();
    const receiverPhone = (this.data.receiverPhone || "").trim();
    const receiverAddress = (this.data.receiverAddress || "").trim();

    if (!receiverName || !receiverPhone || !receiverAddress) {
      wx.showToast({ title: "请填写完整收货信息", icon: "none" });
      return false;
    }

    if (!/^1\d{10}$/.test(receiverPhone)) {
      wx.showToast({ title: "手机号格式不正确", icon: "none" });
      return false;
    }

    return true;
  },

  async onSubmitOrder() {
    if (!requireLogin()) return;
    if (this.data.submitting || this.data.paying) return;
    if (!this.validateForm()) return;
    if (!this.data.orderProducts.length) {
      wx.showToast({ title: "无可下单商品", icon: "none" });
      return;
    }

    this.setData({ submitting: true });
    try {
      const orderItems = this.data.orderLines.map((line) => ({
        productId: line.productId,
        quantity: line.quantity,
      }));

      const orderData = {
        userId: getUserId(),
        items: orderItems,
        packagingFee: 0,
        deliveryFee: Number(this.data.deliveryFee || 0),
        deliveryMode: this.data.selectedDeliveryMode,
        deliverySlot: this.data.selectedDeliverySlot,
        remark: this.buildOrderRemark(),
        receiverName: (this.data.receiverName || "").trim(),
        receiverPhone: (this.data.receiverPhone || "").trim(),
        receiverAddress: (this.data.receiverAddress || "").trim(),
      };

      const res = await post("/orders", orderData);
      if (!res.success || !res.data) {
        wx.showToast({ title: res.message || "下单失败", icon: "none" });
        this.setData({ submitting: false });
        return;
      }

      const createdOrders = Array.isArray(res.data.orders) ? res.data.orders : [];
      const orderNo = createdOrders.length ? createdOrders[0].orderNo : (res.data.orderNo || res.data.id);

      if (this.data.fromCart) {
        try {
          await del(`/cart/${getUserId()}`);
        } catch (err) {
          // ignore
        }
      }

      const payableAmount = res.data.totalAmount == null ? this.data.payableAmount : formatPrice(res.data.totalAmount);

      if (createdOrders.length > 1) {
        wx.setStorageSync("orders_status_filter", "PENDING_PAY");
        this.setData({ submitting: false, paying: false, payableAmount });
        wx.showToast({ title: `已拆分${createdOrders.length}笔订单`, icon: "success" });
        setTimeout(() => {
          wx.switchTab({ url: "/pages/orders/orders" });
        }, 600);
        return;
      }

      this.setData({ submitting: false, paying: false, payableAmount });
      this.goToPayPage(orderNo, payableAmount);
    } catch (err) {
      wx.showToast({ title: "网络错误", icon: "none" });
      this.setData({ submitting: false });
    }
  },

  goToPayPage(orderNo, amount = this.data.payableAmount) {
    const query = `orderNo=${encodeURIComponent(orderNo)}&amount=${encodeURIComponent(amount || "0.00")}`;
    wx.redirectTo({
      url: `/pages/pay/pay?${query}`,
    });
  },

  async requestPayment(orderNo) {
    wx.redirectTo({
      url: `/pages/order-detail/order-detail?orderNo=${orderNo}`,
    });
  },
});
