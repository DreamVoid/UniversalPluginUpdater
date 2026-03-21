package me.dreamvoid.universalpluginupdater.service;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 异步操作锁
 * 用于确保同一时间只有一个异步任务正在执行
 * 类似于apt的单进程锁机制
 * <p>
 * 线程安全，不支持等待 - 如果无法获取锁，立即返回失败
 */
public class AsyncLock {
    private static final AtomicBoolean locked = new AtomicBoolean(false);

    /**
     * 尝试获取锁
     * @return true 如果成功获取锁，false 如果锁已被其他线程持有
     */
    public static boolean tryAcquire() {
        return locked.compareAndSet(false, true);
    }

    /**
     * 释放锁
     */
    public static void release() {
        locked.set(false);
    }

    /**
     * 检查当前是否有线程持有锁
     * @return true 如果锁已被持有，false 如果锁可用
     */
    public static boolean isLocked() {
        return locked.get();
    }
}
