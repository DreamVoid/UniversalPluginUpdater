package me.dreamvoid.universalpluginupdater.bukkit;

import me.dreamvoid.universalpluginupdater.platform.ICommandSender;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

/**
 * Bukkit平台的命令发送者适配器
 * 将Bukkit的CommandSender转换为我们的抽象CommandSender
 */
public class BukkitCommandSender implements ICommandSender {
    protected final CommandSender sender;

    public BukkitCommandSender(CommandSender sender) {
        this.sender = sender;
    }

    @Override
    public void sendMessage(String message) {
        //noinspection deprecation
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    @Override
    public boolean hasPermission(String permission) {
        return sender.hasPermission(permission);
    }

    @Override
    public String getName() {
        return sender.getName();
    }

    @Override
    public boolean isConsole() {
        return sender instanceof org.bukkit.command.ConsoleCommandSender;
    }
}
