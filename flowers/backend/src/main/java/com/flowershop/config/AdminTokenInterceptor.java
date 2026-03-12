package com.flowershop.config;

import com.flowershop.exception.BusinessException;
import com.flowershop.rbac.Permission;
import com.flowershop.rbac.RbacService;
import com.flowershop.rbac.RequirePermission;
import com.flowershop.rbac.RequireRole;
import com.flowershop.rbac.Role;
import com.flowershop.service.AuthSession;
import com.flowershop.service.AuthSessionGateway;
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

    public static final String REQUEST_ATTR_ROLES = "X-User-Roles";
    public static final String REQUEST_ATTR_ACCOUNT = "X-User-Account";
    public static final String REQUEST_ATTR_LOGIN_TYPE = "X-Login-Type";

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final RbacService rbacService;
    private final AuthSessionGateway authSessionGateway;

    @Value("${app.auth.enabled:true}")
    private boolean authEnabled;

    public AdminTokenInterceptor(RbacService rbacService, AuthSessionGateway authSessionGateway) {
        this.rbacService = rbacService;
        this.authSessionGateway = authSessionGateway;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!authEnabled) {
            return true;
        }

        if (isPublicApiRequest(request)) {
            attachSessionIfPresent(request);
            return true;
        }

        AuthSession authSession = requireSession(request);
        List<Role> userRoles = attachRequestContext(request, authSession);

        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        Method method = handlerMethod.getMethod();
        Class<?> controllerClass = method.getDeclaringClass();

        RequireRole methodRoleAnnotation = method.getAnnotation(RequireRole.class);
        RequireRole classRoleAnnotation = controllerClass.getAnnotation(RequireRole.class);

        if (methodRoleAnnotation != null) {
            if (!rbacService.hasRole(userRoles, methodRoleAnnotation.value(), methodRoleAnnotation.allowSuperAdmin())) {
                throw new BusinessException("FORBIDDEN", "权限不足，需要指定角色");
            }
        } else if (classRoleAnnotation != null) {
            if (!rbacService.hasRole(userRoles, classRoleAnnotation.value(), classRoleAnnotation.allowSuperAdmin())) {
                throw new BusinessException("FORBIDDEN", "权限不足，需要指定角色");
            }
        }

        RequirePermission methodPermAnnotation = method.getAnnotation(RequirePermission.class);
        RequirePermission classPermAnnotation = controllerClass.getAnnotation(RequirePermission.class);

        if (methodPermAnnotation != null) {
            if (!rbacService.hasPermission(
                    userRoles,
                    methodPermAnnotation.value(),
                    methodPermAnnotation.requireAll(),
                    methodPermAnnotation.allowSuperAdmin())) {
                throw new BusinessException("FORBIDDEN", "权限不足，需要指定权限");
            }
        } else if (classPermAnnotation != null) {
            if (!rbacService.hasPermission(
                    userRoles,
                    classPermAnnotation.value(),
                    classPermAnnotation.requireAll(),
                    classPermAnnotation.allowSuperAdmin())) {
                throw new BusinessException("FORBIDDEN", "权限不足，需要指定权限");
            }
        }

        if (methodRoleAnnotation == null && classRoleAnnotation == null
                && methodPermAnnotation == null && classPermAnnotation == null) {
            return checkTraditionalPathRules(request);
        }

        return true;
    }

    private AuthSession requireSession(HttpServletRequest request) {
        String token = request.getHeader("X-Admin-Token");
        if (token == null || token.trim().isEmpty()) {
            throw new BusinessException("UNAUTHORIZED", "未授权访问，无效的 Token");
        }
        return authSessionGateway.findByToken(token.trim())
                .orElseThrow(() -> new BusinessException("UNAUTHORIZED", "未授权访问，无效的 Token"));
    }

    private List<Role> extractRolesFromSession(AuthSession authSession) {
        Role role = Role.fromCode(authSession.roleCode());
        if (role == null) {
            throw new BusinessException("UNAUTHORIZED", "未授权访问，角色无效");
        }
        return List.of(role);
    }

    private void attachSessionIfPresent(HttpServletRequest request) {
        String token = request.getHeader("X-Admin-Token");
        if (token == null || token.trim().isEmpty()) {
            return;
        }

        AuthSession authSession = authSessionGateway.findByToken(token.trim())
            .orElseThrow(() -> new BusinessException("UNAUTHORIZED", "X-Admin-Token 无效"));
        attachRequestContext(request, authSession);
    }

    private List<Role> attachRequestContext(HttpServletRequest request, AuthSession authSession) {
        List<Role> userRoles = extractRolesFromSession(authSession);
        request.setAttribute(REQUEST_ATTR_ROLES, userRoles);
        request.setAttribute(REQUEST_ATTR_ACCOUNT, authSession.account());
        request.setAttribute(REQUEST_ATTR_LOGIN_TYPE, authSession.loginType());
        return userRoles;
    }

    private boolean isPublicApiRequest(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if ("POST".equalsIgnoreCase(method) && "/api/v1/after-sales".equals(path)) {
            return true;
        }
        if ("POST".equalsIgnoreCase(method) && "/api/v1/support-tickets".equals(path)) {
            return true;
        }
        if ("POST".equalsIgnoreCase(method) && "/api/v1/reviews".equals(path)) {
            return true;
        }
        if (!"GET".equalsIgnoreCase(method)) {
            return false;
        }
        if ("/api/v1/products".equals(path) || pathMatcher.match("/api/v1/products/**", path)) {
            return true;
        }
        if (pathMatcher.match("/api/v1/reviews/product/*", path)) {
            return true;
        }
        if (pathMatcher.match("/api/v1/reviews/user/*", path)) {
            return true;
        }
        if (pathMatcher.match("/api/v1/support-tickets/user/*", path)) {
            return true;
        }
        if (!pathMatcher.match("/api/v1/after-sales/*", path)) {
            return false;
        }
        return !"/api/v1/after-sales/list".equals(path);
    }

    private boolean checkTraditionalPathRules(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();

        @SuppressWarnings("unchecked")
        List<Role> userRoles = (List<Role>) request.getAttribute(REQUEST_ATTR_ROLES);

        if (pathMatcher.match("/api/v1/debug/**", path)) {
            if (!rbacService.hasPermission(userRoles, Permission.DEBUG_ACCESS)) {
                throw new BusinessException("FORBIDDEN", "无权访问调试接口");
            }
            return true;
        }

        if (pathMatcher.match("/api/v1/merchants/**", path)) {
            if ("GET".equalsIgnoreCase(method)) {
                if (!rbacService.hasPermission(userRoles, Permission.MERCHANT_VIEW)) {
                    throw new BusinessException("FORBIDDEN", "无权查看商家信息");
                }
            } else if (!rbacService.hasPermission(userRoles, Permission.MERCHANT_UPDATE)) {
                throw new BusinessException("FORBIDDEN", "无权修改商家信息");
            }
            return true;
        }

        if (pathMatcher.match("/api/v1/system-config/**", path)) {
            if ("GET".equalsIgnoreCase(method)) {
                if (!rbacService.hasPermission(userRoles, Permission.CONFIG_VIEW)) {
                    throw new BusinessException("FORBIDDEN", "无权查看系统配置");
                }
            } else if (!rbacService.hasPermission(userRoles, Permission.CONFIG_UPDATE)) {
                throw new BusinessException("FORBIDDEN", "无权修改系统配置");
            }
            return true;
        }

        if (pathMatcher.match("/api/v1/inventory/**", path)) {
            if ("GET".equalsIgnoreCase(method)) {
                if (!rbacService.hasPermission(userRoles, Permission.INVENTORY_VIEW)) {
                    throw new BusinessException("FORBIDDEN", "无权查看库存");
                }
            } else if (!rbacService.hasPermission(userRoles, Permission.INVENTORY_MANAGE)) {
                throw new BusinessException("FORBIDDEN", "无权管理库存");
            }
            return true;
        }

        if (pathMatcher.match("/api/v1/flowers/**", path)) {
            if (!rbacService.hasPermission(userRoles, Permission.INVENTORY_MANAGE)) {
                throw new BusinessException("FORBIDDEN", "无权管理花材");
            }
            return true;
        }

        if (path.equals("/api/v1/users")) {
            if ("GET".equalsIgnoreCase(method)) {
                if (!rbacService.hasPermission(userRoles, Permission.USER_VIEW)) {
                    throw new BusinessException("FORBIDDEN", "无权查看用户");
                }
            } else if (!rbacService.hasPermission(userRoles, Permission.USER_CREATE, Permission.USER_UPDATE)) {
                throw new BusinessException("FORBIDDEN", "无权操作用户");
            }
            return true;
        }

        if (path.equals("/api/v1/orders/all")) {
            if (!rbacService.hasPermission(userRoles, Permission.ORDER_VIEW)) {
                throw new BusinessException("FORBIDDEN", "无权查看所有订单");
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
}

