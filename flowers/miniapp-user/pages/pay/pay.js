const { get, post } = require("../../utils/request");
const { formatPrice } = require("../../utils/format");
const { requireLogin } = require("../../utils/auth");

const DEFAULT_MERCHANT_NAME = "花之都官方花店";
const PASSWORD_BOXES = [0, 1, 2, 3, 4, 5];
const KEYPAD_ROWS = [
  [
    { key: "1", label: "1", type: "digit" },
    { key: "2", label: "2", type: "digit" },
    { key: "3", label: "3", type: "digit" },
  ],
  [
    { key: "4", label: "4", type: "digit" },
    { key: "5", label: "5", type: "digit" },
    { key: "6", label: "6", type: "digit" },
  ],
  [
    { key: "7", label: "7", type: "digit" },
    { key: "8", label: "8", type: "digit" },
    { key: "9", label: "9", type: "digit" },
  ],
  [
    { key: "blank", label: "", type: "blank" },
    { key: "0", label: "0", type: "digit" },
    { key: "delete", label: "删除", type: "action" },
  ],
];

function toFen(amount) {
  const n = Number(amount || 0);
  if (!Number.isFinite(n) || n <= 0) return "0";
  return String(Math.round(n * 100));
}

function nowWxTimeEnd() {
  const d = new Date();
  const yyyy = d.getFullYear();
  const MM = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  const hh = String(d.getHours()).padStart(2, "0");
  const mm = String(d.getMinutes()).padStart(2, "0");
  const ss = String(d.getSeconds()).padStart(2, "0");
  return `${yyyy}${MM}${dd}${hh}${mm}${ss}`;
}

function pickMerchantName(orderData, firstItem) {
  return (
    orderData.merchantName ||
    orderData.merchant_name ||
    orderData.shopName ||
    orderData.shop_name ||
    firstItem.merchantName ||
    firstItem.merchant_name ||
    DEFAULT_MERCHANT_NAME
  );
}

function buildProductSummary(firstTitle, items) {
  if (items.length > 1) {
    return `${firstTitle} 等${items.length}件商品`;
  }
  return firstTitle;
}

