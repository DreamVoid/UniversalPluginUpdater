package me.dreamvoid.universalpluginupdater.bukkit;

import me.dreamvoid.universalpluginupdater.platform.ICommandSender;
import me.dreamvoid.universalpluginupdater.service.LanguageService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Bukkit平台的命令发送者适配器
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
    public void broadcastMessage(String message) {
        //noinspection deprecation
        Command.broadcastCommandMessage(sender, ChatColor.translateAlternateColorCodes('&', message));
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
    public Locale getLocale() {
        //noinspection deprecation
        return sender instanceof Player ? Locale.forLanguageTag(((Player) sender).getLocale().replace('_', '-')) : LanguageService.getLocale();
    }
}
