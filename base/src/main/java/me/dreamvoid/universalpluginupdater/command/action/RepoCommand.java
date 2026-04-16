package me.dreamvoid.universalpluginupdater.command.action;

import me.dreamvoid.universalpluginupdater.command.CommandContext;
import me.dreamvoid.universalpluginupdater.command.CommandHandler;
import me.dreamvoid.universalpluginupdater.platform.CommandSender;
import me.dreamvoid.universalpluginupdater.platform.Platform;
import me.dreamvoid.universalpluginupdater.service.AsyncLock;
import me.dreamvoid.universalpluginupdater.service.RepositoryService;

import java.util.*;
import java.util.logging.Logger;

import static me.dreamvoid.universalpluginupdater.service.LanguageService.tr;

/**
 * repo 子命令处理器
 * 从远程仓库下载/更新插件更新配置
 */
public final class RepoCommand extends CommandHandler {
    private final Logger logger;
    private final RepositoryService repositoryService;

    public RepoCommand(Platform platform) {
        super(platform);
        this.logger = platform.getPlatformLogger();
        this.repositoryService = new RepositoryService(platform);
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
                            case "all" -> repositoryService.getUpdateResult().stream()
                                    .filter(RepositoryService.ChannelUpdateResult::available)
                                    .map(RepositoryService.ChannelUpdateResult::pluginId)
                                    .forEach(pluginIds::add);
                            case "updatable" -> repositoryService.getUpdateResult().stream()
                                    .filter(RepositoryService.ChannelUpdateResult::updatable)
                                    .map(RepositoryService.ChannelUpdateResult::pluginId)
                                    .forEach(pluginIds::add);
                            default -> pluginIds.add(arg);
                        }
                    }

                    sender.broadcastMessage(tr(locale, "message.command.repo.get.start"));

                    RepositoryService.RepositoryDownloadResult result = repositoryService.download(pluginIds);
                    if (result.emptyCache()) {
                        sender.sendMessage(tr(locale, "message.command.repo.get.none"));
                    } else {
                        sender.broadcastMessage(tr(locale, "message.command.repo.get.summary", result.successList().size(), result.failedList().size(), result.skippedList().size()));
                    }

                }
                case "list" -> {
                    boolean filterAvailable = false;
                    boolean filterUpdatable = false;

                    for (int i = 1; i < args.length; i++) {
                        String arg = args[i];
                        if ("--available".equalsIgnoreCase(arg)) {
                            filterAvailable = true;
                        } else if ("--updatable".equalsIgnoreCase(arg)) {
                            filterUpdatable = true;
                        } else if (arg != null && !arg.isBlank()) {
                            sender.sendMessage(tr(locale, "message.command.error.unknown-argument", arg));
                        }
                    }

                    List<RepositoryService.ChannelUpdateResult> entries = repositoryService.getUpdateResult();

                    sender.broadcastMessage(tr(locale, "message.command.repo.list.header"));
                    for (RepositoryService.ChannelUpdateResult entry : entries) {
                        boolean needOutput = !filterAvailable && !filterUpdatable || !filterAvailable && entry.updatable() || entry.available() && (!filterUpdatable || entry.updatable());

                        if (needOutput) {
                            Set<String> label = new HashSet<>();
                            if (entry.available()) label.add("可获取");
                            if (entry.updatable()) label.add("可更新");
                            sender.sendMessage("  &7- &b" + entry.pluginId() + (label.isEmpty() ? "" : " &7[" + String.join(", ", label) + "]"));
                        }
                    }
                }
                case "update" -> {
                    sender.broadcastMessage(tr(locale, "message.command.repo.update.start"));

                    List<RepositoryService.ChannelUpdateResult> result = repositoryService.update();

                    sender.broadcastMessage(tr(locale,
                            "message.command.repo.update.summary",
                            result.stream().filter(RepositoryService.ChannelUpdateResult::available).count(),
                            result.stream().filter(RepositoryService.ChannelUpdateResult::updatable).count(),
                            result.stream().filter(RepositoryService.ChannelUpdateResult::latest).count(),
                            result.stream().filter(RepositoryService.ChannelUpdateResult::skipped).count()));
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

        if (args.length == 2) {
            String input = args[1].toLowerCase();
            if ("update".startsWith(input)) result.add("update");
            if ("list".startsWith(input)) result.add("list");
            if ("get".startsWith(input)) result.add("get");
        } else if (args.length >= 3) {
            if ("list".equalsIgnoreCase(args[1])) {
                Set<String> used = new HashSet<>(Arrays.asList(args).subList(2, args.length));
                String input = args[args.length - 1].toLowerCase();
                if ("--available".startsWith(input) && !used.contains(input)) {
                    result.add("--available");
                }
                if ("--updatable".startsWith(input) && !used.contains(input)) {
                    result.add("--updatable");
                }
            } else if ("get".equalsIgnoreCase(args[1])) {
                Set<String> used = new HashSet<>(Arrays.asList(args).subList(2, args.length));
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
