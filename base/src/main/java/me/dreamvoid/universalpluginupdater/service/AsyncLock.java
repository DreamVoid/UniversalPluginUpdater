package me.dreamvoid.universalpluginupdater.service;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 异步操作锁<br>
 * 确保同一时间只有一个异步任务正在执行<br>
 * 线程安全，无等待
 */
public class AsyncLock {
    private static final AtomicBoolean locked = new AtomicBoolean(false);

    /**
     * 尝试获取锁
     * @return true 如果成功获取锁，false 如果锁已被其他线程持有
     */
    public static boolean acquire() {
        return locked.compareAndSet(false, true);
    }

    /**
     * 释放锁
     */
    public static void release() {
        locked.set(false);
    }
}
