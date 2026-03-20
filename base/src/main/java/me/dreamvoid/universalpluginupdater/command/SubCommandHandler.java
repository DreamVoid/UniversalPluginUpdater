package me.dreamvoid.universalpluginupdater.command;

/**
 * 子命令处理器的抽象基类
 */
public abstract class SubCommandHandler {
    /**
     * 执行子命令
     * @param context 命令上下文
     * @return 是否成功执行
     */
    public abstract boolean execute(CommandContext context);

    /**
     * 获取命令补全列表
     * @param context 命令上下文
     * @return 补全选项数组
     */
    public String[] getTabCompletion(CommandContext context) {
        return new String[0];
    }
}
