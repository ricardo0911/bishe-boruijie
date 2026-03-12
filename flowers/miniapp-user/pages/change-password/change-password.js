const { post } = require("../../utils/request");
const { getCurrentUser, requireLogin } = require("../../utils/auth");

Page({
  data: {
    oldPassword: "",
    newPassword: "",
    confirmPassword: "",
    submitting: false,
  },

  onLoad() {
    if (!requireLogin("/pages/change-password/change-password")) {
      return;
    }
  },

  onInputOldPassword(e) {
    this.setData({ oldPassword: e.detail.value || "" });
  },

  onInputNewPassword(e) {
    this.setData({ newPassword: e.detail.value || "" });
  },

  onInputConfirmPassword(e) {
    this.setData({ confirmPassword: e.detail.value || "" });
  },

  async onSubmit() {
    if (this.data.submitting) return;
    const user = getCurrentUser();
    if (!user) {
      requireLogin("/pages/change-password/change-password");
      return;
    }

    const oldPassword = this.data.oldPassword || "";
    const newPassword = this.data.newPassword || "";
    const confirmPassword = this.data.confirmPassword || "";

    if (oldPassword.length < 6 || newPassword.length < 6 || newPassword.length > 32) {
      wx.showToast({ title: "密码长度需为 6-32 位", icon: "none" });
      return;
    }
    if (newPassword !== confirmPassword) {
      wx.showToast({ title: "两次新密码输入不一致", icon: "none" });
      return;
    }

    this.setData({ submitting: true });
    try {
      const res = await post("/users/change-password", {
        userId: user.id,
        oldPassword,
        newPassword,
      });
      if (!res.success) {
        wx.showToast({ title: res.message || "修改失败", icon: "none" });
        this.setData({ submitting: false });
        return;
      }

      wx.showToast({ title: "修改成功", icon: "success" });
      setTimeout(() => wx.navigateBack(), 500);
    } catch (err) {
      wx.showToast({ title: "网络错误", icon: "none" });
      this.setData({ submitting: false });
    }
  },
});
