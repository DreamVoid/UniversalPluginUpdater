package me.dreamvoid.universalpluginupdater.command;

import me.dreamvoid.universalpluginupdater.platform.ICommandHandler;
import me.dreamvoid.universalpluginupdater.platform.ICommandSender;

import java.util.HashMap;
import java.util.Map;

/**
 * 通用的命令处理器
 * 由各个平台的实现调用
 */
public class CommandHandler implements ICommandHandler {
    private final Map<String, SubCommandHandler> subCommands = new HashMap<>();

    public CommandHandler() {
        registerSubCommands();
    }

    /**
     * 注册所有子命令
     */
    private void registerSubCommands() {
        subCommands.put("update", new UpdateCommandHandler());
        subCommands.put("download", new DownloadCommandHandler());
        subCommands.put("upgrade", new UpgradeCommandHandler());
    }

    @Override
    public boolean handleCommand(CommandContext context) {
        String subCommand = context.getSubCommand();

        // 如果没有子命令，显示帮助
        if (subCommand == null) {
            showHelp(context.getSender());
            return true;
        }

        // 查找对应的子命令处理器
        SubCommandHandler handler = subCommands.get(subCommand.toLowerCase());
        if (handler == null) {
            context.getSender().sendMessage("&c未知或不完整的命令: " + subCommand);
            showHelp(context.getSender());
            return true;
        }

        // 检查权限
        if (!context.getSender().hasPermission("universalpluginupdater." + subCommand)) {
            context.getSender().sendMessage("&c你没有权限使用此命令！");
            return true;
        }

        // 执行子命令
        return handler.execute(context);
    }

    @Override
    public String[] getTabCompletion(CommandContext context) {
        String[] args = context.getArgs();

        // 第一个参数是子命令补全
        if (args.length == 0 || args.length == 1) {
            return subCommands.keySet().toArray(new String[0]);
        }

        // 子命令的参数补全
        String subCommand = args[0].toLowerCase();
        SubCommandHandler handler = subCommands.get(subCommand);
        if (handler != null) {
            return handler.getTabCompletion(context);
        }

        return new String[0];
    }

    /**
     * 显示帮助信息
     */
    private void showHelp(ICommandSender sender) {
        sender.sendMessage("&b========== UniversalPluginUpdater &b==========");
        sender.sendMessage("&e/upu update &7- &f检查所有插件的更新");
        sender.sendMessage("&e/upu download &7- &f仅下载插件更新");
        sender.sendMessage("&e/upu upgrade &7- &f下载并更新插件");
        sender.sendMessage("&b==========================================");
    }
}
