package me.dreamvoid.universalpluginupdater.command.sub;

import me.dreamvoid.universalpluginupdater.command.CommandContext;
import me.dreamvoid.universalpluginupdater.command.ISubCommand;
import me.dreamvoid.universalpluginupdater.service.AsyncLock;

import java.util.Collections;
import java.util.List;

/**
 * download 子命令处理器
 * 仅下载插件更新
 */
public class DownloadCommand implements ISubCommand {
    @Override
    public void execute(CommandContext context) {
        if(AsyncLock.tryAcquire()) {
            try {
                context.getSender().sendMessage("&b开始下载插件更新...");

                // TODO: 实现具体的下载逻辑

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
