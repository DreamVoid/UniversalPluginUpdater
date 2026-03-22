package me.dreamvoid.universalpluginupdater.platform;

import org.jetbrains.annotations.Nullable;

/**
 * 抽象的命令发送者接口
 * 用于屏蔽不同平台的发送者差异
 */
public interface ICommandSender {
    /**
     * 发送消息到命令发送者
     */
    void sendMessage(String message);

    /**
     * 发送消息到命令发送者，同时广播消息给其他具有 op 权限的玩家
     */
    void broadcastMessage(String message);

    /**
     * 检查是否有特定权限
     */
    boolean hasPermission(String permission);

    /**
     * 获取发送者的名称
     */
    String getName();

    /**
     * 获取底层平台对象
     * 平台实现可以通过该对象判断发送者是否为玩家，并获取其客户端语言
     * @return 底层对象，如果不存在返回null
     */
    @Nullable
    Object getHandle();
}
