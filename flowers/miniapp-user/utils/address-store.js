const ADDRESS_STORE_PREFIX = "address_book_";

function toNumberId(value) {
  const n = Number(value);
  return Number.isFinite(n) && n > 0 ? n : 0;
}

function buildStoreKey(userId) {
  return `${ADDRESS_STORE_PREFIX}${userId || "guest"}`;
}

function buildFullAddress(address) {
  const parts = [];
  if (address.province) parts.push(address.province);
  if (address.city) parts.push(address.city);
  if (address.district) parts.push(address.district);
  if (address.detail) parts.push(address.detail);
  if (address.address) parts.push(address.address);
  return parts.join(" ").trim();
}

function normalizeAddress(input) {
  const normalized = {
    id: toNumberId(input.id),
    receiverName: input.receiverName || input.name || "",
    receiverPhone: input.receiverPhone || input.phone || "",
    province: input.province || "",
    city: input.city || "",
    district: input.district || "",
    detail: input.detail || input.address || "",
    isDefault: Boolean(input.isDefault || input.is_default),
    createdAt: input.createdAt || input.created_at || Date.now(),
    updatedAt: Date.now(),
  };

  normalized.fullAddress = buildFullAddress(normalized);
  return normalized;
}

function readAddressList(userId) {
  const key = buildStoreKey(userId);
  try {
    const data = wx.getStorageSync(key);
    return Array.isArray(data) ? data : [];
  } catch (err) {
    return [];
  }
}

function writeAddressList(userId, list) {
  const key = buildStoreKey(userId);
  wx.setStorageSync(key, list);
  return list;
}

function ensureOneDefault(list) {
  if (!list.length) return list;
  const hasDefault = list.some((item) => item.isDefault || item.is_default);
  if (hasDefault) {
    return list.map((item) => ({ ...item, isDefault: Boolean(item.isDefault || item.is_default) }));
  }
  return list.map((item, index) => ({ ...item, isDefault: index === 0 }));
}

function listAddresses(userId) {
  const list = ensureOneDefault(readAddressList(userId));
  return list
    .slice()
    .sort((a, b) => Number(Boolean(b.isDefault)) - Number(Boolean(a.isDefault)) || (b.updatedAt || 0) - (a.updatedAt || 0));
}

function getAddress(userId, addressId) {
  const targetId = toNumberId(addressId);
  if (!targetId) return null;
  const list = readAddressList(userId);
  return list.find((item) => toNumberId(item.id) === targetId) || null;
}

function saveAddress(userId, payload, addressId) {
  const list = readAddressList(userId);
  const targetId = toNumberId(addressId || payload.id) || Date.now();
  const normalized = normalizeAddress({ ...payload, id: targetId });

  const index = list.findIndex((item) => toNumberId(item.id) === targetId);
  if (index >= 0) {
    normalized.createdAt = list[index].createdAt || normalized.createdAt;
    list[index] = normalized;
  } else {
    list.push(normalized);
  }

  let next = list;
  if (normalized.isDefault || list.length === 1) {
    next = list.map((item) => ({
      ...item,
      isDefault: toNumberId(item.id) === targetId,
      updatedAt: toNumberId(item.id) === targetId ? Date.now() : item.updatedAt,
    }));
  }

  const saved = ensureOneDefault(next);
  writeAddressList(userId, saved);
  return saved.find((item) => toNumberId(item.id) === targetId) || null;
}

function setDefaultAddress(userId, addressId) {
  const targetId = toNumberId(addressId);
  if (!targetId) return false;
  const list = readAddressList(userId);
  if (!list.length) return false;

  const exists = list.some((item) => toNumberId(item.id) === targetId);
  if (!exists) return false;

  const next = list.map((item) => ({
    ...item,
    isDefault: toNumberId(item.id) === targetId,
    updatedAt: toNumberId(item.id) === targetId ? Date.now() : item.updatedAt,
  }));
  writeAddressList(userId, next);
  return true;
}

function removeAddress(userId, addressId) {
  const targetId = toNumberId(addressId);
  if (!targetId) return false;

  const list = readAddressList(userId);
  const next = list.filter((item) => toNumberId(item.id) !== targetId);
  if (next.length === list.length) return false;

  writeAddressList(userId, ensureOneDefault(next));
  return true;
}

function getDefaultAddress(userId) {
  const list = listAddresses(userId);
  return list.find((item) => item.isDefault) || list[0] || null;
}

module.exports = {
  listAddresses,
  getAddress,
  saveAddress,
  setDefaultAddress,
  removeAddress,
  getDefaultAddress,
};
