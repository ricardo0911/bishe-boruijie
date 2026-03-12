const { get, post } = require("../../utils/request");
const { getCurrentUser, requireLogin } = require("../../utils/auth");

const ISSUE_TYPES = [
  { value: "ORDER", label: "订单问题" },
  { value: "DELIVERY", label: "配送问题" },
  { value: "REFUND", label: "退款售后" },
  { value: "PRODUCT", label: "商品问题" },
  { value: "ACCOUNT", label: "账户问题" },
  { value: "OTHER", label: "其他问题" },
];

function trim(value) {
  return String(value || "").trim();
}

Page({
  data: {
    submitting: false,
    issueTypes: ISSUE_TYPES,
    issueType: "ORDER",
    orderNo: "",
    title: "",
    content: "",
    contactName: "",
    contactPhone: "",
    currentUser: null,
    supportPhone: "",
  },

  onLoad() {
    if (!requireLogin("/pages/support-ticket/support-ticket")) {
      return;
    }
    const user = getCurrentUser();
    this.setData({
      currentUser: user,
      contactName: user && user.name ? user.name : "",
      contactPhone: user && user.phone ? user.phone : "",
    });
    this.loadSupportInfo();
  },

  async loadSupportInfo() {
    try {
      const res = await get("/merchants/public/support");
      if (res.success && res.data && res.data.contactPhone) {
        this.setData({ supportPhone: res.data.contactPhone });
      }
    } catch (err) {
    }
  },

  onSelectIssueType(e) {
    const value = e.currentTarget.dataset.value || "ORDER";
    this.setData({ issueType: value });
  },

  onInputOrderNo(e) {
    this.setData({ orderNo: e.detail.value || "" });
  },

  onInputTitle(e) {
    this.setData({ title: e.detail.value || "" });
  },

  onInputContent(e) {
    this.setData({ content: e.detail.value || "" });
  },

  onInputContactName(e) {
    this.setData({ contactName: e.detail.value || "" });
  },

  onInputContactPhone(e) {
    this.setData({ contactPhone: e.detail.value || "" });
  },

  onCallPhone() {
    const phone = trim(this.data.supportPhone);
    if (!phone) {
      wx.showToast({ title: "暂无客服电话", icon: "none" });
      return;
    }
    wx.makePhoneCall({ phoneNumber: phone });
  },

  async onSubmit() {
    if (this.data.submitting) {
      return;
    }

    const user = getCurrentUser();
    if (!user || !user.id) {
      requireLogin("/pages/support-ticket/support-ticket");
      return;
    }

    const payload = {
      userId: user.id,
      issueType: trim(this.data.issueType || "ORDER"),
      orderNo: trim(this.data.orderNo),
      title: trim(this.data.title),
      content: trim(this.data.content),
      contactName: trim(this.data.contactName),
      contactPhone: trim(this.data.contactPhone),
    };

    if (!payload.title) {
      wx.showToast({ title: "请填写问题标题", icon: "none" });
      return;
    }
    if (!payload.content) {
      wx.showToast({ title: "请填写问题描述", icon: "none" });
      return;
    }
    if (!payload.contactName) {
      wx.showToast({ title: "请填写联系人", icon: "none" });
      return;
    }
    if (!payload.contactPhone) {
      wx.showToast({ title: "请填写联系电话", icon: "none" });
      return;
    }

    this.setData({ submitting: true });
    try {
      const res = await post("/support-tickets", payload);
      if (!res.success) {
        wx.showToast({ title: res.message || "提交失败", icon: "none" });
        return;
      }

      const ticketNo = res.data && res.data.ticketNo ? res.data.ticketNo : "--";
      wx.showModal({
        title: "提交成功",
        content: `工单号：${ticketNo}\n客服会尽快与你联系。`,
        showCancel: false,
        success: () => {
          wx.navigateBack({ delta: 1 });
        },
      });
    } catch (err) {
      wx.showToast({ title: "网络异常，请稍后重试", icon: "none" });
    } finally {
      this.setData({ submitting: false });
    }
  },
});
