package com.flowershop.rbac;

import com.flowershop.config.AdminTokenInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;
import java.util.List;

public class RbacContext {

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

    public static String getCurrentAccount() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        Object account = attributes.getRequest().getAttribute(AdminTokenInterceptor.REQUEST_ATTR_ACCOUNT);
        return account == null ? null : String.valueOf(account);
    }

    public static String getCurrentLoginType() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        Object loginType = attributes.getRequest().getAttribute(AdminTokenInterceptor.REQUEST_ATTR_LOGIN_TYPE);
        return loginType == null ? null : String.valueOf(loginType);
    }

    public static boolean hasRole(Role role) {
        List<Role> roles = getCurrentUserRoles();
        return roles.contains(role);
    }

    public static boolean isSuperAdmin() {
        return hasRole(Role.SUPER_ADMIN);
    }

    public static boolean isMerchant() {
        return hasRole(Role.MERCHANT);
    }

    public static boolean isOperator() {
        return hasRole(Role.OPERATOR);
    }
}
