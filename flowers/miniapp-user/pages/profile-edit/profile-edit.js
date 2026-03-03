const { get, put } = require("../../utils/request");
const { getUserId } = require("../../utils/format");

Page({
  data: {
    loading: true,
    saving: false,
    name: "",
    phone: "",
  },

  onLoad() {
    this.loadProfile();
  },

  async loadProfile() {
    this.setData({ loading: true });
    try {
      const res = await get(`/users/${getUserId()}`);
      if (!res.success || !res.data) {
        this.setData({ loading: false });
        wx.showToast({ title: res.message || "加载失败", icon: "none" });
        return;
      }

      this.setData({
        loading: false,
        name: res.data.name || "",
        phone: res.data.phone || "",
      });
    } catch (err) {
      this.setData({ loading: false });
      wx.showToast({ title: "网络错误", icon: "none" });
    }
  },

  onInputName(e) {
    this.setData({ name: e.detail.value || "" });
  },

  onInputPhone(e) {
    this.setData({ phone: e.detail.value || "" });
  },

  validateForm() {
    const name = (this.data.name || "").trim();
    const phone = (this.data.phone || "").trim();
    if (!name) {
      wx.showToast({ title: "昵称不能为空", icon: "none" });
      return false;
    }
    if (phone && !/^1\d{10}$/.test(phone)) {
      wx.showToast({ title: "手机号格式不正确", icon: "none" });
      return false;
    }
    return true;
  },

  async onSave() {
    if (this.data.saving) return;
    if (!this.validateForm()) return;

    this.setData({ saving: true });
    try {
      const res = await put(`/users/${getUserId()}`, {
        name: (this.data.name || "").trim(),
        phone: (this.data.phone || "").trim(),
      });
      if (!res.success) {
        wx.showToast({ title: res.message || "保存失败", icon: "none" });
        this.setData({ saving: false });
        return;
      }
      wx.showToast({ title: "保存成功", icon: "success" });
      setTimeout(() => wx.navigateBack(), 700);
    } catch (err) {
      wx.showToast({ title: "网络错误", icon: "none" });
      this.setData({ saving: false });
    }
  },
});
