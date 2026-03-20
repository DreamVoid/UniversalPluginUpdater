package me.dreamvoid.universalpluginupdater.platform;

import me.dreamvoid.universalpluginupdater.command.CommandContext;

/**
 * 命令处理程序接口
 * 各个平台都通过这个接口处理命令
 */
public interface ICommandHandler {
    /**
     * 处理命令
     * @param context 命令上下文
     * @return 是否成功处理
     */
    boolean handleCommand(CommandContext context);

    /**
     * 获取命令补全列表
     * @param context 命令上下文
     * @return 可用的补全选项列表
     */
    String[] getTabCompletion(CommandContext context);
}
