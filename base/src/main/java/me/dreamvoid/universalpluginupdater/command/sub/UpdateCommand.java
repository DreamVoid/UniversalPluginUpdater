package me.dreamvoid.universalpluginupdater.command.sub;

import me.dreamvoid.universalpluginupdater.command.CommandContext;
import me.dreamvoid.universalpluginupdater.command.ISubCommand;
import me.dreamvoid.universalpluginupdater.platform.IPlatformProvider;
import me.dreamvoid.universalpluginupdater.plugin.UpdateInfo;
import me.dreamvoid.universalpluginupdater.service.AsyncLock;
import me.dreamvoid.universalpluginupdater.service.UpdateManager;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * update 子命令处理器
 * 检查所有插件的更新
 */
public final class UpdateCommand implements ISubCommand {
    private final Logger logger;

    public UpdateCommand(IPlatformProvider platform) {
        logger = platform.getPlatformLogger();
    }

    @Override
    public void execute(CommandContext context) {
        if(AsyncLock.tryAcquire()){
            try {
                context.getSender().broadcastMessage("&7开始检查插件更新...");

                // 获取待更新的插件列表
                UpdateManager updateManager = UpdateManager.getInstance();
                List<UpdateInfo> updateInfos = updateManager.checkAllPluginUpdates();

                if (updateInfos.isEmpty()) {
                    context.getSender().broadcastMessage("所有插件都已是最新版本。");
                    return;
                }

                // 显示可更新的插件数量
                context.getSender().broadcastMessage(MessageFormat.format("有 {0} 个插件可以升级。", updateInfos.size()));

                // 详细列出每个可更新的插件
                for (UpdateInfo update : updateInfos) {
                    String message = MessageFormat.format("  &7- &b{0} &f({1} &7-> &f{2}) &7[{3}]", update.pluginName(), update.currentVersion(), update.newVersion(), update.updateChannel());
                    context.getSender().sendMessage(message);
                }

                context.getSender().broadcastMessage("使用 /upu download 命令下载更新，或使用 /upu upgrade 安装更新。");
            } catch (Exception e) {
                logger.severe("检查更新时出错: " + e);
                context.getSender().broadcastMessage("&c检查更新时出错，请查看控制台了解更多信息！");
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
        // TODO: 占位
        return Collections.emptyList();
    }
}
