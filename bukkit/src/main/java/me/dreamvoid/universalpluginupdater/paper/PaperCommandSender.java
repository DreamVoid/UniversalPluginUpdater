package me.dreamvoid.universalpluginupdater.paper;

import me.dreamvoid.universalpluginupdater.bukkit.BukkitCommandSender;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

/**
 * Paper平台的命令发送者适配器
 * 将Bukkit的CommandSender转换为我们的抽象CommandSender
 * 使用Adventure API处理消息格式
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
}
