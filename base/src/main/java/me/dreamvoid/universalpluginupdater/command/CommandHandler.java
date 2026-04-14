package me.dreamvoid.universalpluginupdater.command;

import me.dreamvoid.universalpluginupdater.command.action.DownloadCommand;
import me.dreamvoid.universalpluginupdater.command.action.RepoCommand;
import me.dreamvoid.universalpluginupdater.command.action.UpdateCommand;
import me.dreamvoid.universalpluginupdater.command.action.UpgradeCommand;
import me.dreamvoid.universalpluginupdater.platform.CommandSender;
import me.dreamvoid.universalpluginupdater.platform.Platform;

import java.util.*;

import static me.dreamvoid.universalpluginupdater.service.LanguageService.tr;

/**
 * 通用的命令处理器
 * 由各个平台的实现调用
 */
public class CommandHandler {
    protected final Platform platform;
    private final Map<String, CommandHandler> actions = new HashMap<>();

    public CommandHandler(Platform platform) {
        this.platform = platform;

        if (this.getClass() == CommandHandler.class) {
            actions.put("update", new UpdateCommand(platform));
            actions.put("download", new DownloadCommand(platform));
            actions.put("upgrade", new UpgradeCommand(platform));
            actions.put("repo", new RepoCommand(platform));
        }
    }

    /**
     * 处理命令
     * @param context 命令上下文
     */
    public void execute(CommandContext context) {
        platform.runTaskAsync(() -> {
            CommandSender sender = context.sender();
            Locale locale = sender.getLocale();
            String action = context.args().length > 0 ? context.args()[0] : null;

            // 如果没有子命令，显示帮助
            if (action == null) {
                sender.sendMessage(tr(locale, "message.command.help", platform.getPluginVersion(), platform.getPlatformName()));
                return;
            }

            // 查找对应的子命令处理器
            CommandHandler handler = actions.get(action.toLowerCase());
            if (handler == null) {
                sender.sendMessage(tr(locale, "message.command.error.unknown", action));
                return;
            }

            // 检查权限
            if (!sender.hasPermission("universalpluginupdater.command." + action)) {
                sender.sendMessage(tr(locale, "message.command.error.no-permission"));
                return;
            }

            // 执行子命令
            handler.execute(new CommandContext(context.sender(), Arrays.copyOfRange(context.args(), 1, context.args().length)));
        });
    }

    /**
     * 获取命令补全列表
     * @param context 命令上下文
     * @return 可用的补全选项列表
     */
    public List<String> suggest(CommandContext context) {
        String[] args = context.args();
        List<String> result = new ArrayList<>();

        // 第一个参数是子命令补全
        if (args.length == 0) {
            result = actions.keySet().stream().toList();
        } else if (args.length == 1) {
            for(String s : actions.keySet()) {
                if(s.startsWith(args[0].toLowerCase())) result.add(s);
            }
        } else {
            // 子命令的参数补全
            String subCommand = args[0].toLowerCase();
            CommandHandler handler = actions.get(subCommand);
            if (handler != null) {
                result = handler.suggest(context);
            }
        }

        return result;
    }
}
