package com.flowershop.rbac;

import org.springframework.stereotype.Service;

import java.util.*;

/**
 * RBAC权限服务
 * 提供角色和权限的校验逻辑
 */
@Service
public class RbacService {

    /**
     * 角色权限映射表
     * 定义每个角色拥有的权限
     */
    private static final Map<Role, Set<Permission>> ROLE_PERMISSIONS = new EnumMap<>(Role.class);

    static {
        // 超级管理员 - 拥有所有权限
        Set<Permission> superAdminPermissions = new HashSet<>(Arrays.asList(Permission.values()));
        ROLE_PERMISSIONS.put(Role.SUPER_ADMIN, superAdminPermissions);

        // 商家 - 管理自己的订单和库存
        Set<Permission> merchantPermissions = new HashSet<>(Arrays.asList(
                // 商品管理
                Permission.PRODUCT_VIEW, Permission.PRODUCT_CREATE, Permission.PRODUCT_UPDATE,
                // 订单管理
                Permission.ORDER_VIEW, Permission.ORDER_CREATE, Permission.ORDER_UPDATE,
                Permission.ORDER_CONFIRM, Permission.ORDER_COMPLETE, Permission.ORDER_CANCEL,
                // 库存管理
                Permission.INVENTORY_VIEW, Permission.INVENTORY_UPDATE, Permission.INVENTORY_MANAGE,
                // 商家信息
                Permission.MERCHANT_VIEW, Permission.MERCHANT_UPDATE
        ));
        ROLE_PERMISSIONS.put(Role.MERCHANT, merchantPermissions);

        // 运营人员 - 查看报表和统计数据
        Set<Permission> operatorPermissions = new HashSet<>(Arrays.asList(
                // 用户查看
                Permission.USER_VIEW,
                // 商品查看
                Permission.PRODUCT_VIEW,
                // 订单查看
                Permission.ORDER_VIEW,
                // 库存查看
                Permission.INVENTORY_VIEW,
                // 商家查看
                Permission.MERCHANT_VIEW,
                // 统计报表
                Permission.STATS_VIEW, Permission.REPORT_VIEW, Permission.ANALYSIS_VIEW,
                // 配置查看
                Permission.CONFIG_VIEW
        ));
        ROLE_PERMISSIONS.put(Role.OPERATOR, operatorPermissions);
    }

    /**
     * 检查用户是否拥有指定角色
     *
     * @param userRoles 用户拥有的角色列表
     * @param requiredRoles 要求的角色列表
     * @param allowSuperAdmin 是否允许超级管理员通过
     * @return 是否拥有权限
     */
    public boolean hasRole(List<Role> userRoles, Role[] requiredRoles, boolean allowSuperAdmin) {
        if (userRoles == null || userRoles.isEmpty()) {
            return false;
        }

        // 如果允许超级管理员且用户是超级管理员，直接通过
        if (allowSuperAdmin && userRoles.contains(Role.SUPER_ADMIN)) {
            return true;
        }

        // 如果没有指定要求角色，则通过
        if (requiredRoles == null || requiredRoles.length == 0) {
            return true;
        }

        // 检查用户是否拥有任一要求的角色
        for (Role requiredRole : requiredRoles) {
            if (userRoles.contains(requiredRole)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检查用户是否拥有指定权限
     *
     * @param userRoles 用户拥有的角色列表
     * @param requiredPermissions 要求的权限列表
     * @param requireAll 是否需要满足所有权限（AND逻辑）
     * @param allowSuperAdmin 是否允许超级管理员通过
     * @return 是否拥有权限
     */
    public boolean hasPermission(List<Role> userRoles, Permission[] requiredPermissions,
                                  boolean requireAll, boolean allowSuperAdmin) {
        if (userRoles == null || userRoles.isEmpty()) {
            return false;
        }

        // 如果允许超级管理员且用户是超级管理员，直接通过
        if (allowSuperAdmin && userRoles.contains(Role.SUPER_ADMIN)) {
            return true;
        }

        // 如果没有指定要求权限，则通过
        if (requiredPermissions == null || requiredPermissions.length == 0) {
            return true;
        }

        // 获取用户的所有权限
        Set<Permission> userPermissions = getUserPermissions(userRoles);

        if (requireAll) {
            // AND逻辑：需要满足所有权限
            for (Permission requiredPermission : requiredPermissions) {
                if (!userPermissions.contains(requiredPermission)) {
                    return false;
                }
            }
            return true;
        } else {
            // OR逻辑：满足其中一个权限即可
            for (Permission requiredPermission : requiredPermissions) {
                if (userPermissions.contains(requiredPermission)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * 获取用户的所有权限
     *
     * @param userRoles 用户拥有的角色列表
     * @return 用户拥有的所有权限
     */
    public Set<Permission> getUserPermissions(List<Role> userRoles) {
        Set<Permission> permissions = new HashSet<>();
        if (userRoles == null) {
            return permissions;
        }

        for (Role role : userRoles) {
            Set<Permission> rolePerms = ROLE_PERMISSIONS.get(role);
            if (rolePerms != null) {
                permissions.addAll(rolePerms);
            }
        }

        return permissions;
    }

    /**
     * 获取角色的所有权限
     *
     * @param role 角色
     * @return 角色拥有的所有权限
     */
    public Set<Permission> getRolePermissions(Role role) {
        return ROLE_PERMISSIONS.getOrDefault(role, new HashSet<>());
    }

    /**
     * 检查用户是否拥有指定权限
     *
     * @param userRoles 用户拥有的角色列表
     * @param permission 权限
     * @return 是否拥有权限
     */
    public boolean hasPermission(List<Role> userRoles, Permission permission) {
        if (userRoles == null || userRoles.isEmpty() || permission == null) {
            return false;
        }

        // 超级管理员拥有所有权限
        if (userRoles.contains(Role.SUPER_ADMIN)) {
            return true;
        }

        Set<Permission> userPermissions = getUserPermissions(userRoles);
        return userPermissions.contains(permission);
    }

    /**
     * 检查用户是否拥有任一指定权限（可变参数版本）
     *
     * @param userRoles 用户拥有的角色列表
     * @param permissions 权限列表（可变参数）
     * @return 是否拥有任一权限
     */
    public boolean hasPermission(List<Role> userRoles, Permission... permissions) {
        if (userRoles == null || userRoles.isEmpty() || permissions == null || permissions.length == 0) {
            return false;
        }

        // 超级管理员拥有所有权限
        if (userRoles.contains(Role.SUPER_ADMIN)) {
            return true;
        }

        Set<Permission> userPermissions = getUserPermissions(userRoles);
        for (Permission permission : permissions) {
            if (userPermissions.contains(permission)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从token字符串解析角色列表
     * 格式: "ROLE1,ROLE2,ROLE3"
     *
     * @param tokenRoleStr token中的角色字符串
     * @return 角色列表
     */
    public List<Role> parseRolesFromToken(String tokenRoleStr) {
        List<Role> roles = new ArrayList<>();
        if (tokenRoleStr == null || tokenRoleStr.trim().isEmpty()) {
            return roles;
        }

        String[] roleCodes = tokenRoleStr.split(",");
        for (String roleCode : roleCodes) {
            Role role = Role.fromCode(roleCode.trim());
            if (role != null) {
                roles.add(role);
            }
        }

        return roles;
    }
}
