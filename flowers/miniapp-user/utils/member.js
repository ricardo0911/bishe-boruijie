const MEMBER_LEVELS = [
  {
    key: "SPROUT",
    minPoints: 300,
    levelName: "新芽会员",
    discountRate: 0.98,
    discountText: "98折",
    summary: "购买时自动享98折会员折扣",
  },
  {
    key: "CRAFT",
    minPoints: 1000,
    levelName: "花匠会员",
    discountRate: 0.95,
    discountText: "95折",
    summary: "购买时自动享95折会员折扣",
  },
  {
    key: "MASTER",
    minPoints: 2000,
    levelName: "花语大师",
    discountRate: 0.9,
    discountText: "9折",
    summary: "购买时自动享9折会员折扣",
  },
];

const DEFAULT_LEVEL = {
  key: "BASIC",
  minPoints: 0,
  levelName: "普通用户",
  discountRate: 1,
  discountText: "无折扣",
  summary: "累计积分可解锁会员折扣",
};

function normalizePoints(pointsInput) {
  const points = Number(pointsInput || 0);
  if (!Number.isFinite(points) || points <= 0) return 0;
  return Math.floor(points);
}

function roundPrice(value) {
  const amount = Number(value || 0);
  if (!Number.isFinite(amount) || amount <= 0) return 0;
  return Math.round(amount * 100) / 100;
}

function formatPrice(value) {
  return roundPrice(value).toFixed(2);
}

function getCurrentLevel(pointsInput) {
  const points = normalizePoints(pointsInput);
  for (let index = MEMBER_LEVELS.length - 1; index >= 0; index -= 1) {
    if (points >= MEMBER_LEVELS[index].minPoints) {
      return MEMBER_LEVELS[index];
    }
  }
  return DEFAULT_LEVEL;
}

function getNextLevel(pointsInput) {
  const points = normalizePoints(pointsInput);
  return MEMBER_LEVELS.find((item) => points < item.minPoints) || null;
}

function getMemberProfile(pointsInput) {
  const points = normalizePoints(pointsInput);
  const currentLevel = getCurrentLevel(points);
  const nextLevel = getNextLevel(points);

  if (!nextLevel) {
    return {
      ...currentLevel,
      points,
      progress: 100,
      hasDiscount: currentLevel.discountRate < 1,
      toNextText: "已达到最高等级，继续保持你的花礼审美",
      nextLevelName: "",
      nextLevelPoints: currentLevel.minPoints,
      gapToNext: 0,
    };
  }

  const progressBase = currentLevel.minPoints || 0;
  const progressRange = Math.max(nextLevel.minPoints - progressBase, 1);
  const progressValue = Math.min(Math.max(points - progressBase, 0), progressRange);

  return {
    ...currentLevel,
    points,
    progress: Math.round((progressValue / progressRange) * 100),
    hasDiscount: currentLevel.discountRate < 1,
    toNextText: `再获得 ${nextLevel.minPoints - points} 积分可升级为 ${nextLevel.levelName}`,
    nextLevelName: nextLevel.levelName,
    nextLevelPoints: nextLevel.minPoints,
    gapToNext: nextLevel.minPoints - points,
  };
}

function getMemberLevels(pointsInput) {
  const points = normalizePoints(pointsInput);
  const currentLevel = getCurrentLevel(points);

  return MEMBER_LEVELS.map((item) => {
    const gap = Math.max(item.minPoints - points, 0);
    const isUnlocked = points >= item.minPoints;
    const isCurrent = item.key === currentLevel.key;

    let statusText = `还差 ${gap} 积分解锁`;
    if (isUnlocked) {
      statusText = isCurrent ? "当前等级" : "已解锁";
    }

    return {
      ...item,
      isUnlocked,
      isCurrent,
      gap,
      statusText,
      benefitText: `购买时自动享${item.discountText}会员折扣`,
      sampleSaveText: `订单满100元约省${formatPrice(100 - 100 * item.discountRate)}元`,
    };
  });
}

function resolveMemberDiscount(pointsInput, goodsAmountInput) {
  const goodsAmount = roundPrice(goodsAmountInput);
  const member = getMemberProfile(pointsInput);

  if (!member.hasDiscount || goodsAmount <= 0) {
    return {
      points: member.points,
      hasDiscount: false,
      levelName: member.levelName,
      discountRate: 1,
      discountText: "",
      discountAmountValue: 0,
      discountAmount: "0.00",
      discountedGoodsAmountValue: goodsAmount,
      discountedGoodsAmount: formatPrice(goodsAmount),
    };
  }

  const discountedGoodsAmountValue = roundPrice(goodsAmount * member.discountRate);
  const discountAmountValue = roundPrice(goodsAmount - discountedGoodsAmountValue);

  return {
    points: member.points,
    hasDiscount: discountAmountValue > 0,
    levelName: member.levelName,
    discountRate: member.discountRate,
    discountText: member.discountText,
    discountAmountValue,
    discountAmount: formatPrice(discountAmountValue),
    discountedGoodsAmountValue,
    discountedGoodsAmount: formatPrice(discountedGoodsAmountValue),
  };
}

module.exports = {
  MEMBER_LEVELS,
  getMemberProfile,
  getMemberLevels,
  resolveMemberDiscount,
};