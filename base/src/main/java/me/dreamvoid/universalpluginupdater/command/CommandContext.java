package me.dreamvoid.universalpluginupdater.command;

import me.dreamvoid.universalpluginupdater.platform.ICommandSender;

/**
 * 命令执行上下文
 * 由平台实现填充，最终由CommandHandler处理
 *
 * @param args   命令参数
 * @param sender 发送者
 */
public record CommandContext(
        ICommandSender sender,
        String[] args
) { }
