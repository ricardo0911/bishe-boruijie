package com.flowershop.rbac;

/**
 * 角色枚举定义
 * 定义系统中的所有角色类型
 */
public enum Role {
    /**
     * 超级管理员 - 拥有所有权限
     */
    SUPER_ADMIN("SUPER_ADMIN", "超级管理员"),

    /**
     * 商家 - 管理自己的订单和库存
     */
    MERCHANT("MERCHANT", "商家"),

    /**
     * 运营人员 - 查看报表和统计数据
     */
    OPERATOR("OPERATOR", "运营人员");

    private final String code;
    private final String description;

    Role(String code, String description) {
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
     * 根据code获取角色
     */
    public static Role fromCode(String code) {
        for (Role role : values()) {
            if (role.code.equalsIgnoreCase(code)) {
                return role;
            }
        }
        return null;
    }
}
