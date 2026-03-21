package me.dreamvoid.universalpluginupdater.command;

import me.dreamvoid.universalpluginupdater.platform.ICommandSender;

/**
 * 命令执行上下文
 * 由平台实现填充，最终由CommandHandler处理
 */
public class CommandContext {
    private final String[] args;        // 命令参数
    private final ICommandSender sender; // 发送者

    public CommandContext(ICommandSender sender, String[] args) {
        this.sender = sender;
        this.args = args;
    }

    public String[] getArgs() {
        return args;
    }

    public ICommandSender getSender() {
        return sender;
    }

    /**
     * 获取第一个参数（子命令）
     */
    public String getSubCommand() {
        return args.length > 0 ? args[0] : null;
    }

    /**
     * 获取子命令后的其他参数
     */
    public String[] getSubCommandArgs() {
        if (args.length > 1) {
            String[] result = new String[args.length - 1];
            System.arraycopy(args, 1, result, 0, args.length - 1);
            return result;
        }
        return new String[0];
    }
}
