package com.dafeiyu.shortlink.common;

/**
 * 当前登录用户。
 *
 * ThreadLocal 的用法就是「一个请求一个线程，线程里挂一个变量」：
 *   拦截器里 set → 业务代码里 get → 请求结束 afterCompletion 里 remove。
 *
 * 为什么必须 remove：Tomcat 的线程是复用的（线程池），
 * 不清理的话下一个请求可能拿到上一个用户的数据 —— 这就是「用户串号」事故。
 */
public final class UserContext {

    private static final ThreadLocal<Long> HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> ADMIN = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(Long userId) {
        set(userId, false);
    }

    public static void set(Long userId, boolean admin) {
        HOLDER.set(userId);
        ADMIN.set(admin);
    }

    /** 只有显式设为 true 才是管理员；没登录 / 没设置一律 false（最小权限） */
    public static boolean isAdmin() {
        return Boolean.TRUE.equals(ADMIN.get());
    }

    /** 没登录时返回 null（理论上拦截器已经拦掉了，这里兜底） */
    public static Long get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
        ADMIN.remove();
    }
}
