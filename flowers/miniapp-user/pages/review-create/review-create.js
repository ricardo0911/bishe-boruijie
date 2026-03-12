const { get, post } = require("../../utils/request");
const { formatPrice, getUserId, resolveImageUrl } = require("../../utils/format");
const { requireLogin } = require("../../utils/auth");

function getApiBase() {
  try {
    const app = getApp();
    return (app && app.globalData && app.globalData.apiBase) || "http://127.0.0.1:18080/api/v1";
  } catch (err) {
    return "http://127.0.0.1:18080/api/v1";
  }
}

function uploadReviewImage(filePath) {
  return new Promise((resolve, reject) => {
    wx.uploadFile({
      url: `${String(getApiBase()).replace(/\/+$/, "")}/upload/image`,
      filePath,
      name: "file",
      success(res) {
        try {
          const payload = JSON.parse(res.data || "{}");
          if (payload && payload.success) {
            resolve(payload.data);
            return;
          }
          reject(new Error((payload && payload.message) || "\u4e0a\u4f20\u5931\u8d25"));
        } catch (error) {
          reject(error);
        }
      },
      fail: reject,
    });
  });
}

function normalizeReviewMap(reviewList, orderNo) {
  return (Array.isArray(reviewList) ? reviewList : []).reduce((result, item) => {
    const currentOrderNo = item.orderNo || item.order_no || "";
    const productId = Number(item.productId || item.product_id || 0);
    if (currentOrderNo !== orderNo || !productId) return result;
    result[productId] = {
      score: Number(item.score || item.rating || 0),
      content: item.content || "",
      createTime: item.createTime || item.create_time || "",
      reply: item.reply || "",
      replyTime: item.replyTime || item.reply_time || "",
      images: Array.isArray(item.images) ? item.images.map((img) => resolveImageUrl(img)) : [],
    };
    return result;
  }, {});
}

