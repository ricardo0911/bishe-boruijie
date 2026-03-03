App({
  globalData: {
    apiBase: "http://127.0.0.1:18080/api/v1",
    enableRemoteAddressApi: true,
    enableRemoteFavoritesApi: true,
    enableRemoteCouponApi: true,
    enableRemoteOrderApi: true,
    userId: 0,
    loginMode: "backend",
  },

  onLaunch() {
    const userId = Number(wx.getStorageSync("userId") || 0);
    if (userId > 0) {
      this.globalData.userId = userId;
    }

    // Always sync with backend so stale local userId does not cause 400 on profile/address APIs.
    this.ensureBackendUser();
  },

  ensureBackendUser() {
    let openid = wx.getStorageSync("openid");
    if (!openid) {
      openid = `wx_u_${Date.now()}`;
      wx.setStorageSync("openid", openid);
    }

    wx.request({
      url: `${this.globalData.apiBase}/users/login`,
      method: "POST",
      data: {
        openid,
        name: "微信用户",
      },
      timeout: 10000,
      success: (res) => {
        const body = res.data || {};
        if (body.success && body.data && body.data.id) {
          const uid = Number(body.data.id);
          this.globalData.userId = uid;
          wx.setStorageSync("userId", uid);
        } else {
          this.globalData.userId = 0;
          wx.removeStorageSync("userId");
        }
      },
      fail: () => {
        this.globalData.userId = 0;
        wx.removeStorageSync("userId");
      },
    });
  },
});
