package me.dreamvoid.universalpluginupdater.service;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 异步操作锁<br>
 * 确保同一时间只有一个异步任务正在执行<br>
 * 线程安全，无等待
 */
public class AsyncLock implements AutoCloseable {
    private static final AtomicBoolean locked = new AtomicBoolean(false);

    private AsyncLock() throws IllegalStateException {
        if(!locked.compareAndSet(false, true)){
            throw new IllegalStateException();
        }
    }

    /**
     * 尝试获取锁
     * @return 如果成功获取锁，返回 AsyncLock 对象
     * @throws IllegalStateException 锁被占用时抛出
     */
    public static AsyncLock acquire() throws IllegalStateException {
        return new AsyncLock();
    }

    /**
     * 释放锁
     */
    @Override
    public void close() {
        locked.set(false);
    }
}
