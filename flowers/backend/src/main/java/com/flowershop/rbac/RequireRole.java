package com.flowershop.rbac;

import java.lang.annotation.*;

/**
 * 角色要求注解
 * 用于标记需要特定角色才能访问的方法或类
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {
    /**
     * 要求的角色列表
     * 满足其中一个角色即可访问
     */
    Role[] value() default {};

    /**
     * 是否允许超级管理员访问（默认true）
     */
    boolean allowSuperAdmin() default true;
}
