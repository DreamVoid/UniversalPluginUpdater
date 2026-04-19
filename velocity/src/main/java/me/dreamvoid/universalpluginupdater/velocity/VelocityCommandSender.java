package me.dreamvoid.universalpluginupdater.velocity;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.Player;
import me.dreamvoid.universalpluginupdater.platform.CommandSender;
import me.dreamvoid.universalpluginupdater.service.LanguageManager;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Locale;

public class VelocityCommandSender implements CommandSender {
    private final CommandSource sender;

    VelocityCommandSender(CommandSource sender) {
        this.sender = sender;
    }

    @Override
    public void sendMessage(String message) {
        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(message));
    }

    @Override
    public void broadcastMessage(String message) {
        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(message));
    }

    @Override
    public boolean hasPermission(String permission) {
        return sender.hasPermission(permission);
    }

    @Override
    public String getName() {
        if(sender instanceof Player player){
            return player.getUsername();
        } else if(sender instanceof ConsoleCommandSource console){
            return "Console";
        } else {
            return "Unknown";
        }
    }

    @Override
    public Locale getLocale() {
        if(sender instanceof Player player){
            return player.getEffectiveLocale();
        } else {
            return LanguageManager.getLocale();
        }
    }
}
