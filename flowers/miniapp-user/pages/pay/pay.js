const { get, post } = require("../../utils/request");
const { formatPrice } = require("../../utils/format");

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

Page({
  data: {
    loading: true,
    submitting: false,
    orderNo: "",
    amount: "0.00",
    orderStatus: "",
    productTitle: "",
    itemCount: 0,
  },

  onLoad(options) {
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

      const items = Array.isArray(res.data.items) ? res.data.items : [];
      const firstTitle = items.length ? (items[0].productTitle || "鲜花订单") : "鲜花订单";
      const itemCount = items.reduce((sum, item) => sum + Number(item.quantity || 0), 0);
      const amount = formatPrice(res.data.totalAmount || this.data.amount || 0);

      this.setData({
        loading: false,
        amount,
        orderStatus: res.data.status || "",
        productTitle: firstTitle,
        itemCount,
      });
    } catch (err) {
      wx.showToast({ title: "网络错误", icon: "none" });
      this.setData({ loading: false });
    }
  },

  async onConfirmPay() {
    if (this.data.submitting || this.data.loading) return;
    this.setData({ submitting: true });

    try {
      const paymentNo = `WX${Date.now()}`;
      const payRes = await post(`/orders/${this.data.orderNo}/pay`, {
        paymentChannel: "WECHAT_MINI",
        paymentNo,
      });

      if (!payRes.success) {
        wx.showToast({ title: payRes.message || "支付失败", icon: "none" });
        this.setData({ submitting: false });
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
      this.setData({ submitting: false });
    }
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
      // ignore callback error in non-wechat environments
    }
  },

  finishPaySuccess() {
    wx.showToast({ title: "支付成功", icon: "success" });
    this.setData({ submitting: false });
    setTimeout(() => {
      wx.redirectTo({
        url: `/pages/order-detail/order-detail?orderNo=${this.data.orderNo}`,
      });
    }, 700);
  },

  onCancelPay() {
    wx.redirectTo({
      url: `/pages/order-detail/order-detail?orderNo=${this.data.orderNo}`,
    });
  },
});
