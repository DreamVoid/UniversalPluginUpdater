package me.dreamvoid.universalpluginupdater.command;

/**
 * upgrade 子命令处理器
 * 下载并更新插件
 */
public class UpgradeCommandHandler extends SubCommandHandler {
    @Override
    public boolean execute(CommandContext context) {
        context.getSender().sendMessage("&b开始升级插件...");
        
        // TODO: 实现具体的升级逻辑
        
        return true;
    }

    @Override
    public String[] getTabCompletion(CommandContext context) {
        // upgrade命令暂无参数补全
        return new String[0];
    }
}
