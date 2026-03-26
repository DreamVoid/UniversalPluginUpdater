package me.dreamvoid.universalpluginupdater.platform;

import java.util.Locale;

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
     * 获取命令发送者的Locale
     * @return {@link Locale} 对象
     */
    Locale getLocale();
}
