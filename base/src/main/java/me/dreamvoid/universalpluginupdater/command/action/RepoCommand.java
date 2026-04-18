package me.dreamvoid.universalpluginupdater.command.action;

import me.dreamvoid.universalpluginupdater.command.CommandContext;
import me.dreamvoid.universalpluginupdater.command.CommandHandler;
import me.dreamvoid.universalpluginupdater.platform.CommandSender;
import me.dreamvoid.universalpluginupdater.platform.Platform;
import me.dreamvoid.universalpluginupdater.service.AsyncLock;
import me.dreamvoid.universalpluginupdater.service.RepositoryManager;

import java.util.*;
import java.util.logging.Logger;

import static me.dreamvoid.universalpluginupdater.service.LanguageManager.tr;

/**
 * repo 子命令处理器
 * 从远程仓库下载/更新插件更新配置
 */
public final class RepoCommand extends CommandHandler {
    private final Logger logger;

    public RepoCommand(Platform platform) {
        super(platform);
        logger = platform.getPlatformLogger();
    }

    @Override
    public void execute(CommandContext context) {
        CommandSender sender = context.sender();
        Locale locale = sender.getLocale();
        String[] args = context.args();
        if (args.length == 0) {
            sender.broadcastMessage(tr(locale, "message.command.repo.help"));
            return;
        }

        try (AsyncLock ignored = AsyncLock.acquire()) {
            switch (args[0].toLowerCase()) {
                case "get" -> {
                    if (args.length == 1) {
                        sender.sendMessage(tr(locale, "message.command.repo.get.error.no-argument"));
                        return;
                    }

                    Set<String> pluginIds = new HashSet<>();

                    for (int i = 1; i < args.length; i++) {
                        String arg = args[i].toLowerCase();
                        switch (arg) {
                            case "all" -> RepositoryManager.instance().getChannelUpdateResults().stream()
                                    .map(RepositoryManager.ChannelUpdateResult::pluginId)
                                    .forEach(pluginIds::add);
                            case "updatable" -> {
                                for (RepositoryManager.ChannelUpdateResult channelUpdateResult : RepositoryManager.instance().getChannelUpdateResults()) {
                                    if (channelUpdateResult.localFileStatus() == 2) {
                                        String pluginId = channelUpdateResult.pluginId();
                                        pluginIds.add(pluginId);
                                    }
                                }
                            }
                            default -> pluginIds.add(arg);
                        }
                    }

                    sender.broadcastMessage(tr(locale, "message.command.repo.get.start"));

                    RepositoryManager.RepositoryDownloadResult result = RepositoryManager.instance().download(pluginIds);
                    int success = result.success();
                    int failed = result.failed();
                    int skipped = result.skipped();
                    if (success > 0 || failed > 0 || skipped > 0) {
                        sender.broadcastMessage(tr(locale, "message.command.repo.get.summary", success, failed, skipped));
                    } else {
                        sender.sendMessage(tr(locale, "message.command.repo.get.none"));
                    }

                }
                case "list" -> {
                    boolean filterUpdatable = false;

                    for (int i = 1; i < args.length; i++) {
                        String arg = args[i];
                        if ("--updatable".equalsIgnoreCase(arg)) {
                            filterUpdatable = true;
                        } else if (arg != null && !arg.isBlank()) {
                            sender.sendMessage(tr(locale, "message.command.error.unknown-argument", arg));
                        }
                    }

                    List<RepositoryManager.ChannelUpdateResult> entries = RepositoryManager.instance().getChannelUpdateResults();

                    if (filterUpdatable) {
                        entries = entries.stream()
                                .filter(entry -> entry.localFileStatus() == 2)
                                .toList();
                    }

                    if (!entries.isEmpty()) {
                        sender.sendMessage(tr(locale, "message.command.repo.list.header"));
                        for (RepositoryManager.ChannelUpdateResult entry : entries) {
                            Set<String> label = new HashSet<>();
                            if (entry.localFileStatus() == 2) label.add("可更新");
                            sender.sendMessage("  &7- &b" + entry.pluginId() + (label.isEmpty() ? "" : " &7[" + String.join(", ", label) + "]"));
                        }
                    } else {
                        sender.sendMessage(tr(locale, "message.command.repo.list.none"));
                    }
                }
                case "update" -> {
                    sender.broadcastMessage(tr(locale, "message.command.repo.update.start"));

                    List<RepositoryManager.ChannelUpdateResult> result = RepositoryManager.instance().update();

                    long count = result.stream()
                            .filter(channelUpdateResult -> channelUpdateResult.localFileStatus() == 2)
                            .count();
                    sender.broadcastMessage(tr(locale,
                            "message.command.repo.update.summary",
                            result.size(),
                            count));
                    sender.sendMessage(tr(locale, "message.command.repo.update.next"));
                }
                default -> {
                    sender.sendMessage(tr(locale, "message.command.error.unknown-argument", args[0]));
                    sender.sendMessage(tr(locale, "message.command.repo.usage"));
                }
            }
        } catch (IllegalStateException e) {
            sender.sendMessage(tr(locale, "message.command.lock.failed"));
            sender.sendMessage(tr(locale, "message.command.lock.warning"));
        } catch (Exception e) {
            logger.severe(tr("message.command.repo.exception.log") + e);
            sender.sendMessage(tr(locale, "message.command.repo.exception.game"));
        }
    }

    @Override
    public List<String> suggest(CommandContext context) {
        String[] args = context.args();
        List<String> result = new java.util.ArrayList<>();

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            if ("update".startsWith(input)) result.add("update");
            if ("list".startsWith(input)) result.add("list");
            if ("get".startsWith(input)) result.add("get");
        } else if (args.length >= 2) {
            if ("list".equalsIgnoreCase(args[0])) {
                Set<String> used = new HashSet<>(Arrays.asList(args).subList(1, args.length));
                String input = args[args.length - 1].toLowerCase();
                if ("--updatable".startsWith(input) && !used.contains(input)) {
                    result.add("--updatable");
                }
            } else if ("get".equalsIgnoreCase(args[0])) {
                Set<String> used = new HashSet<>(Arrays.asList(args).subList(1, args.length));
                String input = args[args.length - 1].toLowerCase();
                if ("all".startsWith(input) && !used.contains(input)) {
                    result.add("all");
                }
                if ("updatable".startsWith(input) && !used.contains(input)) {
                    result.add("updatable");
                }
            }
        }

        return result;
    }
}
