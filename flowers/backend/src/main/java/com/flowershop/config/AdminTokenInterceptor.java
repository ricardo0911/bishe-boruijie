package com.flowershop.config;

import com.flowershop.exception.BusinessException;
import com.flowershop.rbac.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.lang.reflect.Method;
import java.util.List;

@Component
public class AdminTokenInterceptor implements HandlerInterceptor {

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private final RbacService rbacService;

    @Value("${app.auth.enabled:true}")
    private boolean authEnabled;

    @Value("${app.auth.admin-token:please-change-admin-token}")
    private String adminToken;

    // Token中角色信息的请求属性key
    public static final String REQUEST_ATTR_ROLES = "X-User-Roles";

    public AdminTokenInterceptor(RbacService rbacService) {
        this.rbacService = rbacService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!authEnabled) {
            return true;
        }

        // 首先进行Token基础校验
        if (!validateToken(request)) {
            throw new BusinessException("UNAUTHORIZED", "未授权访问，无效的Token");
        }

        // 解析Token中的角色信息
        List<Role> userRoles = extractRolesFromToken(request);
        request.setAttribute(REQUEST_ATTR_ROLES, userRoles);

        // 检查是否是HandlerMethod（Controller方法）
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;
        Method method = handlerMethod.getMethod();
        Class<?> controllerClass = method.getDeclaringClass();

        // 1. 检查类和方法上的 @RequireRole 注解
        RequireRole methodRoleAnnotation = method.getAnnotation(RequireRole.class);
        RequireRole classRoleAnnotation = controllerClass.getAnnotation(RequireRole.class);

        if (methodRoleAnnotation != null) {
            // 方法上的注解优先级最高
            if (!rbacService.hasRole(userRoles, methodRoleAnnotation.value(), methodRoleAnnotation.allowSuperAdmin())) {
                throw new BusinessException("FORBIDDEN", "无权访问，需要特定角色");
            }
        } else if (classRoleAnnotation != null) {
            // 类上的注解
            if (!rbacService.hasRole(userRoles, classRoleAnnotation.value(), classRoleAnnotation.allowSuperAdmin())) {
                throw new BusinessException("FORBIDDEN", "无权访问，需要特定角色");
            }
        }

        // 2. 检查类和方法上的 @RequirePermission 注解
        RequirePermission methodPermAnnotation = method.getAnnotation(RequirePermission.class);
        RequirePermission classPermAnnotation = controllerClass.getAnnotation(RequirePermission.class);

        if (methodPermAnnotation != null) {
            // 方法上的注解优先级最高
            if (!rbacService.hasPermission(userRoles, methodPermAnnotation.value(),
                    methodPermAnnotation.requireAll(), methodPermAnnotation.allowSuperAdmin())) {
                throw new BusinessException("FORBIDDEN", "无权访问，需要特定权限");
            }
        } else if (classPermAnnotation != null) {
            // 类上的注解
            if (!rbacService.hasPermission(userRoles, classPermAnnotation.value(),
                    classPermAnnotation.requireAll(), classPermAnnotation.allowSuperAdmin())) {
                throw new BusinessException("FORBIDDEN", "无权访问，需要特定权限");
            }
        }

        // 3. 如果没有注解，使用传统的路径匹配规则
        if (methodRoleAnnotation == null && classRoleAnnotation == null &&
            methodPermAnnotation == null && classPermAnnotation == null) {
            return checkTraditionalPathRules(request);
        }

