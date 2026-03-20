package me.dreamvoid.universalpluginupdater.command;

public class UpgradeCommandHandler extends SubCommandHandler {
    @Override
    public boolean execute(CommandContext context) {
        context.getSender().sendMessage("&b开始升级插件...");
        return true;
    }

    @Override
    public String[] getTabCompletion(CommandContext context) {
        return new String[0];
    }
}