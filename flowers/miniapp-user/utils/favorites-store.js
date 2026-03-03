const FAVORITES_STORE_PREFIX = "favorite_products_";

function toId(value) {
  const n = Number(value);
  return Number.isFinite(n) && n > 0 ? n : 0;
}

function buildStoreKey(userId) {
  return `${FAVORITES_STORE_PREFIX}${userId || "guest"}`;
}

function readFavorites(userId) {
  const key = buildStoreKey(userId);
  try {
    const data = wx.getStorageSync(key);
    return Array.isArray(data) ? data : [];
  } catch (err) {
    return [];
  }
}

function writeFavorites(userId, list) {
  const key = buildStoreKey(userId);
  wx.setStorageSync(key, list);
  return list;
}

function normalizeFavorite(item) {
  return {
    productId: toId(item.productId || item.id),
    title: item.title || "",
    coverImage: item.coverImage || item.cover_image || "",
    createdAt: item.createdAt || item.created_at || Date.now(),
  };
}

function listFavorites(userId) {
  return readFavorites(userId)
    .map(normalizeFavorite)
    .filter((item) => item.productId > 0)
    .sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));
}

function setFavorites(userId, favorites) {
  const list = Array.isArray(favorites)
    ? favorites
      .map(normalizeFavorite)
      .filter((item) => item.productId > 0)
    : [];

  return writeFavorites(userId, list);
}

function hasFavorite(userId, productId) {
  const id = toId(productId);
  if (!id) return false;
  return listFavorites(userId).some((item) => item.productId === id);
}

function addFavorite(userId, product, productId) {
  const id = toId(productId || (product && (product.id || product.productId)));
  if (!id) return false;

  const list = listFavorites(userId);
  const exists = list.some((item) => item.productId === id);
  if (exists) {
    return true;
  }

  const next = [
    normalizeFavorite({
      productId: id,
      title: (product && product.title) || "",
      coverImage: (product && (product.coverImage || product.cover_image)) || "",
      createdAt: Date.now(),
    }),
    ...list,
  ];
  writeFavorites(userId, next);
  return true;
}

function removeFavorite(userId, productId) {
  const id = toId(productId);
  if (!id) return false;

  const list = listFavorites(userId);
  const next = list.filter((item) => item.productId !== id);
  if (next.length === list.length) {
    return false;
  }

  writeFavorites(userId, next);
  return true;
}

module.exports = {
  listFavorites,
  setFavorites,
  hasFavorite,
  addFavorite,
  removeFavorite,
};
