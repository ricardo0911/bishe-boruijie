const TAB_PAGES = new Set([
  "/pages/home/home",
  "/pages/category/category",
  "/pages/cart/cart",
  "/pages/orders/orders",
  "/pages/profile/profile",
]);

function getAppSafe() {
  try {
    return getApp();
  } catch (err) {
    return null;
  }
}

function normalizeSession(data) {
  if (!data || typeof data !== "object") return null;
  const id = Number(data.id || data.userId || 0);
  const account = String(data.account || "").trim();
  if (!Number.isFinite(id) || id <= 0 || !account) {
    return null;
  }
  return {
    id,
    account,
    name: String(data.name || account || "").trim(),
    phone: String(data.phone || "").trim(),
    points: Number(data.points || 0),
    createdAt: data.createdAt || data.created_at || "",
  };
}

function saveUserSession(data) {
  const session = normalizeSession(data);
  if (!session) {
    clearUserSession();
    return null;
  }
  wx.setStorageSync("userSession", session);
  wx.setStorageSync("userId", session.id);

  const app = getAppSafe();
  if (app && app.globalData) {
    app.globalData.userId = session.id;
    app.globalData.currentUser = session;
  }
  return session;
}

function restoreUserSession() {
  try {
    const session = normalizeSession(wx.getStorageSync("userSession"));
    if (!session) {
      clearUserSession();
      return null;
    }
    return saveUserSession(session);
  } catch (err) {
    clearUserSession();
    return null;
  }
}

function getCurrentUser() {
  const app = getAppSafe();
  const current = normalizeSession(app && app.globalData ? app.globalData.currentUser : null);
  if (current) return current;
  return restoreUserSession();
}

function isLoggedIn() {
  return Boolean(getCurrentUser());
}

function clearUserSession() {
  wx.removeStorageSync("userSession");
  wx.removeStorageSync("userId");
  wx.removeStorageSync("openid");

  const app = getAppSafe();
  if (app && app.globalData) {
    app.globalData.userId = 0;
    app.globalData.currentUser = null;
  }
}

function syncUserSession(patch) {
  const current = getCurrentUser();
  if (!current) return null;
  return saveUserSession({ ...current, ...patch });
}

function buildCurrentPageUrl() {
  const pages = getCurrentPages();
  const current = pages[pages.length - 1];
  if (!current || !current.route) {
    return "/pages/home/home";
  }
  const path = current.route.startsWith("/") ? current.route : `/${current.route}`;
  const options = current.options || {};
  const query = Object.keys(options)
    .map((key) => `${encodeURIComponent(key)}=${encodeURIComponent(options[key])}`)
    .join("&");
  return query ? `${path}?${query}` : path;
}

function stripQuery(url) {
  return String(url || "").split("?")[0];
}

function goToLogin(target) {
  const currentPages = getCurrentPages();
  const current = currentPages[currentPages.length - 1];
  if (current && current.route === "pages/login/login") {
    return false;
  }
  const redirect = encodeURIComponent(target || "/pages/home/home");
  const url = `/pages/login/login?redirect=${redirect}`;
  const currentPath = current && current.route ? `/${current.route}` : "";
  const currentPagePath = stripQuery(currentPath);

  try {
    if (!currentPages.length || TAB_PAGES.has(currentPagePath)) {
      wx.reLaunch({ url });
      return false;
    }

    wx.redirectTo({ url });
    return false;
  } catch (err) {
    wx.navigateTo({ url });
  }
  return false;
}

function requireLogin(target) {
  if (isLoggedIn()) return true;
  return goToLogin(target || buildCurrentPageUrl());
}

function navigateAfterLogin(target) {
  const decoded = String(target || "").trim();
  const destination = decoded ? decodeURIComponent(decoded) : "/pages/home/home";
  const pagePath = stripQuery(destination);
  if (TAB_PAGES.has(pagePath)) {
    wx.switchTab({ url: pagePath });
    return;
  }
  wx.redirectTo({ url: destination });
}

module.exports = {
  clearUserSession,
  getCurrentUser,
  isLoggedIn,
  navigateAfterLogin,
  requireLogin,
  restoreUserSession,
  saveUserSession,
  syncUserSession,
};
