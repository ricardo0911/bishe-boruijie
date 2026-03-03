package com.flowershop.rbac;

/**
 * 权限枚举定义
 * 定义系统中的所有权限类型
 */
public enum Permission {
    // ==================== 用户管理权限 ====================
    USER_VIEW("user:view", "查看用户"),
    USER_CREATE("user:create", "创建用户"),
    USER_UPDATE("user:update", "更新用户"),
    USER_DELETE("user:delete", "删除用户"),

    // ==================== 商品管理权限 ====================
    PRODUCT_VIEW("product:view", "查看商品"),
    PRODUCT_CREATE("product:create", "创建商品"),
    PRODUCT_UPDATE("product:update", "更新商品"),
    PRODUCT_DELETE("product:delete", "删除商品"),

    // ==================== 订单管理权限 ====================
    ORDER_VIEW("order:view", "查看订单"),
    ORDER_CREATE("order:create", "创建订单"),
    ORDER_UPDATE("order:update", "更新订单"),
    ORDER_DELETE("order:delete", "删除订单"),
    ORDER_CONFIRM("order:confirm", "确认订单"),
    ORDER_COMPLETE("order:complete", "完成订单"),
    ORDER_CANCEL("order:cancel", "取消订单"),

    // ==================== 库存管理权限 ====================
    INVENTORY_VIEW("inventory:view", "查看库存"),
    INVENTORY_UPDATE("inventory:update", "更新库存"),
    INVENTORY_MANAGE("inventory:manage", "管理库存"),

    // ==================== 商家管理权限 ====================
    MERCHANT_VIEW("merchant:view", "查看商家"),
    MERCHANT_CREATE("merchant:create", "创建商家"),
    MERCHANT_UPDATE("merchant:update", "更新商家"),
    MERCHANT_DELETE("merchant:delete", "删除商家"),

    // ==================== 报表统计权限 ====================
    STATS_VIEW("stats:view", "查看统计"),
    REPORT_VIEW("report:view", "查看报表"),
    ANALYSIS_VIEW("analysis:view", "查看分析"),

    // ==================== 系统配置权限 ====================
    CONFIG_VIEW("config:view", "查看配置"),
    CONFIG_UPDATE("config:update", "更新配置"),

    // ==================== 调试权限 ====================
    DEBUG_ACCESS("debug:access", "调试访问"),

    // ==================== 超级权限 ====================
    ALL("*", "所有权限");

    private final String code;
    private final String description;

    Permission(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据code获取权限
     */
    public static Permission fromCode(String code) {
        for (Permission permission : values()) {
            if (permission.code.equalsIgnoreCase(code)) {
                return permission;
            }
        }
        return null;
    }
}
