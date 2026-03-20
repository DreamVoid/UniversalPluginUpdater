package me.dreamvoid.universalpluginupdater.bukkit;

import me.dreamvoid.universalpluginupdater.LifeCycle;
import me.dreamvoid.universalpluginupdater.command.CommandContext;
import me.dreamvoid.universalpluginupdater.command.CommandHandler;
import me.dreamvoid.universalpluginupdater.platform.IPlatformProvider;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Bukkit 平台主类
 * @author DreamVoid
 */
public class BukkitPlugin extends JavaPlugin implements IPlatformProvider {
    protected final CommandHandler commandHandler;
    protected final LifeCycle lifeCycle;

    public BukkitPlugin() {
        lifeCycle = new LifeCycle(this);
        this.commandHandler = new CommandHandler();

        lifeCycle.startUp(getLogger());
    }

    @Override
    public void onLoad() {
        lifeCycle.preload();
    }

    @Override
    public void onEnable() {
        lifeCycle.postload();
    }

    @Override
    public void onDisable() {
        lifeCycle.unload();
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command cmd, @NonNull String label, String @NonNull [] args) {
        BukkitCommandSender commandSender = new BukkitCommandSender(sender);
        CommandContext context = new CommandContext(label, args, commandSender);
        return commandHandler.handleCommand(context);
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command cmd, @NonNull String alias, String @NonNull [] args) {
        BukkitCommandSender commandSender = new BukkitCommandSender(sender);
        CommandContext context = new CommandContext(alias, args, commandSender);

        String[] completions = commandHandler.getTabCompletion(context);
        return new ArrayList<>(Arrays.asList(completions));
    }

    // 平台实现接口

    @Override
    @NotNull
    public Path getDataPath() {
        return getDataFolder().toPath();
    }

    @Override
    public List<String> getPlugins() {
        return Arrays.stream(getServer().getPluginManager().getPlugins()).map(p -> p.getName().toLowerCase()).collect(Collectors.toList());
    }

    @Override
    public String getPluginVersion(String pluginName) {
        Plugin plugin = getServer().getPluginManager().getPlugin(pluginName);
        if (plugin != null) {
            //noinspection deprecation
            return plugin.getDescription().getVersion();
        } else {
            return null;
        }
    }

    @Override
    public List<String> getLoaders() {
        return Collections.singletonList("bukkit");
    }

    @Override
    public List<String> getGameVersions() {
        String version = Bukkit.getVersion();
        return Collections.singletonList(version.substring(version.lastIndexOf("MC: ") + 4, version.length() - 1));
    }

    @Override
    public Logger getPlatformLogger() {
        return getLogger();

    }
}
