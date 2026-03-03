const { get, post, put } = require("../../utils/request");
const { getUserId } = require("../../utils/format");

Page({
  data: {
    isEdit: false,
    addressId: null,
    saving: false,
    source: "remote",
    regionValue: ["", "", ""],
    form: {
      receiverName: "",
      receiverPhone: "",
      province: "",
      city: "",
      district: "",
      detail: "",
      isDefault: false,
    },
  },

  onLoad(options) {
    if (options && options.id) {
      this.setData({
        isEdit: true,
        addressId: options.id,
      });
      this.loadAddress(options.id);
    }
  },

  async loadAddress(addressId) {
    const userId = getUserId();
    try {
      const res = await get(`/users/${userId}/addresses/${addressId}`);
      if (res.success && res.data) {
        this.applyAddressToForm(res.data, "remote");
        return;
      }
      wx.showToast({ title: res.message || "地址不存在", icon: "none" });
    } catch (err) {
      wx.showToast({ title: "网络错误", icon: "none" });
    }
  },

  applyAddressToForm(data, source) {
    const province = data.province || "";
    const city = data.city || "";
    const district = data.district || "";

    this.setData({
      source,
      regionValue: [province, city, district],
      form: {
        receiverName: data.receiverName || data.name || "",
        receiverPhone: data.receiverPhone || data.phone || "",
        province,
        city,
        district,
        detail: data.detail || data.address || "",
        isDefault: Boolean(data.isDefault || data.is_default),
      },
    });
  },

  onInputName(e) {
    this.setData({ "form.receiverName": e.detail.value });
  },

  onInputPhone(e) {
    this.setData({ "form.receiverPhone": e.detail.value });
  },

  onInputDetail(e) {
    this.setData({ "form.detail": e.detail.value });
  },

  onRegionChange(e) {
    const region = (e && e.detail && e.detail.value) || [];
    const province = region[0] || "";
    const city = region[1] || "";
    const district = region[2] || "";
    this.setData({
      regionValue: [province, city, district],
      "form.province": province,
      "form.city": city,
      "form.district": district,
    });
  },

  onToggleDefault(e) {
    this.setData({ "form.isDefault": e.detail.value });
  },

  validateForm() {
    const { receiverName, receiverPhone, province, detail } = this.data.form;

    if (!receiverName.trim()) {
      wx.showToast({ title: "请输入收货人姓名", icon: "none" });
      return false;
    }

    if (!receiverPhone.trim()) {
      wx.showToast({ title: "请输入联系电话", icon: "none" });
      return false;
    }

    if (!/^1\d{10}$/.test(receiverPhone)) {
      wx.showToast({ title: "手机号格式不正确", icon: "none" });
      return false;
    }

    if (!province) {
      wx.showToast({ title: "请选择所在地区", icon: "none" });
      return false;
    }

    if (!detail.trim()) {
      wx.showToast({ title: "请输入详细地址", icon: "none" });
      return false;
    }

    return true;
  },

  async onSave() {
    if (this.data.saving) return;
    if (!this.validateForm()) return;

    this.setData({ saving: true });

    const userId = getUserId();
    const { form, isEdit, addressId } = this.data;

    const data = {
      receiverName: form.receiverName.trim(),
      receiverPhone: form.receiverPhone.trim(),
      province: form.province,
      city: form.city,
      district: form.district,
      detail: form.detail.trim(),
      isDefault: form.isDefault,
    };

    try {
      let res;
      if (isEdit) {
        res = await put(`/users/${userId}/addresses/${addressId}`, data);
      } else {
        res = await post(`/users/${userId}/addresses`, data);
      }

      if (res.success) {
        wx.showToast({ title: isEdit ? "修改成功" : "添加成功", icon: "success" });
        wx.setStorageSync("address_list_need_refresh", true);
        setTimeout(() => {
          wx.navigateBack();
        }, 800);
        return;
      }

      wx.showToast({ title: res.message || "保存失败", icon: "none" });
    } catch (err) {
      wx.showToast({ title: "网络错误", icon: "none" });
    }

    this.setData({ saving: false });
  },
});
