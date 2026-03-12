const DELIVERY_MODE_LABELS = {
  STANDARD: "标准同城",
  COURIER: "同城快送",
  EXPRESS: "鲜花急送",
  TIMED: "定时配送",
};

const DELIVERY_SLOT_LABELS = {
  ASAP: "尽快送达",
  MORNING: "10:00-12:00",
  AFTERNOON: "14:00-18:00",
  EVENING: "19:00-21:00",
};

function extractLegacyDeliveryRemark(remark) {
  const text = String(remark || "").trim();
  if (!text) return null;

  const match = text.match(/(?:^| \| )配送:([^|]+)\|时段:([^|]+)\|配送费:([^|]+)/);
  if (!match) return null;

  return {
    modeLabel: (match[1] || "").trim(),
    slotLabel: (match[2] || "").trim(),
    feeText: (match[3] || "").trim(),
    fullText: match[0],
  };
}

function stripLegacyDeliveryRemark(remark) {
  const text = String(remark || "").trim();
  const legacy = extractLegacyDeliveryRemark(text);
  if (!legacy) return text;
  return text.replace(legacy.fullText, "").replace(/\s+\|\s+$/, "").trim();
}

function resolveDeliveryModeLabel(deliveryMode, remark) {
  if (deliveryMode && DELIVERY_MODE_LABELS[deliveryMode]) {
    return DELIVERY_MODE_LABELS[deliveryMode];
  }
  return extractLegacyDeliveryRemark(remark)?.modeLabel || "";
}

function resolveDeliverySlotLabel(deliverySlot, remark) {
  if (deliverySlot && DELIVERY_SLOT_LABELS[deliverySlot]) {
    return DELIVERY_SLOT_LABELS[deliverySlot];
  }
  return extractLegacyDeliveryRemark(remark)?.slotLabel || "";
}

module.exports = {
  DELIVERY_MODE_LABELS,
  DELIVERY_SLOT_LABELS,
  extractLegacyDeliveryRemark,
  stripLegacyDeliveryRemark,
  resolveDeliveryModeLabel,
  resolveDeliverySlotLabel,
};
