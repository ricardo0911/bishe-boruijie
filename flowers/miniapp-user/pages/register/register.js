const { post } = require("../../utils/request");
const { getCurrentUser, navigateAfterLogin, saveUserSession } = require("../../utils/auth");

Page({
  data: {
    account: "",
    name: "",
    phone: "",
    password: "",
    confirmPassword: "",
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

  onInputName(e) {
    this.setData({ name: e.detail.value || "" });
  },

  onInputPhone(e) {
    this.setData({ phone: e.detail.value || "" });
  },

  onInputPassword(e) {
    this.setData({ password: e.detail.value || "" });
  },

  onInputConfirmPassword(e) {
    this.setData({ confirmPassword: e.detail.value || "" });
  },

  async onSubmit() {
    if (this.data.submitting) return;

    const account = (this.data.account || "").trim();
    const name = (this.data.name || "").trim();
    const phone = (this.data.phone || "").trim();
    const password = this.data.password || "";
    const confirmPassword = this.data.confirmPassword || "";

    if (!/^[A-Za-z0-9_]{4,32}$/.test(account)) {
      wx.showToast({ title: "账号需为 4-32 位字母数字或下划线", icon: "none" });
      return;
    }
    if (!name) {
      wx.showToast({ title: "请输入昵称", icon: "none" });
      return;
    }
    if (phone && !/^1\d{10}$/.test(phone)) {
      wx.showToast({ title: "手机号格式不正确", icon: "none" });
      return;
    }
    if (password.length < 6 || password.length > 32) {
      wx.showToast({ title: "密码长度需为 6-32 位", icon: "none" });
      return;
    }
    if (password !== confirmPassword) {
      wx.showToast({ title: "两次密码输入不一致", icon: "none" });
      return;
    }

    this.setData({ submitting: true });
    try {
      const res = await post("/users/register", { account, name, phone, password });
      if (!res.success || !res.data) {
        wx.showToast({ title: res.message || "注册失败", icon: "none" });
        this.setData({ submitting: false });
        return;
      }

      saveUserSession(res.data);
      wx.showToast({ title: "注册成功", icon: "success" });
      setTimeout(() => navigateAfterLogin(this.data.redirect), 300);
    } catch (err) {
      wx.showToast({ title: "网络错误", icon: "none" });
      this.setData({ submitting: false });
    }
  },
});
