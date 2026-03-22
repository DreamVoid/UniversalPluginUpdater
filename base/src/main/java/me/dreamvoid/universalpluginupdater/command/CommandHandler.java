package me.dreamvoid.universalpluginupdater.command;

import me.dreamvoid.universalpluginupdater.command.sub.DownloadCommand;
import me.dreamvoid.universalpluginupdater.command.sub.UpdateCommand;
import me.dreamvoid.universalpluginupdater.command.sub.UpgradeCommand;
import me.dreamvoid.universalpluginupdater.service.LanguageService;
import me.dreamvoid.universalpluginupdater.platform.ICommandHandler;
import me.dreamvoid.universalpluginupdater.platform.ICommandSender;
import me.dreamvoid.universalpluginupdater.platform.IPlatformProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * 通用的命令处理器
 * 由各个平台的实现调用
 */
public class CommandHandler implements ICommandHandler {
    private final Map<String, ISubCommand> subCommands = new HashMap<>();
    private final IPlatformProvider platform;

    public CommandHandler(IPlatformProvider platform) {
        this.platform = platform;
        registerSubCommands();
    }

    /**
     * 注册所有子命令
     */
    private void registerSubCommands() {
        subCommands.put("update", new UpdateCommand(platform));
        subCommands.put("download", new DownloadCommand(platform));
        subCommands.put("upgrade", new UpgradeCommand(platform));
    }

    @Override
    public void executeCommand(CommandContext context) {
        platform.runTaskAsync(() -> {
            String subCommand = context.getSubCommand();

            // 如果没有子命令，显示帮助
            if (subCommand == null) {
                showHelp(context.getSender());
                return;
            }

            // 查找对应的子命令处理器
            ISubCommand handler = subCommands.get(subCommand.toLowerCase());
            if (handler == null) {
                context.getSender().sendMessage("&c未知或不完整的命令: " + subCommand);
                return;
            }

            // 检查权限
            if (!context.getSender().hasPermission("universalpluginupdater.command." + subCommand)) {
                context.getSender().sendMessage("&c你没有足够的权限使用此命令！");
                return;
            }

            // 执行子命令
            handler.execute(context);
        });
    }

    @Override
    public List<String> getTabCompletion(CommandContext context) {
        String[] args = context.getArgs();
        List<String> result = new ArrayList<>();

        // 第一个参数是子命令补全
        if (args.length == 0) {
            return subCommands.keySet().stream().toList();
        }

        if (args.length == 1) {
            for(String s : subCommands.keySet()) {
                if(s.startsWith(args[0])) result.add(s);
            }
            return result;
        }

        // 子命令的参数补全
        String subCommand = args[0].toLowerCase();
        ISubCommand handler = subCommands.get(subCommand);
        if (handler != null) {
            return handler.getTabCompletion(context);
        }

        return result;
    }

    /**
     * 显示帮助信息
     */
    private void showHelp(ICommandSender sender) {
        Locale locale = platform.getLocale(sender);
        sender.sendMessage(LanguageService.instance().tr(locale,
            "message.command.help",
            platform.getPluginVersion(),
            platform.getPlatformName()));
    }
}
