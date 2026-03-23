package me.dreamvoid.universalpluginupdater.paper;

import me.dreamvoid.universalpluginupdater.bukkit.BukkitCommandSender;
import me.dreamvoid.universalpluginupdater.service.LanguageService;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Paper平台的命令发送者适配器
 */
public class PaperCommandSender extends BukkitCommandSender {
    public PaperCommandSender(CommandSender sender) {
        super(sender);
    }

    @Override
    public void sendMessage(String message) {
        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(message));
    }

    @Override
    public void broadcastMessage(String message) {
        Command.broadcastCommandMessage(sender, LegacyComponentSerializer.legacyAmpersand().deserialize(message));
    }

    @Override
    public Locale getLocale() {
        return sender instanceof Player ? ((Player) sender).locale() : LanguageService.instance().getLocale();
    }
}
