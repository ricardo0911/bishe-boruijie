const { get } = require("../../utils/request");
const { requireLogin } = require("../../utils/auth");

const SELECTED_MERCHANT_KEY = "selected_merchant";

function normalizeMerchant(item) {
  const raw = item && typeof item === "object" ? item : {};
  return {
    id: raw.id,
    name: raw.name || "",
    address: raw.address || "",
    contactPhone: raw.contactPhone || raw.contact_phone || "",
  };
}

Page({
  data: {
    loading: true,
    merchants: [],
    selectedName: "",
  },

  onLoad() {
    if (!requireLogin()) return;
    this.refreshSelected();
    this.loadMerchants();
  },

  onShow() {
    if (!requireLogin()) return;
    this.refreshSelected();
  },

  refreshSelected() {
    const selected = wx.getStorageSync(SELECTED_MERCHANT_KEY);
    const name = selected && selected.name ? String(selected.name) : "";
    this.setData({ selectedName: name });
  },

  async loadMerchants() {
    this.setData({ loading: true });
    try {
      const res = await get("/merchants/public/list");
      if (res.success && Array.isArray(res.data)) {
        const merchants = res.data.map(normalizeMerchant).filter((m) => m.name);
        this.setData({ merchants, loading: false });
        return;
      }
      wx.showToast({ title: res.message || "门店加载失败", icon: "none" });
    } catch (err) {
      wx.showToast({ title: "网络错误", icon: "none" });
    } finally {
      this.setData({ loading: false });
    }
  },

  onSelectAll() {
    wx.removeStorageSync(SELECTED_MERCHANT_KEY);
    this.setData({ selectedName: "" });
    wx.navigateBack();
  },

  onSelectMerchant(e) {
    const index = Number(e.currentTarget.dataset.index);
    const merchant = (this.data.merchants || [])[index];
    if (!merchant || !merchant.name) return;
    wx.setStorageSync(SELECTED_MERCHANT_KEY, merchant);
    this.setData({ selectedName: merchant.name });
    wx.navigateBack();
  },
});

