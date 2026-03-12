function toNumber(value, fallback = 0) {
  const n = Number(value);
  return Number.isFinite(n) ? n : fallback;
}

function formatPrice(value) {
  return toNumber(value, 0).toFixed(2);
}

function resolvePrice(data) {
  if (!data) return 0;
  if (data.unitPrice !== undefined && data.unitPrice !== null) {
    return toNumber(data.unitPrice, 0);
  }
  if (data.autoPrice !== undefined && data.autoPrice !== null) {
    return toNumber(data.autoPrice, 0);
  }
  if (data.unit_price !== undefined && data.unit_price !== null) {
    return toNumber(data.unit_price, 0);
  }
  return 0;
}

function pickText(data, keys) {
  if (!data || !Array.isArray(keys)) return "";
  for (let i = 0; i < keys.length; i += 1) {
    const key = keys[i];
    if (data[key] !== undefined && data[key] !== null) {
      return String(data[key]);
    }
  }
  return "";
}

function resolveMerchantName(data, fallback = "官方花店") {
  const text = pickText(data, ["merchantName", "merchant_name", "shopName", "shop_name"]);
  return text && text.trim() ? text.trim() : fallback;
}

function resolveImageUrl(url) {
  if (!url) return "";
  const text = String(url).trim();
  if (!text) return "";
  if (/^https?:\/\//i.test(text)) return text;
  if (text.indexOf("//") === 0) return `https:${text}`;

  let base = "http://127.0.0.1:8080/api/v1";
  try {
    const app = getApp();
    if (app && app.globalData && app.globalData.apiBase) {
      base = String(app.globalData.apiBase);
    }
  } catch (err) {
    // keep default
  }

  const apiIndex = base.indexOf("/api/");
  const origin = apiIndex > -1 ? base.slice(0, apiIndex) : base.replace(/\/+$/, "");
  if (text.indexOf("/") === 0) {
    return `${origin}${text}`;
  }
  return `${origin}/${text}`;
}

function getStatusLabel(status) {
  const map = {
    CREATED: "待支付",
    LOCKED: "待支付",
    PENDING_PAY: "待支付",
    PAID: "待发货",
    CONFIRMED: "待收货",
    REFUND_REQUESTED: "退款审核中",
    REFUNDING: "退款处理中",
    REFUND_FAILED: "退款失败",
    SHIPPED: "待收货",
    COMPLETED: "已完成",
    CANCELLED: "已取消",
    REFUNDED: "已退款",
  };
  return map[status] || status || "未知";
}

function getStatusClass(status) {
  if (status === "PAID" || status === "COMPLETED") return "status-success";
  if (status === "CANCELLED" || status === "REFUNDED") return "status-danger";
  if (status === "PENDING_PAY") return "status-warning";
  if (status === "REFUND_REQUESTED" || status === "REFUNDING" || status === "REFUND_FAILED") return "status-warning";
  if (status === "SHIPPED" || status === "CONFIRMED") return "status-info";
  return "status-default";
}

function getUserId() {
  const stored = wx.getStorageSync("userId");
  const uid = Number(stored);
  if (Number.isFinite(uid) && uid > 0) {
    return uid;
  }
  try {
    const app = getApp();
    const current = Number(app && app.globalData ? app.globalData.userId : 0);
    return Number.isFinite(current) && current > 0 ? current : 0;
  } catch (err) {
    return 0;
  }
}

module.exports = {
  toNumber,
  formatPrice,
  resolvePrice,
  pickText,
  resolveMerchantName,
  resolveImageUrl,
  getStatusLabel,
  getStatusClass,
  getUserId,
};
