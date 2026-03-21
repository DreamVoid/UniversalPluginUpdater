package me.dreamvoid.universalpluginupdater.command;

import me.dreamvoid.universalpluginupdater.service.AsyncLock;

import java.util.Collections;
import java.util.List;

/**
 * upgrade 子命令处理器
 * 下载并更新插件
 */
public class UpgradeCommand implements ISubCommand {
    @Override
    public void execute(CommandContext context) {
        if(AsyncLock.tryAcquire()) {
            try {
                context.getSender().sendMessage("&b开始升级插件...");

                // TODO: 实现具体的升级逻辑

            } finally {
                AsyncLock.release();
            }
        } else {
            context.getSender().broadcastMessage("&c无法获得锁。锁正由另一个线程持有。");
            context.getSender().broadcastMessage("&7请注意，通过其他手段移除锁不一定是合适的解决方案，且可能损坏您的系统。");
        }
    }

    @Override
    public List<String> getTabCompletion(CommandContext context) {
        // TODO: 占位
        return Collections.emptyList();
    }
}
