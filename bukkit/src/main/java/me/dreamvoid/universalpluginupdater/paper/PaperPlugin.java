package me.dreamvoid.universalpluginupdater.paper;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.dreamvoid.universalpluginupdater.bukkit.BukkitPlugin;
import me.dreamvoid.universalpluginupdater.command.CommandContext;
import me.dreamvoid.universalpluginupdater.platform.ICommandSender;
import me.dreamvoid.universalpluginupdater.service.LanguageService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * Paper平台主类
 * @author DreamVoid
 */
public class PaperPlugin extends BukkitPlugin {

    @Override
    public void onLoad() {
        super.onLoad();
    }

    @Override
    public void onEnable() {
        super.onEnable();

        getLogger().info("正在向 Paper 注册命令...");
        registerCommand("universalpluginupdater", "UniversalPluginUpdater 主命令", Collections.singleton("upu"), new BasicCommand() {
            @Override
            public void execute(@NonNull CommandSourceStack stack, String @NonNull [] args) {
                PaperCommandSender sender = new PaperCommandSender(stack.getSender());
                CommandContext context = new CommandContext(sender, args);
                commandHandler.executeCommand(context);
            }

            @Override
            public String permission() {
                return "universalpluginupdater.command";
            }

            @Override
            public @NonNull Collection<String> suggest(@NonNull CommandSourceStack stack, String @NonNull [] args) {
                PaperCommandSender sender = new PaperCommandSender(stack.getSender());
                CommandContext context = new CommandContext(sender, args);
                return commandHandler.getTabCompletion(context);
            }
        });
    }

    // 平台实现接口

    @Override
    public String getPlatformName() {
        return "Paper";
    }

    @Override
    public List<String> getGameVersions() {
        return Collections.singletonList(getServer().getMinecraftVersion());
    }

    @Override
    public List<String> getLoaders() {
        return Arrays.asList("bukkit", "paper");
    }

    @Override
    @NotNull
    public String getPluginVersion() {
        return getPluginMeta().getVersion();
    }

    @Override
    @Nullable
    public String getPluginVersion(String pluginName) {
        Plugin plugin = getServer().getPluginManager().getPlugin(pluginName);
        if (plugin != null) {
            return plugin.getPluginMeta().getVersion();
        } else {
            return null;
        }
    }

    /**
     * 获取指定命令发送者的Locale
     * @param sender 命令发送者
     * @return {@link Locale} 对象
     */
    @Override
    public Locale getLocale(ICommandSender sender){
        if(sender != null && sender.getHandle() instanceof Player player){
            return player.locale();
        } else {
            return LanguageService.instance().getLocale();
        }
    }
}
