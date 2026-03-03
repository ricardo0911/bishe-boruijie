function getBaseUrl() {
  try {
    const app = getApp();
    return (app && app.globalData && app.globalData.apiBase) || "http://127.0.0.1:18080/api/v1";
  } catch (err) {
    return "http://127.0.0.1:18080/api/v1";
  }
}

function buildRequestUrl(url) {
  const rawUrl = String(url || "");
  if (/^https?:\/\//i.test(rawUrl)) {
    return rawUrl;
  }

  const base = String(getBaseUrl() || "").replace(/\/+$/, "");
  const path = rawUrl.startsWith("/") ? rawUrl : `/${rawUrl}`;
  return `${base}${path}`;
}

function normalizeSuccessPayload(payload) {
  if (payload && typeof payload === "object" && Object.prototype.hasOwnProperty.call(payload, "success")) {
    return payload;
  }
  return {
    success: true,
    data: payload,
    message: "OK",
  };
}

function normalizeErrorPayload(statusCode, payload) {
  const code = `HTTP_${statusCode}`;
  if (payload && typeof payload === "object") {
    return {
      success: false,
      code: payload.code || code,
      message: payload.message || code,
      data: Object.prototype.hasOwnProperty.call(payload, "data") ? payload.data : null,
    };
  }
  return {
    success: false,
    code,
    message: code,
    data: null,
  };
}

function request({ url, method = "GET", data = null, header = {} }) {
  const fullUrl = buildRequestUrl(url);
  const httpMethod = String(method || "GET").toUpperCase();

  return new Promise((resolve, reject) => {
    wx.request({
      url: fullUrl,
      method: httpMethod,
      data,
      timeout: 15000,
      header: {
        "content-type": "application/json",
        ...header,
      },
      success(res) {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(normalizeSuccessPayload(res.data));
          return;
        }
        resolve(normalizeErrorPayload(res.statusCode, res.data));
      },
      fail(err) {
        reject(err);
      },
    });
  });
}

function get(url, data = null) {
  return request({ url, method: "GET", data });
}

function post(url, data) {
  return request({ url, method: "POST", data });
}

function put(url, data) {
  return request({ url, method: "PUT", data });
}

function del(url, data = null) {
  return request({ url, method: "DELETE", data });
}

function patch(url, data) {
  return request({ url, method: "PATCH", data });
}

module.exports = {
  request,
  get,
  post,
  put,
  del,
  patch,
};
