package me.dreamvoid.universalpluginupdater.upgrade;

/**
 * 升级操作的执行阶段
 */
public enum ExecutionPhase {
    /**
     * 运行期阶段<br>
     * 策略在服务器运行期间即可执行升级操作
     */
    RUNTIME,

    /**
     * 关闭阶段<br>
     * 策略只能在插件禁用阶段执行升级操作
     */
    SHUTDOWN
}
