package me.dreamvoid.universalpluginupdater.command.action;

import me.dreamvoid.universalpluginupdater.command.CommandContext;
import me.dreamvoid.universalpluginupdater.command.CommandHandler;
import me.dreamvoid.universalpluginupdater.platform.CommandSender;
import me.dreamvoid.universalpluginupdater.platform.Platform;
import me.dreamvoid.universalpluginupdater.service.AsyncLock;
import me.dreamvoid.universalpluginupdater.service.RepositorySyncService;

import java.util.*;
import java.util.logging.Logger;

import static me.dreamvoid.universalpluginupdater.service.LanguageService.tr;

/**
 * repo 子命令处理器
 * 从远程仓库下载/更新插件更新配置
 */
public final class RepoCommand extends CommandHandler {
    private final Logger logger;
    private final RepositorySyncService repositorySyncService;

    public RepoCommand(Platform platform) {
        super(platform);
        this.logger = platform.getPlatformLogger();
        this.repositorySyncService = new RepositorySyncService(platform);
    }

    @Override
    public void execute(CommandContext context) {
        CommandSender sender = context.sender();
        Locale locale = sender.getLocale();
        String[] args = context.args();
        if (args.length == 0) {
            sender.sendMessage("&7用法: /upu repo <update|list|get>");
            sender.sendMessage("&7  /upu repo update - 检查仓库更新配置");
            sender.sendMessage("&7  /upu repo list [--available] [--updatable] - 查看仓库检查结果");
            sender.sendMessage("&7  /upu repo get [all|插件ID...] - 获取仓库更新配置到本地");
            return;
        }

        try (AsyncLock ignored = AsyncLock.acquire()) {
            switch (args[0].toLowerCase()) {
                case "get" -> {
                    boolean downloadAll = args.length <= 1;
                    List<String> pluginIds = new ArrayList<>();

                    for (int i = 1; i < args.length; i++) {
                        String arg = args[i];
                        if (arg == null || arg.isBlank()) {
                            continue;
                        }
                        if ("all".equalsIgnoreCase(arg)) {
                            downloadAll = true;
                            pluginIds.clear();
                            break;
                        }
                        pluginIds.add(arg.toLowerCase(Locale.ROOT));
                    }

                    sender.broadcastMessage("&7从远程仓库获取更新配置...");

                    RepositorySyncService.RepositoryDownloadResult result = repositorySyncService.download(pluginIds, downloadAll);
                    if (result.emptyCache()) {
                        sender.sendMessage("&e没有可获取的仓库更新配置。请先执行 /upu repo update 进行检查。");
                    } else {
                        sender.broadcastMessage("&7仓库获取完成。成功: " + result.successList().size() + "，失败: " + result.failedList().size() + "，跳过: " + result.skippedList().size());
                        if (!result.successList().isEmpty()) {
                            sender.sendMessage("&a获取成功: " + String.join(", ", result.successList()));
                        }
                        if (!result.failedList().isEmpty()) {
                            sender.sendMessage("&c获取失败: " + String.join(", ", result.failedList()));
                        }
                        if (!result.skippedList().isEmpty()) {
                            sender.sendMessage("&e未命中可获取缓存(已跳过): " + String.join(", ", result.skippedList()));
                        }
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
                            sender.sendMessage("&e忽略未知参数: " + arg);
                        }
                    }

                    List<RepositorySyncService.RepoCheckEntry> entries = repositorySyncService.getCachedCheckEntries();

                    sender.broadcastMessage("&7仓库列表结果:");
                    for (RepositorySyncService.RepoCheckEntry entry : entries) {
                        int statusCode = entry.statusCode();
                        boolean available = RepositorySyncService.isAvailable(statusCode);
                        boolean updatable = RepositorySyncService.isUpdatable(statusCode);

                        boolean shouldOutput = (!filterAvailable || available) && (!filterUpdatable || updatable);

                        if (shouldOutput) {
                            Set<String> label = new HashSet<>();
                            if (available) label.add("可获取");
                            if (updatable) label.add("可更新");
                            sender.sendMessage("  &7- &b" + entry.pluginId() + (label.isEmpty() ? "" : " &7[" + String.join(", ", label) + "]"));
                        }
                    }
                }
                case "update" -> {
                    sender.broadcastMessage("&7开始检查仓库更新配置...");

                    RepositorySyncService.RepositoryCheckResult result = repositorySyncService.check();

                    sender.broadcastMessage("&7仓库检查完成。可获取: " + result.availableCount() + "，可更新: " + result.updatableCount() + "，已最新: " + result.latestCount() + "，失败: " + result.failedList().size());
                    sender.sendMessage("&7使用 /upu repo list --available 查看可获取/可更新列表。");
                }
                default -> {
                    sender.sendMessage("&c未知参数: " + args[0]);
                    sender.sendMessage("&7用法: /upu repo <update|list|get>");
                }
            }
        } catch (IllegalStateException e) {
            sender.sendMessage(tr(locale, "message.command.lock.failed"));
            sender.sendMessage(tr(locale, "message.command.lock.warning"));
        } catch (Exception e) {
            logger.severe("执行远程仓库操作时出现异常: " + e);
            sender.sendMessage("&c执行远程仓库操作时出现异常，查看控制台了解更多信息！");
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
                if (args.length == 3) {
                    return "all".startsWith(args[2].toLowerCase())
                            ? List.of("all")
                            : Collections.emptyList();
                }
            }
        }

        return result;
    }
}
