const COUPON_STORE_PREFIX = "coupon_book_";

const DEFAULT_COUPONS = [
  {
    id: -101,
    name: "花之都新客券",
    type: "AMOUNT",
    value: 12,
    minOrderAmount: 99,
    description: "满99可用",
  },
  {
    id: -102,
    name: "鲜花满减券",
    type: "AMOUNT",
    value: 25,
    minOrderAmount: 199,
    description: "满199可用",
  },
  {
    id: -103,
    name: "节日心意券",
    type: "PERCENT",
    value: 95,
    minOrderAmount: 0,
    description: "95折",
  },
];

function toId(value) {
  const n = Number(value);
  return Number.isFinite(n) ? n : 0;
}

function buildStoreKey(userId) {
  return `${COUPON_STORE_PREFIX}${userId || "guest"}`;
}

function normalizeCoupon(item) {
  return {
    id: toId(item.id),
    name: item.name || item.title || "优惠券",
    type: item.type === "PERCENT" ? "PERCENT" : "AMOUNT",
    value: Number(item.value || item.amount || 0),
    minOrderAmount: Number(item.minOrderAmount || item.min_order_amount || 0),
    description: item.description || "",
    validUntil: item.validUntil || item.valid_until || "",
    isLocal: item.isLocal !== false,
  };
}

function readCoupons(userId) {
  const key = buildStoreKey(userId);
  try {
    const data = wx.getStorageSync(key);
    return Array.isArray(data) ? data : [];
  } catch (err) {
    return [];
  }
}

function writeCoupons(userId, list) {
  const key = buildStoreKey(userId);
  wx.setStorageSync(key, list);
  return list;
}

function seedCoupons(userId) {
  const seeded = DEFAULT_COUPONS.map((item) => normalizeCoupon(item));
  writeCoupons(userId, seeded);
  return seeded;
}

function getUserCoupons(userId) {
  const stored = readCoupons(userId)
    .map(normalizeCoupon)
    .filter((item) => item.id !== 0 && item.value > 0);

  const coupons = stored.length > 0 ? stored : seedCoupons(userId);
  return coupons.sort((a, b) => a.minOrderAmount - b.minOrderAmount || a.id - b.id);
}

function setUserCoupons(userId, coupons) {
  const list = Array.isArray(coupons)
    ? coupons
      .map(normalizeCoupon)
      .filter((item) => item.id !== 0 && item.value > 0)
    : [];

  if (!list.length) {
    return writeCoupons(userId, []);
  }

  return writeCoupons(userId, list);
}

function consumeUserCoupon(userId, couponId) {
  const id = toId(couponId);
  if (!id) return getUserCoupons(userId);

  const list = getUserCoupons(userId);
  const next = list.filter((item) => toId(item.id) !== id);
  writeCoupons(userId, next);
  return next;
}

module.exports = {
  getUserCoupons,
  setUserCoupons,
  consumeUserCoupon,
  normalizeCoupon,
};