Page({
  data: {
    loading: true,
    orderNo: "",
    orderId: 0,
    status: "",
    items: [],
    starOptions: [1, 2, 3, 4, 5],
    submittingProductId: 0,
    uploadingProductId: 0,
  },

  onLoad(options) {
    const orderNo = options.orderNo || "";
    if (!requireLogin(`/pages/review-create/review-create?orderNo=${encodeURIComponent(orderNo)}`)) {
      return;
    }
    if (!orderNo) {
      wx.showToast({ title: "\u8ba2\u5355\u4e0d\u5b58\u5728", icon: "none" });
      setTimeout(() => wx.navigateBack({ delta: 1 }), 800);
      return;
    }
    this.loadData(orderNo);
  },

  async loadData(orderNo) {
    this.setData({ loading: true, orderNo });
    try {
      const userId = getUserId();
      const [orderRes, reviewRes] = await Promise.all([
        get(`/orders/${orderNo}`),
        get(`/reviews/user/${userId}`),
      ]);

      if (!orderRes.success || !orderRes.data) {
        wx.showToast({ title: orderRes.message || "\u52a0\u8f7d\u5931\u8d25", icon: "none" });
        this.setData({ loading: false, items: [] });
        return;
      }

      const reviewMap = normalizeReviewMap(reviewRes.success ? reviewRes.data : [], orderNo);
      const items = Array.isArray(orderRes.data.items)
        ? orderRes.data.items.map((item) => {
            const productId = Number(item.productId || item.product_id || 0);
            const existingReview = reviewMap[productId] || null;
            return {
              productId,
              productTitle: item.productTitle || item.productName || item.title || `\u5546\u54c1#${productId}`,
              coverImage: resolveImageUrl(item.coverImage || item.cover_image || ""),
              quantity: Number(item.quantity || 0),
              unitPrice: formatPrice(item.unitPrice || item.unit_price || 0),
              reviewed: !!existingReview,
              score: existingReview ? Number(existingReview.score || 5) : 5,
              content: existingReview ? String(existingReview.content || "") : "",
              createTime: existingReview ? existingReview.createTime || "" : "",
              reply: existingReview ? existingReview.reply || "" : "",
              replyTime: existingReview ? existingReview.replyTime || "" : "",
              images: existingReview && Array.isArray(existingReview.images) ? existingReview.images : [],
            };
          })
        : [];

      this.setData({
        loading: false,
        orderNo,
        orderId: Number(orderRes.data.id || 0),
        status: orderRes.data.status || "",
        items,
      });
    } catch (err) {
      wx.showToast({ title: "\u7f51\u7edc\u9519\u8bef", icon: "none" });
      this.setData({ loading: false, items: [] });
    }
  },

  updateItem(productId, updater) {
    const items = (this.data.items || []).map((item) => {
      if (item.productId !== productId) return item;
      return updater(item);
    });
    this.setData({ items });
  },

  onTapScore(e) {
    const productId = Number(e.currentTarget.dataset.productId || 0);
    const score = Number(e.currentTarget.dataset.score || 0);
    if (!productId || !score) return;
    this.updateItem(productId, (item) => item.reviewed ? item : { ...item, score });
  },

  onInputContent(e) {
    const productId = Number(e.currentTarget.dataset.productId || 0);
    const content = e.detail.value || "";
    this.updateItem(productId, (item) => item.reviewed ? item : { ...item, content });
  },

  onPreviewImages(e) {
    const productId = Number(e.currentTarget.dataset.productId || 0);
    const index = Number(e.currentTarget.dataset.index || 0);
    const currentItem = (this.data.items || []).find((item) => item.productId === productId);
    const urls = currentItem && Array.isArray(currentItem.images) ? currentItem.images : [];
    if (!urls.length) return;
    wx.previewImage({ current: urls[index] || urls[0], urls });
  },

  onRemoveImage(e) {
    const productId = Number(e.currentTarget.dataset.productId || 0);
    const index = Number(e.currentTarget.dataset.index || -1);
    if (!productId || index < 0) return;
    this.updateItem(productId, (item) => {
      if (item.reviewed) return item;
      return { ...item, images: (item.images || []).filter((_, idx) => idx !== index) };
    });
  },

  onChooseImages(e) {
    const productId = Number(e.currentTarget.dataset.productId || 0);
    const currentItem = (this.data.items || []).find((item) => item.productId === productId);
    const currentCount = currentItem && Array.isArray(currentItem.images) ? currentItem.images.length : 0;
    const remain = Math.max(0, 3 - currentCount);
    if (!productId || !currentItem || currentItem.reviewed || remain <= 0) {
      return;
    }

    wx.chooseImage({
      count: remain,
      sizeType: ["compressed"],
      sourceType: ["album", "camera"],
      success: async (res) => {
        const tempFilePaths = Array.isArray(res.tempFilePaths) ? res.tempFilePaths : [];
        if (!tempFilePaths.length) return;
        this.setData({ uploadingProductId: productId });
        wx.showLoading({ title: "\u4e0a\u4f20\u4e2d", mask: true });
        try {
          const uploadedImages = [];
          for (const filePath of tempFilePaths) {
            const uploaded = await uploadReviewImage(filePath);
            uploadedImages.push(resolveImageUrl(uploaded));
          }
          this.updateItem(productId, (item) => ({
            ...item,
            images: (item.images || []).concat(uploadedImages).slice(0, 3),
          }));
        } catch (error) {
          wx.showToast({ title: error.message || "\u56fe\u7247\u4e0a\u4f20\u5931\u8d25", icon: "none" });
        } finally {
          wx.hideLoading();
          this.setData({ uploadingProductId: 0 });
        }
      },
    });
  },

  async onSubmitReview(e) {
    const productId = Number(e.currentTarget.dataset.productId || 0);
    const userId = getUserId();
    const currentItem = (this.data.items || []).find((item) => item.productId === productId);
    if (!productId || !currentItem || currentItem.reviewed) return;
    if (!this.data.orderId || !userId) {
      wx.showToast({ title: "\u8ba2\u5355\u4fe1\u606f\u7f3a\u5931", icon: "none" });
      return;
    }

    this.setData({ submittingProductId: productId });
    try {
      const res = await post("/reviews", {
        orderId: this.data.orderId,
        productId,
        userId,
        score: Number(currentItem.score || 5),
        content: (currentItem.content || "").trim(),
        images: currentItem.images || [],
      });
      if (res.success) {
        wx.showToast({ title: "\u8bc4\u4ef7\u6210\u529f", icon: "success" });
        setTimeout(() => this.loadData(this.data.orderNo), 400);
      } else {
        wx.showToast({ title: res.message || "\u63d0\u4ea4\u5931\u8d25", icon: "none" });
      }
    } catch (err) {
      wx.showToast({ title: "\u7f51\u7edc\u9519\u8bef", icon: "none" });
    } finally {
      this.setData({ submittingProductId: 0 });
    }
  },

  onGoOrderDetail() {
    if (!this.data.orderNo) return;
    wx.navigateTo({
      url: `/pages/order-detail/order-detail?orderNo=${encodeURIComponent(this.data.orderNo)}`,
    });
  },
});