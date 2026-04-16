package me.dreamvoid.universalpluginupdater.service;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 异步操作锁<br>
 * 确保同一时间只有一个任务正在执行
 */
public final class AsyncLock implements AutoCloseable {
    private static final AtomicBoolean locked = new AtomicBoolean(false);

    private AsyncLock() throws IllegalStateException {
        if(!locked.compareAndSet(false, true)){
            throw new IllegalStateException();
        }
    }

    /**
     * 尝试获取异步操作锁<br>
     * <br>
     * 使用此方法获取操作锁时，建议使用 <code>try-with-resources</code>，在操作结束时可以自动释放锁，以下是使用例：<br>
     * <pre>
     *     try (AsyncLock ignored = AsyncLock.acquire()){
     *         // do some logic...
     *     }
     * </pre>
     *
     * 如果不使用 <code>try-with-resources</code>，则必须在需要锁的操作完成后调用 {@link #close()} 方法手动释放锁，否则不再能够获取锁。
     * @return {@link AsyncLock} 对象
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
