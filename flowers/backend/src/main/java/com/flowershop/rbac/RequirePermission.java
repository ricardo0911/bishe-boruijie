package com.flowershop.rbac;

import java.lang.annotation.*;

/**
 * 权限要求注解
 * 用于标记需要特定权限才能访问的方法或类
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {
    /**
     * 要求的权限列表
     * 默认需要满足所有权限（AND逻辑）
     */
    Permission[] value() default {};

    /**
     * 权限检查逻辑
     * true = 需要满足所有权限（AND）
     * false = 满足其中一个权限即可（OR）
     */
    boolean requireAll() default true;

    /**
     * 是否允许超级管理员访问（默认true）
     */
    boolean allowSuperAdmin() default true;
}
