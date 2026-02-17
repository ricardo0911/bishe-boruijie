function getBaseUrl() {
  try {
    const app = getApp();
    return (app && app.globalData && app.globalData.apiBase) || "http://127.0.0.1:9090/api/v1";
  } catch (err) {
    return "http://127.0.0.1:9090/api/v1";
  }
}

function request({ url, method = "GET", data = null }) {
  const base = getBaseUrl();
  const fullUrl = `${base}${url.startsWith("/") ? url : `/${url}`}`;

  return new Promise((resolve, reject) => {
    wx.request({
      url: fullUrl,
      method,
      data,
      timeout: 15000,
      header: {
        "content-type": "application/json",
      },
      success(res) {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(res.data);
        } else {
          reject(new Error(`HTTP_${res.statusCode}`));
        }
      },
      fail(err) {
        reject(err);
      },
    });
  });
}

function get(url) {
  return request({ url, method: "GET" });
}

function post(url, data) {
  return request({ url, method: "POST", data });
}

function put(url, data) {
  return request({ url, method: "PUT", data });
}

function del(url) {
  return request({ url, method: "DELETE" });
}

module.exports = {
  request,
  get,
  post,
  put,
  del,
};
