package me.dreamvoid.universalpluginupdater.platform;

import java.time.Duration;

public interface Scheduler {
    /**
     * 异步运行任务
     * @param runnable 要执行的任务
     */
    void runTaskAsync(Runnable runnable);

    /**
     * 延迟指定时间后，异步运行任务
     * @param runnable 要执行的任务
     * @param delay 延迟时间，单位为秒
     */
    void runTaskLaterAsync(Runnable runnable, long delay);

    /**
     * 延迟指定时间后，异步运行任务
     * @param runnable 要执行的任务
     * @param delay 延迟时间
     */
    void runTaskLaterAsync(Runnable runnable, Duration delay);

    /** 异步重复运行任务
     * @param runnable 要执行的任务
     * @param repeat 循环间隔，单位为秒
     */
    void runTaskTimerAsync(Runnable runnable, long repeat);

    /**
     * 异步重复运行任务
     * @param runnable 要执行的任务
     * @param repeat 循环间隔
     */
    void runTaskTimerAsync(Runnable runnable, Duration repeat);
}
