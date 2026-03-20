package me.dreamvoid.universalpluginupdater.command;

public class DownloadCommandHandler extends SubCommandHandler {
    @Override
    public boolean execute(CommandContext context) {
        context.getSender().sendMessage("&b开始下载插件更新...");
        return true;
    }

    @Override
    public String[] getTabCompletion(CommandContext context) {
        return new String[0];
    }
}