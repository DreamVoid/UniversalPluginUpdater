package me.dreamvoid.universalpluginupdater.command;

/**
 * download 子命令处理器
 * 仅下载插件更新
 */
public class DownloadCommandHandler extends SubCommandHandler {
    @Override
    public boolean execute(CommandContext context) {
        context.getSender().sendMessage("&b开始下载插件更新...");
        
        // TODO: 实现具体的下载逻辑
        
        return true;
    }

    @Override
    public String[] getTabCompletion(CommandContext context) {
        // download命令暂无参数补全
        return new String[0];
    }
}
