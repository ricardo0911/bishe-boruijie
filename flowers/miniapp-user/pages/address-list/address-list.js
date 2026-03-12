const { get, del, patch } = require("../../utils/request");
const { getUserId } = require("../../utils/format");
const { requireLogin } = require("../../utils/auth");

function normalizeAddress(item) {
  const address = {
    ...item,
    receiverName: item.receiverName || item.name || "",
    receiverPhone: item.receiverPhone || item.phone || "",
    isDefault: Boolean(item.isDefault || item.is_default),
  };

  const parts = [];
  if (address.province) parts.push(address.province);
  if (address.city) parts.push(address.city);
  if (address.district) parts.push(address.district);
  if (address.detail) parts.push(address.detail);
  if (address.address) parts.push(address.address);
  address.fullAddress = address.fullAddress || address.full_address || parts.join(" ").trim();
  return address;
}

function sortDefaultFirst(list) {
  return list.slice().sort((a, b) => Number(Boolean(b.isDefault)) - Number(Boolean(a.isDefault)));
}

Page({
  data: {
    loading: true,
    addresses: [],
    from: "",
    source: "remote",
  },

  onLoad(options) {
    if (!requireLogin()) return;
    this.setData({
      from: (options && options.from) || "",
    });
    this.loadAddresses();
  },

  onShow() {
    if (!requireLogin()) return;
    const needRefresh = wx.getStorageSync("address_list_need_refresh");
    if (needRefresh) {
      wx.removeStorageSync("address_list_need_refresh");
      this.loadAddresses();
    }
  },

  async loadAddresses() {
    const userId = getUserId();
    if (!userId) {
      this.setData({ loading: false, addresses: [], source: "remote" });
      return;
    }

    this.setData({ loading: true });

    try {
      const res = await get(`/users/${userId}/addresses`);
      if (res.success && Array.isArray(res.data)) {
        const addresses = sortDefaultFirst(res.data.map(normalizeAddress));
        this.setData({ addresses, loading: false, source: "remote" });
        return;
      }

      this.setData({ addresses: [], loading: false, source: "remote" });
      wx.showToast({ title: res.message || "地址加载失败", icon: "none" });
    } catch (err) {
      this.setData({ addresses: [], loading: false, source: "remote" });
      wx.showToast({ title: "网络错误", icon: "none" });
    }
  },

  onSelectAddress(e) {
    const address = e.currentTarget.dataset.address;
    if (!address) return;

    if (this.data.from === "checkout") {
      wx.setStorageSync("checkout_selected_address", address);
      wx.navigateBack();
      return;
    }

    wx.showActionSheet({
      itemList: ["设为默认", "编辑"],
      success: (res) => {
        if (res.tapIndex === 0) {
          this.setDefaultAddress(address.id);
        } else if (res.tapIndex === 1) {
          this.editAddress(address.id);
        }
      },
    });
  },

  async setDefaultAddress(addressId) {
    const userId = getUserId();
    try {
      const res = await patch(`/users/${userId}/addresses/${addressId}/default`, {});
      if (res.success) {
        wx.showToast({ title: "设置成功", icon: "success" });
        this.loadAddresses();
        return;
      }
      wx.showToast({ title: res.message || "设置失败", icon: "none" });
    } catch (err) {
      wx.showToast({ title: "网络错误", icon: "none" });
    }
  },

  onEditAddress(e) {
    const id = e.currentTarget.dataset.id;
    this.editAddress(id);
  },

  editAddress(id) {
    wx.navigateTo({
      url: `/pages/address-edit/address-edit?id=${id}`,
    });
  },

  onDeleteAddress(e) {
    const id = e.currentTarget.dataset.id;
    wx.showModal({
      title: "提示",
      content: "确定删除该地址吗？",
      confirmColor: "#bf4d68",
      success: (res) => {
        if (res.confirm) {
          this.deleteAddress(id);
        }
      },
    });
  },

  async deleteAddress(addressId) {
    const userId = getUserId();
    try {
      const res = await del(`/users/${userId}/addresses/${addressId}`);
      if (res.success) {
        wx.showToast({ title: "删除成功", icon: "success" });
        this.loadAddresses();
        return;
      }
      wx.showToast({ title: res.message || "删除失败", icon: "none" });
    } catch (err) {
      wx.showToast({ title: "网络错误", icon: "none" });
    }
  },

  onAddAddress() {
    wx.navigateTo({
      url: "/pages/address-edit/address-edit",
    });
  },
});