        return true;
    }

    /**
     * 验证Token有效性
     */
    private boolean validateToken(HttpServletRequest request) {
        String token = request.getHeader("X-Admin-Token");
        return token != null && token.equals(adminToken);
    }

    /**
     * 从Token中解析角色信息
     * 支持两种方式：
     * 1. 从请求头 X-User-Roles 中解析（格式: "SUPER_ADMIN,MERCHANT"）
     * 2. 默认分配 SUPER_ADMIN 角色（向后兼容）
     */
    private List<Role> extractRolesFromToken(HttpServletRequest request) {
        // 从请求头中获取角色信息
        String rolesHeader = request.getHeader("X-User-Roles");

        if (rolesHeader != null && !rolesHeader.trim().isEmpty()) {
            return rbacService.parseRolesFromToken(rolesHeader);
        }

        // 向后兼容：如果没有角色头，默认分配 SUPER_ADMIN
        // 这样现有的单token系统可以继续工作
        return List.of(Role.SUPER_ADMIN);
    }

    /**
     * 传统的路径匹配规则检查
     * 用于没有注解的接口
     */
    private boolean checkTraditionalPathRules(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();

        // 获取用户角色
        @SuppressWarnings("unchecked")
        List<Role> userRoles = (List<Role>) request.getAttribute(REQUEST_ATTR_ROLES);

        // 调试接口 - 需要 DEBUG_ACCESS 权限
        if (pathMatcher.match("/api/v1/debug/**", path)) {
            if (!rbacService.hasPermission(userRoles, Permission.DEBUG_ACCESS)) {
                throw new BusinessException("FORBIDDEN", "无权访问调试接口");
            }
            return true;
        }

        // 商家管理接口 - 需要 MERCHANT_VIEW/MERCHANT_UPDATE 权限
        if (pathMatcher.match("/api/v1/merchants/**", path)) {
            if ("GET".equalsIgnoreCase(method)) {
                if (!rbacService.hasPermission(userRoles, Permission.MERCHANT_VIEW)) {
                    throw new BusinessException("FORBIDDEN", "无权查看商家信息");
                }
            } else {
                if (!rbacService.hasPermission(userRoles, Permission.MERCHANT_UPDATE)) {
                    throw new BusinessException("FORBIDDEN", "无权修改商家信息");
                }
            }
            return true;
        }

        // 系统配置接口 - 需要 CONFIG_VIEW/CONFIG_UPDATE 权限
        if (pathMatcher.match("/api/v1/system-config/**", path)) {
            if ("GET".equalsIgnoreCase(method)) {
                if (!rbacService.hasPermission(userRoles, Permission.CONFIG_VIEW)) {
                    throw new BusinessException("FORBIDDEN", "无权查看系统配置");
                }
            } else {
                if (!rbacService.hasPermission(userRoles, Permission.CONFIG_UPDATE)) {
                    throw new BusinessException("FORBIDDEN", "无权修改系统配置");
                }
            }
            return true;
        }

        // 库存管理接口 - 需要 INVENTORY_VIEW/INVENTORY_MANAGE 权限
        if (pathMatcher.match("/api/v1/inventory/**", path)) {
            if ("GET".equalsIgnoreCase(method)) {
                if (!rbacService.hasPermission(userRoles, Permission.INVENTORY_VIEW)) {
                    throw new BusinessException("FORBIDDEN", "无权查看库存");
                }
            } else {
                if (!rbacService.hasPermission(userRoles, Permission.INVENTORY_MANAGE)) {
                    throw new BusinessException("FORBIDDEN", "无权管理库存");
                }
            }
            return true;
        }

        // 花材管理接口 - 需要 INVENTORY_MANAGE 权限
        if (pathMatcher.match("/api/v1/flowers/**", path)) {
            if (!rbacService.hasPermission(userRoles, Permission.INVENTORY_MANAGE)) {
                throw new BusinessException("FORBIDDEN", "无权管理花材");
            }
            return true;
        }

        // 用户管理接口 - 需要 USER_VIEW/USER_UPDATE 权限
        if (path.equals("/api/v1/users")) {
            if ("GET".equalsIgnoreCase(method)) {
                if (!rbacService.hasPermission(userRoles, Permission.USER_VIEW)) {
                    throw new BusinessException("FORBIDDEN", "无权查看用户");
                }
            } else {
                if (!rbacService.hasPermission(userRoles, Permission.USER_CREATE, Permission.USER_UPDATE)) {
                    throw new BusinessException("FORBIDDEN", "无权操作用户");
                }
            }
            return true;
        }

        // 订单管理接口 - 需要 ORDER_VIEW/ORDER_UPDATE 权限
        if (path.equals("/api/v1/orders/all")) {
            if (!rbacService.hasPermission(userRoles, Permission.ORDER_VIEW)) {
                throw new BusinessException("FORBIDDEN", "无权查看所有订单");
            }
            return true;
        }

        if (path.equals("/api/v1/orders/release-expired")) {
            if (!rbacService.hasPermission(userRoles, Permission.ORDER_UPDATE)) {
                throw new BusinessException("FORBIDDEN", "无权释放过期订单");
            }
            return true;
        }

        if (pathMatcher.match("/api/v1/orders/*/confirm", path)) {
            if (!rbacService.hasPermission(userRoles, Permission.ORDER_CONFIRM)) {
                throw new BusinessException("FORBIDDEN", "无权确认订单");
            }
            return true;
        }

        if (pathMatcher.match("/api/v1/orders/*/complete", path)) {
            if (!rbacService.hasPermission(userRoles, Permission.ORDER_COMPLETE)) {
                throw new BusinessException("FORBIDDEN", "无权完成订单");
            }
            return true;
        }

        // 商品管理接口 - 需要 PRODUCT_CREATE/PRODUCT_UPDATE 权限
        if ("POST".equalsIgnoreCase(method) && path.equals("/api/v1/products")) {
            if (!rbacService.hasPermission(userRoles, Permission.PRODUCT_CREATE)) {
                throw new BusinessException("FORBIDDEN", "无权创建商品");
            }
            return true;
        }

        if ("PUT".equalsIgnoreCase(method) && pathMatcher.match("/api/v1/products/*", path)) {
            if (!rbacService.hasPermission(userRoles, Permission.PRODUCT_UPDATE)) {
                throw new BusinessException("FORBIDDEN", "无权更新商品");
            }
            return true;
        }

        return true;
    }

    /**
     * 检查用户是否拥有任一指定权限
     */
    private boolean hasAnyPermission(List<Role> userRoles, Permission... permissions) {
        for (Permission permission : permissions) {
            if (rbacService.hasPermission(userRoles, permission)) {
                return true;
            }
        }
        return false;
    }
}
