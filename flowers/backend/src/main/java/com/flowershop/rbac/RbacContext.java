package com.flowershop.rbac;

import com.flowershop.config.AdminTokenInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;
import java.util.List;

/**
 * RBAC上下文工具类
 * 提供获取当前请求用户角色和权限的便捷方法
 */
public class RbacContext {

    /**
     * 获取当前请求的用户角色列表
     *
     * @return 角色列表，如果无法获取则返回空列表
     */
    public static List<Role> getCurrentUserRoles() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return Collections.emptyList();
        }

        HttpServletRequest request = attributes.getRequest();
        @SuppressWarnings("unchecked")
        List<Role> roles = (List<Role>) request.getAttribute(AdminTokenInterceptor.REQUEST_ATTR_ROLES);
        return roles != null ? roles : Collections.emptyList();
    }

    /**
     * 检查当前用户是否拥有指定角色
     *
     * @param role 角色
     * @return 是否拥有该角色
     */
    public static boolean hasRole(Role role) {
        List<Role> roles = getCurrentUserRoles();
        return roles.contains(role);
    }

    /**
     * 检查当前用户是否是超级管理员
     *
     * @return 是否是超级管理员
     */
    public static boolean isSuperAdmin() {
        return hasRole(Role.SUPER_ADMIN);
    }

    /**
     * 检查当前用户是否是商家
     *
     * @return 是否是商家
     */
    public static boolean isMerchant() {
        return hasRole(Role.MERCHANT);
    }

    /**
     * 检查当前用户是否是运营人员
     *
     * @return 是否是运营人员
     */
    public static boolean isOperator() {
        return hasRole(Role.OPERATOR);
    }
}
