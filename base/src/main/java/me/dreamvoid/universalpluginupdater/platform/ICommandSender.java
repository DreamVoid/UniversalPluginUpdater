package me.dreamvoid.universalpluginupdater.platform;

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
     * 检查是否有特定权限
     */
    boolean hasPermission(String permission);

    /**
     * 获取发送者的名称
     */
    String getName();

    /**
     * 检查是否是控制台发送者
     */
    boolean isConsole();
}
