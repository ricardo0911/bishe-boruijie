App({
  globalData: {
    apiBase: "http://127.0.0.1:9090/api/v1",
    userId: 1,
    loginMode: "mock",
  },

  onLaunch() {
    const userId = wx.getStorageSync("userId");
    if (userId) {
      this.globalData.userId = Number(userId);
      return;
    }

    this.mockLogin();
  },

  mockLogin() {
    let openid = wx.getStorageSync("mockOpenid");
    if (!openid) {
      openid = `mock_openid_${Date.now()}`;
      wx.setStorageSync("mockOpenid", openid);
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
          this.globalData.userId = 1;
          wx.setStorageSync("userId", 1);
        }
      },
      fail: () => {
        this.globalData.userId = 1;
        wx.setStorageSync("userId", 1);
      },
    });
  },
});
