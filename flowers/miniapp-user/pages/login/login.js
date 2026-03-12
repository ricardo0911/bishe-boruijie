const { post } = require("../../utils/request");
const { getCurrentUser, navigateAfterLogin, saveUserSession } = require("../../utils/auth");

Page({
  data: {
    account: "",
    password: "",
    submitting: false,
    redirect: "/pages/home/home",
  },

  onLoad(options) {
    const redirect = options && options.redirect ? decodeURIComponent(options.redirect) : "/pages/home/home";
    this.setData({ redirect });

    if (getCurrentUser()) {
      navigateAfterLogin(redirect);
    }
  },

  onInputAccount(e) {
    this.setData({ account: e.detail.value || "" });
  },

  onInputPassword(e) {
    this.setData({ password: e.detail.value || "" });
  },

  async onSubmit() {
    if (this.data.submitting) return;

    const account = (this.data.account || "").trim();
    const password = this.data.password || "";
    if (!/^[A-Za-z0-9_]{4,32}$/.test(account)) {
      wx.showToast({ title: "账号需为 4-32 位字母数字或下划线", icon: "none" });
      return;
    }
    if (password.length < 6 || password.length > 32) {
      wx.showToast({ title: "密码长度需为 6-32 位", icon: "none" });
      return;
    }

    this.setData({ submitting: true });
    try {
      const res = await post("/users/login", { account, password });
      if (!res.success || !res.data) {
        wx.showToast({ title: res.message || "登录失败", icon: "none" });
        this.setData({ submitting: false });
        return;
      }

      saveUserSession(res.data);
      wx.showToast({ title: "登录成功", icon: "success" });
      setTimeout(() => navigateAfterLogin(this.data.redirect), 300);
    } catch (err) {
      wx.showToast({ title: "网络错误", icon: "none" });
      this.setData({ submitting: false });
    }
  },

  onGoRegister() {
    const redirect = encodeURIComponent(this.data.redirect || "/pages/home/home");
    wx.navigateTo({ url: `/pages/register/register?redirect=${redirect}` });
  },
});
