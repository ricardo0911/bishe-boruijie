const { get, post, del } = require("../../utils/request");
const { formatPrice, resolvePrice, resolveImageUrl, getUserId } = require("../../utils/format");

const CATEGORY_META = {
  VALENTINE: { short: "爱" },
  DAILY: { short: "日" },
  MOTHER_DAY: { short: "母" },
  BUSINESS: { short: "商" },
  BIRTHDAY: { short: "生" },
};

const DELIVERY_MODES = [
  {
    key: "STANDARD",
    label: "标准同城",
    desc: "当日送达",
    fee: 8,
    eta: "预计 4 小时内送达",
  },
  {
    key: "COURIER",
    label: "同城快递",
    desc: "骑手专送",
    fee: 10,
    eta: "预计 3 小时内送达",
  },
  {
    key: "EXPRESS",
    label: "鲜花急送",
    desc: "2小时极速达",
    fee: 18,
    eta: "预计 2 小时内送达",
  },
  {
    key: "TIMED",
    label: "定时配送",
    desc: "按时段送达",
    fee: 12,
    eta: "按预约时段送达",
  },
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
    fromCart: false,
    orderLines: [],
    orderProducts: [],
    totalAmount: "0.00",
    deliveryModes: DELIVERY_MODES,
    deliverySlots: DELIVERY_SLOTS,
    selectedDeliveryMode: "STANDARD",
    selectedDeliverySlot: "ASAP",
    deliveryFee: "0.00",
    payableAmount: "0.00",
    deliveryEta: "预计 4 小时内送达",
    deliveryBenefit: "满199元免标准同城配送费",
    receiverName: "",
    receiverPhone: "",
    receiverAddress: "",
    remark: "",
  },

  async onLoad(options) {
    console.log("[checkout] delivery modes:", DELIVERY_MODES.map((item) => `${item.key}:${item.label}`).join(", "));
    const parseResult = this.parseOrderLines(options || {});
    if (!parseResult.length) {
      wx.showToast({ title: "参数错误", icon: "none" });
      this.setData({ loading: false });
      return;
    }

    this.setData({
      orderLines: parseResult,
      fromCart: Boolean(options && options.items),
    });

    await Promise.all([this.prefillUser(), this.loadOrderProducts()]);
    this.setData({ loading: false });
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
      const rows = text
        .split(",")
        .map((part) => part.trim())
        .filter(Boolean)
        .map((part) => {
          const arr = part.split(":");
          return {
            productId: Number(arr[0]),
            quantity: Number(arr[1] || 1),
          };
        })
        .filter((line) => line.productId > 0 && line.quantity > 0);
      return rows;
    }

    return [];
  },

  async prefillUser() {
    try {
      const res = await get(`/users/${getUserId()}`);
      if (res.success && res.data) {
        this.setData({
          receiverName: res.data.name || "",
          receiverPhone: res.data.phone || "",
        });
      }
    } catch (err) {
      // ignore
    }
  },

  async loadOrderProducts() {
    try {
      const res = await get("/products");
      if (!res.success || !Array.isArray(res.data)) {
        wx.showToast({ title: res.message || "商品加载失败", icon: "none" });
        this.setData({ orderProducts: [], totalAmount: "0.00" });
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
          title: item.title,
          quantity: line.quantity,
          categoryShort: meta.short,
          unitPrice: formatPrice(unitPrice),
          lineAmount: formatPrice(lineAmount),
          coverImage: resolveImageUrl(item.coverImage || item.cover_image || ""),
        });
        total += lineAmount;
      });

      this.setData({
        orderProducts,
        totalAmount: formatPrice(total),
      });
      this.refreshDeliverySummary(total);
    } catch (err) {
      wx.showToast({ title: "网络错误", icon: "none" });
      this.setData({ orderProducts: [], totalAmount: "0.00" });
      this.refreshDeliverySummary(0);
    }
  },

  getSelectedDeliveryMode() {
    return (
      DELIVERY_MODES.find((item) => item.key === this.data.selectedDeliveryMode) ||
      DELIVERY_MODES[0]
    );
  },

  getSelectedDeliverySlot() {
    return (
      DELIVERY_SLOTS.find((item) => item.key === this.data.selectedDeliverySlot) ||
      DELIVERY_SLOTS[0]
    );
  },

  calculateDeliveryFee(goodsAmount, mode) {
    if (!mode) return 0;
    if (mode.key === "STANDARD" && goodsAmount >= 199) {
      return 0;
    }
    return mode.fee;
  },

  buildDeliveryBenefit(mode, goodsAmount) {
    if (!mode) return "";
    if (mode.key === "STANDARD") {
      return goodsAmount >= 199
        ? "已享满199元标准同城免配送"
        : "满199元免标准同城配送费";
    }
    if (mode.key === "COURIER") {
      return "同城快递优先派单，支持配送前电话联系";
    }
    if (mode.key === "EXPRESS") {
      return "鲜花急送优先调度，节日高峰可能存在延迟";
    }
    return "定时配送建议提前2小时下单";
  },

  refreshDeliverySummary(goodsAmountInput) {
    const goodsAmount = Number.isFinite(goodsAmountInput)
      ? goodsAmountInput
      : Number(this.data.totalAmount || 0);
    const mode = this.getSelectedDeliveryMode();
    const fee = this.calculateDeliveryFee(goodsAmount, mode);
    const payable = goodsAmount + fee;

    this.setData({
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
    if (!key || key === this.data.selectedDeliveryMode) {
      return;
    }
    this.setData({ selectedDeliveryMode: key }, () => {
      this.refreshDeliverySummary();
    });
  },

  onPickDeliverySlot(e) {
    const { key } = e.currentTarget.dataset;
    if (!key || key === this.data.selectedDeliverySlot) {
      return;
    }
    this.setData({ selectedDeliverySlot: key });
  },

  buildOrderRemark() {
    const userRemark = (this.data.remark || "").trim();
    const mode = this.getSelectedDeliveryMode();
    const slot = this.getSelectedDeliverySlot();
    const deliveryRemark = `同城配送:${mode.label}|时段:${slot.label}|配送费:${this.data.deliveryFee}`;
    return userRemark ? `${userRemark} | ${deliveryRemark}` : deliveryRemark;
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
    if (this.data.submitting) return;
    if (!this.validateForm()) return;
    if (!this.data.orderProducts.length) {
      wx.showToast({ title: "无可下单商品", icon: "none" });
      return;
    }

    this.setData({ submitting: true });
    try {
      const res = await post("/orders", {
        userId: getUserId(),
        items: this.data.orderLines,
        deliveryFee: Number(this.data.deliveryFee || 0),
        remark: this.buildOrderRemark(),
        receiverName: (this.data.receiverName || "").trim(),
        receiverPhone: (this.data.receiverPhone || "").trim(),
        receiverAddress: (this.data.receiverAddress || "").trim(),
      });

      if (!res.success || !res.data) {
        wx.showToast({ title: res.message || "下单失败", icon: "none" });
        this.setData({ submitting: false });
        return;
      }

      if (this.data.fromCart) {
        try {
          await del(`/cart/${getUserId()}`);
        } catch (err) {
          // ignore
        }
      }

      wx.showToast({ title: "下单成功", icon: "success" });
      setTimeout(() => {
        wx.switchTab({ url: "/pages/orders/orders" });
      }, 900);
    } catch (err) {
      wx.showToast({ title: "网络错误", icon: "none" });
      this.setData({ submitting: false });
    }
  },
});