Page({
  data: {
    loading: true,
    submitting: false,
    orderNo: "",
    amount: "0.00",
    orderStatus: "",
    productTitle: "",
    productSummary: "鲜花订单",
    merchantName: DEFAULT_MERCHANT_NAME,
    itemCount: 0,
    showPasswordPopup: false,
    passwordValue: "",
    passwordLength: 0,
    passwordBoxes: PASSWORD_BOXES,
    keypadRows: KEYPAD_ROWS,
  },

  onLoad(options) {
    if (!requireLogin()) return;
    const orderNo = (options.orderNo || "").trim();
    const amount = formatPrice(options.amount || 0);

    if (!orderNo) {
      wx.showToast({ title: "订单号缺失", icon: "none" });
      setTimeout(() => wx.navigateBack(), 400);
      return;
    }

    this.setData({ orderNo, amount });
    this.loadOrderDetail();
  },

  async loadOrderDetail() {
    this.setData({ loading: true });
    try {
      const res = await get(`/orders/${this.data.orderNo}`);
      if (!res.success || !res.data) {
        wx.showToast({ title: res.message || "订单加载失败", icon: "none" });
        this.setData({ loading: false });
        return;
      }

      const orderData = res.data;
      const items = Array.isArray(orderData.items) ? orderData.items : [];
      const firstItem = items[0] || {};
      const firstTitle = firstItem.productTitle || "鲜花订单";
      const itemCount = items.reduce((sum, item) => sum + Number(item.quantity || 0), 0) || items.length || 1;
      const amountSource = orderData.totalAmount == null ? this.data.amount : orderData.totalAmount;
      const amount = formatPrice(amountSource || 0);

      this.setData({
        loading: false,
        amount,
        orderStatus: orderData.status || "",
        productTitle: firstTitle,
        productSummary: buildProductSummary(firstTitle, items),
        merchantName: pickMerchantName(orderData, firstItem),
        itemCount,
      });
    } catch (err) {
      wx.showToast({ title: "网络错误", icon: "none" });
      this.setData({ loading: false });
    }
  },

  noop() {},

  onConfirmPay() {
    if (this.data.submitting || this.data.loading) return;
    this.setData({
      showPasswordPopup: true,
      passwordValue: "",
      passwordLength: 0,
    });
  },

  closePasswordPopup() {
    if (this.data.submitting) return;
    this.setData({
      showPasswordPopup: false,
      passwordValue: "",
      passwordLength: 0,
    });
  },

  onForgotPassword() {
    wx.showToast({ title: "演示版无需校验真实密码", icon: "none" });
  },

  onPasswordKeyTap(e) {
    if (this.data.submitting) return;

    const { key, type } = e.currentTarget.dataset;
    if (type === "blank") return;

    if (type === "action") {
      const passwordValue = this.data.passwordValue.slice(0, -1);
      this.setData({
        passwordValue,
        passwordLength: passwordValue.length,
      });
      return;
    }

    if (type !== "digit" || this.data.passwordLength >= 6) return;

    const passwordValue = `${this.data.passwordValue}${key}`.slice(0, 6);
    this.setData({
      passwordValue,
      passwordLength: passwordValue.length,
    });

    if (passwordValue.length === 6) {
      setTimeout(() => {
        this.submitPayment(passwordValue);
      }, 160);
    }
  },

  async submitPayment(passwordValue) {
    if (this.data.submitting || passwordValue.length !== 6) return;
    this.setData({ submitting: true });

    try {
      const paymentNo = `WX${Date.now()}`;
      const payRes = await post(`/orders/${this.data.orderNo}/pay`, {
        paymentChannel: "WECHAT_MINI",
        paymentNo,
      });

      if (!payRes.success) {
        wx.showToast({ title: payRes.message || "支付失败", icon: "none" });
        this.resetPasswordInput();
        return;
      }

      const payParams = payRes.data || {};
      const hasWxParams = Boolean(
        payParams.package &&
          (payParams.timeStamp || payParams.timestamp) &&
          (payParams.nonceStr || payParams.nonce_str) &&
          (payParams.paySign || payParams.pay_sign)
      );

      if (hasWxParams) {
        await this.callWxPay(payParams);
      }

      await this.notifyPaymentCallback(paymentNo);
      this.finishPaySuccess();
    } catch (err) {
      const msg = err && err.message ? err.message : "";
      if (msg.includes("cancel")) {
        wx.showToast({ title: "已取消支付", icon: "none" });
      } else {
        wx.showToast({ title: "支付失败", icon: "none" });
      }
      this.resetPasswordInput();
    }
  },

  resetPasswordInput() {
    this.setData({
      submitting: false,
      passwordValue: "",
      passwordLength: 0,
    });
  },

  callWxPay(params) {
    return new Promise((resolve, reject) => {
      wx.requestPayment({
        timeStamp: String(params.timeStamp || params.timestamp),
        nonceStr: params.nonceStr || params.nonce_str,
        package: params.package,
        signType: params.signType || "RSA",
        paySign: params.paySign || params.pay_sign,
        success: resolve,
        fail: reject,
      });
    });
  },

  async notifyPaymentCallback(paymentNo) {
    const payload = {
      orderNo: this.data.orderNo,
      transactionId: paymentNo,
      totalFee: toFen(this.data.amount),
      resultCode: "SUCCESS",
      timeEnd: nowWxTimeEnd(),
      sign: "",
      nonceStr: String(Date.now()),
    };

    try {
      await post("/payment/callback", payload);
    } catch (err) {
    }
  },

  finishPaySuccess() {
    wx.showToast({ title: "支付成功", icon: "success" });
    this.setData({
      submitting: false,
      showPasswordPopup: false,
      passwordValue: "",
      passwordLength: 0,
    });
    setTimeout(() => {
      wx.redirectTo({
        url: `/pages/order-detail/order-detail?orderNo=${this.data.orderNo}`,
      });
    }, 700);
  },

  onCancelPay() {
    if (this.data.showPasswordPopup) {
      this.closePasswordPopup();
      return;
    }

    wx.redirectTo({
      url: `/pages/order-detail/order-detail?orderNo=${this.data.orderNo}`,
    });
  },
});
