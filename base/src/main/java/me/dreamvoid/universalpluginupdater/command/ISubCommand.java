package me.dreamvoid.universalpluginupdater.command;

import java.util.List;

/**
 * 子命令处理器的抽象基类
 */
public interface ISubCommand {
    /**
     * 执行子命令
     * @param context 命令上下文
     */
    void execute(CommandContext context);

    /**
     * 获取命令补全列表
     * @param context 命令上下文
     * @return 补全选项数组
     */
    List<String> getTabCompletion(CommandContext context);
}
