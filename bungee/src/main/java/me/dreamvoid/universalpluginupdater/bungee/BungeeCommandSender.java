package me.dreamvoid.universalpluginupdater.bungee;

import me.dreamvoid.universalpluginupdater.platform.CommandSender;
import me.dreamvoid.universalpluginupdater.service.LanguageManager;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.Locale;

public class BungeeCommandSender implements CommandSender {
    private final net.md_5.bungee.api.CommandSender sender;

    protected BungeeCommandSender(net.md_5.bungee.api.CommandSender sender) {
        this.sender = sender;
    }

    @Override
    public void sendMessage(String message) {
        sender.sendMessage(TextComponent.fromLegacy(ChatColor.translateAlternateColorCodes('&', message)));
    }

    @Override
    public void broadcastMessage(String message) {
        sender.sendMessage(TextComponent.fromLegacy(ChatColor.translateAlternateColorCodes('&', message)));
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
        return LanguageManager.getLocale();
    }
}
