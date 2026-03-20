package me.dreamvoid.universalpluginupdater.command;

import me.dreamvoid.universalpluginupdater.service.AsyncLock;

/**
 * download 子命令处理器
 * 仅下载插件更新
 */
public class DownloadCommandHandler extends SubCommandHandler {
    @Override
    public boolean execute(CommandContext context) {
        if(AsyncLock.tryAcquire()) {
            try {
                context.getSender().sendMessage("&b开始下载插件更新...");

                // TODO: 实现具体的下载逻辑

                return true;
            } finally {
                AsyncLock.release();
            }
        } else {
            context.getSender().sendMessage("&c无法获得锁。锁正由另一个线程持有。");
            context.getSender().sendMessage("&7请注意，通过其他手段移除锁不一定是合适的解决方案，且可能损坏您的系统。");
            return false;
        }
    }

    @Override
    public String[] getTabCompletion(CommandContext context) {
        // download命令暂无参数补全
        return new String[0];
    }
}
