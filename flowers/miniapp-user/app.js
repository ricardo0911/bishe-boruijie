const { restoreUserSession } = require("./utils/auth");

App({
  globalData: {
    apiBase: "http://127.0.0.1:18080/api/v1",
    enableRemoteAddressApi: true,
    enableRemoteFavoritesApi: true,
    enableRemoteCouponApi: true,
    enableRemoteOrderApi: true,
    userId: 0,
    currentUser: null,
    loginMode: "account",
  },

  onLaunch() {
    const session = restoreUserSession();
    if (session) {
      this.globalData.userId = session.id;
      this.globalData.currentUser = session;
    }
  },
});
