const { get } = require("../../utils/request");
const { getCurrentUser, requireLogin } = require("../../utils/auth");

const STATUS_MAP = {
  PENDING: "待处理",
  PROCESSING: "处理中",
  RESOLVED: "已解决",
  CLOSED: "已关闭",
};

const ISSUE_TYPE_MAP = {
  ORDER: "订单问题",
  DELIVERY: "配送问题",
  REFUND: "退款售后",
  PRODUCT: "商品问题",
  ACCOUNT: "账户问题",
  OTHER: "其他问题",
};

const STATUS_FILTERS = [
  { value: "", label: "全部" },
  { value: "PENDING", label: "待处理" },
  { value: "PROCESSING", label: "处理中" },
  { value: "RESOLVED", label: "已解决" },
  { value: "CLOSED", label: "已关闭" },
];

function formatTime(value) {
  return value ? String(value).replace("T", " ").slice(0, 16) : "";
}

function normalizeTicket(item) {
  const raw = item && typeof item === "object" ? item : {};
  return {
    id: Number(raw.id || 0),
    ticketNo: raw.ticketNo || raw.ticket_no || "",
    userId: Number(raw.userId || raw.user_id || 0),
    orderNo: raw.orderNo || raw.order_no || "",
    issueType: raw.issueType || raw.issue_type || "OTHER",
    issueTypeText: ISSUE_TYPE_MAP[raw.issueType || raw.issue_type] || raw.issueType || raw.issue_type || "其他问题",
    title: raw.title || "",
    content: raw.content || "",
    contactName: raw.contactName || raw.contact_name || "",
    contactPhone: raw.contactPhone || raw.contact_phone || "",
    status: raw.status || "PENDING",
    statusText: STATUS_MAP[raw.status] || raw.status || "待处理",
    handleNote: raw.handleNote || raw.handle_note || "",
    createdAt: formatTime(raw.createdAt || raw.created_at || ""),
    updatedAt: formatTime(raw.updatedAt || raw.updated_at || ""),
    processedAt: formatTime(raw.processedAt || raw.processed_at || ""),
  };
}

Page({
  data: {
    loading: true,
    currentStatus: "",
    statusFilters: STATUS_FILTERS,
    tickets: [],
  },

  onShow() {
    if (!requireLogin("/pages/support-ticket-list/support-ticket-list")) {
      return;
    }
    this.loadTickets();
  },

  onPullDownRefresh() {
    this.loadTickets().finally(() => wx.stopPullDownRefresh());
  },

  onChangeStatus(e) {
    const status = e.currentTarget.dataset.status || "";
    this.setData({ currentStatus: status });
    this.loadTickets();
  },

  async loadTickets() {
    const currentUser = getCurrentUser();
    if (!currentUser || !currentUser.id) {
      this.setData({ loading: false, tickets: [] });
      return;
    }

    this.setData({ loading: true });
    try {
      let url = `/support-tickets/user/${currentUser.id}?limit=50`;
      if (this.data.currentStatus) {
        url += `&status=${encodeURIComponent(this.data.currentStatus)}`;
      }
      const res = await get(url);
      if (!res.success || !Array.isArray(res.data)) {
        wx.showToast({ title: res.message || "加载失败", icon: "none" });
        this.setData({ loading: false, tickets: [] });
        return;
      }
      this.setData({
        loading: false,
        tickets: res.data.map((item) => normalizeTicket(item)),
      });
    } catch (err) {
      wx.showToast({ title: "网络错误", icon: "none" });
      this.setData({ loading: false, tickets: [] });
    }
  },

  onGoTicketForm() {
    wx.navigateTo({ url: "/pages/support-ticket/support-ticket" });
  },

  onGoOrder(e) {
    const orderNo = e.currentTarget.dataset.orderNo || "";
    if (!orderNo) return;
    wx.navigateTo({ url: `/pages/order-detail/order-detail?orderNo=${encodeURIComponent(orderNo)}` });
  },
});