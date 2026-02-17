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

function resolveImageUrl(url) {
  if (!url) return "";
  const text = String(url).trim();
  if (!text) return "";
  if (/^https?:\/\//i.test(text)) return text;
  if (text.indexOf("//") === 0) return `https:${text}`;

  let base = "http://127.0.0.1:9090/api/v1";
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
    PAID: "已支付",
    COMPLETED: "已完成",
    CANCELLED: "已取消",
    REFUNDED: "已退款",
  };
  return map[status] || status || "未知";
}

function getStatusClass(status) {
  if (status === "PAID" || status === "COMPLETED") return "status-success";
  if (status === "CANCELLED" || status === "REFUNDED") return "status-danger";
  return "status-default";
}

function getUserId() {
  const stored = wx.getStorageSync("userId");
  if (stored) return Number(stored);
  return 1;
}

module.exports = {
  toNumber,
  formatPrice,
  resolvePrice,
  pickText,
  resolveImageUrl,
  getStatusLabel,
  getStatusClass,
  getUserId,
};
