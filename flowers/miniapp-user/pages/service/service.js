const { get } = require("../../utils/request");
const { requireLogin } = require("../../utils/auth");

const DEFAULT_SUPPORT = {
  name: "花之都官方花店",
  contactPhone: "400-800-1314",
  email: "",
  address: "北京市朝阳区望京花礼中心",
  serviceHours: "09:00-21:00",
  serviceDesc: "在线客服、电话咨询与售后处理",
};

const FAQ_LIST = [
  {
    question: "配送范围和时间是怎样的？",
    answer: "目前主要支持同城配送。常规时段下单后会尽快安排骑手配送，节日高峰期请以页面说明为准。",
    expanded: true,
  },
  {
    question: "下单后可以修改地址或祝福语吗？",
    answer: "订单未发货前可联系在线客服协助确认；若已进入制作或配送流程，请尽快电话联系门店处理。",
    expanded: false,
  },
  {
    question: "申请退款或售后要怎么操作？",
    answer: "请先进入订单页找到对应订单提交退款申请；如需人工协助，也可在客服中心提交工单或拨打客服电话。",
    expanded: false,
  },
  {
    question: "哪些情况需要尽快联系客服？",
    answer: "如果出现配送时间紧急、收货地址变更、商品异常或需要加急处理，建议直接联系客服。",
    expanded: false,
  },
];

function normalizeSupport(data) {
  const raw = data && typeof data === "object" ? data : {};
  return {
    name: raw.name || DEFAULT_SUPPORT.name,
    contactPhone: raw.contactPhone || DEFAULT_SUPPORT.contactPhone,
    email: raw.email || DEFAULT_SUPPORT.email,
    address: raw.address || DEFAULT_SUPPORT.address,
    serviceHours: raw.serviceHours || DEFAULT_SUPPORT.serviceHours,
    serviceDesc: raw.serviceDesc || DEFAULT_SUPPORT.serviceDesc,
  };
}

Page({
  data: {
    loading: true,
    support: DEFAULT_SUPPORT,
    faqList: FAQ_LIST,
  },

  onLoad() {
    this.loadSupportInfo();
  },

  async loadSupportInfo() {
    try {
      const res = await get("/merchants/public/support");
      if (res.success && res.data) {
        this.setData({ support: normalizeSupport(res.data) });
      }
    } catch (err) {
    } finally {
      this.setData({ loading: false });
    }
  },

  onCallPhone() {
    const phone = this.data.support.contactPhone || "";
    if (!phone) {
      wx.showToast({ title: "暂未配置客服电话", icon: "none" });
      return;
    }
    wx.makePhoneCall({ phoneNumber: phone });
  },

  onGoTicketForm() {
    if (!requireLogin("/pages/support-ticket/support-ticket")) {
      return;
    }
    wx.navigateTo({ url: "/pages/support-ticket/support-ticket" });
  },

  onGoTicketList() {
    if (!requireLogin("/pages/support-ticket-list/support-ticket-list")) {
      return;
    }
    wx.navigateTo({ url: "/pages/support-ticket-list/support-ticket-list" });
  },

  onCopyPhone() {
    const phone = this.data.support.contactPhone || "";
    if (!phone) {
      wx.showToast({ title: "暂无可复制号码", icon: "none" });
      return;
    }
    wx.setClipboardData({
      data: phone,
      success: () => wx.showToast({ title: "电话已复制", icon: "success" }),
    });
  },

  onCopyAddress() {
    const address = this.data.support.address || "";
    if (!address) {
      wx.showToast({ title: "暂无门店地址", icon: "none" });
      return;
    }
    wx.setClipboardData({
      data: address,
      success: () => wx.showToast({ title: "地址已复制", icon: "success" }),
    });
  },

  onToggleFaq(e) {
    const index = Number(e.currentTarget.dataset.index);
    const list = (this.data.faqList || []).map((item, currentIndex) => ({
      ...item,
      expanded: currentIndex === index ? !item.expanded : item.expanded,
    }));
    this.setData({ faqList: list });
  },
});
