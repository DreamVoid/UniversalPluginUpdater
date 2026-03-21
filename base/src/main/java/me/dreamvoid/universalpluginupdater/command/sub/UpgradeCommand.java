package me.dreamvoid.universalpluginupdater.command.sub;

import me.dreamvoid.universalpluginupdater.command.CommandContext;
import me.dreamvoid.universalpluginupdater.command.ISubCommand;
import me.dreamvoid.universalpluginupdater.platform.IPlatformProvider;
import me.dreamvoid.universalpluginupdater.plugin.UpdateInfo;
import me.dreamvoid.universalpluginupdater.service.AsyncLock;
import me.dreamvoid.universalpluginupdater.service.UpdateManager;
import me.dreamvoid.universalpluginupdater.service.UpgradeService;
import me.dreamvoid.universalpluginupdater.update.AbstractUpdate;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * upgrade 子命令处理器
 * 执行插件更新升级（下载 + 更新）
 */
public final class UpgradeCommand implements ISubCommand {
    private final Logger logger;
    public UpgradeCommand(IPlatformProvider platform) {
        logger = platform.getPlatformLogger();
    }

    @Override
    public void execute(CommandContext context) {
        if(AsyncLock.tryAcquire()) {
            try {
                String[] subArgs = context.getSubCommandArgs();
                boolean executeNow = false;
                for (String arg : subArgs) {
                    if ("--now".equalsIgnoreCase(arg)) {
                        executeNow = true;
                    } else {
                        context.getSender().sendMessage(MessageFormat.format("&c未知的命令参数: {0}", arg));
                    }
                }

                context.getSender().broadcastMessage("&7开始升级插件...");

                // 获取缓存的更新信息
                UpdateManager updateManager = UpdateManager.getInstance();
                List<UpdateInfo> updateInfos = updateManager.getCachedUpdateInfos();

                // 检查是否有可升级的更新
                if (updateInfos.isEmpty()) {
                    context.getSender().broadcastMessage("目前没有可安装更新的插件。使用 /upu update 检查更新。");
                    return;
                }

                // 统计成功和失败的升级数
                int successCount = 0;
                int failureCount = 0;
                boolean scheduleUpgrade = UpgradeService.getInstance().canUpgradeNow(executeNow);

                // 遍历每个待更新的插件，执行升级
                for (UpdateInfo updateInfo : updateInfos) {
                    String pluginId = updateInfo.pluginName();
                    if (scheduleUpgrade) {
                        logger.info(MessageFormat.format("正在升级 {0}...", pluginId));
                    } else {
                        logger.info(MessageFormat.format("正在安排 {0} 的升级任务...", pluginId));
                    }

                    try {
                        // 获取该插件的更新实例
                        AbstractUpdate updateInstance = updateManager.getUpdateChannelForPlugin(pluginId);
                        if (updateInstance == null) {
                            logger.warning(MessageFormat.format("无法获取插件 {0} 的更新渠道！", pluginId));
                            failureCount++;
                            continue;
                        }

                        // 执行升级
                        if (updateInstance.upgrade(executeNow)) {
                            logger.info(MessageFormat.format(scheduleUpgrade ? "插件 {0} 已升级。" : "{0} 已加入升级队列。", pluginId));
                            successCount++;
                        } else {
                            logger.warning(MessageFormat.format("{0} 升级失败！", pluginId));
                            failureCount++;
                        }
                    } catch (Exception e) {
                        logger.warning(MessageFormat.format("{0} 升级时出现异常: {1}", pluginId, e.getMessage()));
                        failureCount++;
                    }
                }

                context.getSender().broadcastMessage(MessageFormat.format(scheduleUpgrade ?
                                "升级了 {0} 个插件，有 {1} 个插件升级失败。重新启动服务器以启用新插件。" :
                                "安排了 {0} 个插件的升级任务，有 {1} 个插件升级失败。重新启动服务器以完成升级过程。",
                        successCount,
                        failureCount));
            } catch (Exception e) {
                logger.severe("执行升级时出错: " + e);
                context.getSender().broadcastMessage("&c执行升级时出错，请查看控制台了解更多信息！");
            } finally {
                AsyncLock.release();
            }
        } else {
            context.getSender().sendMessage("&c无法获得锁。锁正由另一个线程持有。");
            context.getSender().sendMessage("&7请注意，通过其他手段移除锁不一定是合适的解决方案，且可能损坏您的系统。");
        }
    }

    @Override
    public List<String> getTabCompletion(CommandContext context) {
        String[] subArgs = context.getSubCommandArgs();
        if (subArgs.length <= 1) {
            if (subArgs.length == 0 || "--now".startsWith(subArgs[0])) {
                return Collections.singletonList("--now");
            }
        }
        return Collections.emptyList();
    }
}
