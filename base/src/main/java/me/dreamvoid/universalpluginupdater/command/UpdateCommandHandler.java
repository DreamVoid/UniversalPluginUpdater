package me.dreamvoid.universalpluginupdater.command;

import me.dreamvoid.universalpluginupdater.Utils;
import me.dreamvoid.universalpluginupdater.plugin.PendingUpdate;
import me.dreamvoid.universalpluginupdater.service.AsyncLock;
import me.dreamvoid.universalpluginupdater.service.UpdateManager;

import java.text.MessageFormat;
import java.util.List;
import java.util.logging.Logger;

/**
 * update 子命令处理器
 * 检查所有插件的更新
 */
public class UpdateCommandHandler extends SubCommandHandler {
    private static final Logger logger = Utils.getLogger();

    @Override
    public boolean execute(CommandContext context) {
        if(AsyncLock.tryAcquire()){
            try {
                context.getSender().broadcastMessage("&7开始检查插件更新...");

                // 获取待更新的插件列表
                UpdateManager updateManager = UpdateManager.getInstance();
                List<PendingUpdate> pendingUpdates = updateManager.checkAllPluginUpdates();

                if (pendingUpdates.isEmpty()) {
                    context.getSender().broadcastMessage("所有插件都已是最新版本。");
                    return true;
                }

                // 显示可更新的插件数量
                context.getSender().broadcastMessage(MessageFormat.format("发现 {0} 个可更新的插件。", pendingUpdates.size()));

                // 详细列出每个可更新的插件
                for (PendingUpdate update : pendingUpdates) {
                    String message = MessageFormat.format("  &7- &b{0} &f({1} &7-> &f{2}) &7[{3}]", update.getPluginName(), update.getCurrentVersion(), update.getNewVersion(), update.getUpdateChannel());
                    context.getSender().sendMessage(message);
                }

                context.getSender().sendMessage("使用 /upu download 命令下载更新，或使用 /upu upgrade 直接更新");
                return true;
            } catch (Exception e) {
                logger.severe("检查更新时出错: " + e);
                context.getSender().broadcastMessage("&c检查更新时出错，请查看控制台了解更多信息！");
                return false;
            } finally {
                AsyncLock.release();
            }
        } else {
            context.getSender().broadcastMessage("&c无法获得锁。锁正由另一个线程持有。");
            context.getSender().broadcastMessage("&7请注意，通过其他手段移除锁不一定是合适的解决方案，且可能损坏您的系统。");
            return false;
        }
    }

    @Override
    public String[] getTabCompletion(CommandContext context) {
        // update命令暂无参数补全
        return new String[0];
    }
}
