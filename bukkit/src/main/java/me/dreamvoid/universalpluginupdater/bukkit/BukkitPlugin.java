package me.dreamvoid.universalpluginupdater.bukkit;

import me.dreamvoid.universalpluginupdater.Config;
import me.dreamvoid.universalpluginupdater.LifeCycle;
import me.dreamvoid.universalpluginupdater.bukkit.update.BukkitPluginUpdate;
import me.dreamvoid.universalpluginupdater.bukkit.upgrade.BukkitUpgradeStrategy;
import me.dreamvoid.universalpluginupdater.command.CommandContext;
import me.dreamvoid.universalpluginupdater.command.CommandHandler;
import me.dreamvoid.universalpluginupdater.platform.Platform;
import me.dreamvoid.universalpluginupdater.platform.Scheduler;
import me.dreamvoid.universalpluginupdater.service.UpdateManager;
import me.dreamvoid.universalpluginupdater.upgrade.UpgradeStrategyRegistry;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Bukkit 平台主类
 * @author DreamVoid
 */
public class BukkitPlugin extends JavaPlugin implements Platform {
    protected final LifeCycle lifeCycle;
    protected final CommandHandler commandHandler;

    public BukkitPlugin() {
        lifeCycle = new LifeCycle(this);
        this.commandHandler = new CommandHandler(this);

        lifeCycle.startUp(getLogger());
    }

    @Override
    public void onLoad() {
        lifeCycle.preLoad();

        UpdateManager.registerUpdateInstance(new BukkitPluginUpdate(getName()));

        // 注册 Bukkit 特定的升级策略
        UpgradeStrategyRegistry.instance().registerStrategy("bukkit", new BukkitUpgradeStrategy(getLogger()));
    }

    @Override
    public void onEnable() {
        lifeCycle.postLoad();
    }

    @Override
    public void onDisable() {
        lifeCycle.unload();
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command cmd, @NonNull String label, String @NonNull [] args) {
        BukkitCommandSender commandSender = new BukkitCommandSender(sender);
        CommandContext context = new CommandContext(commandSender, args);
        commandHandler.execute(context);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command cmd, @NonNull String alias, String @NonNull [] args) {
        BukkitCommandSender commandSender = new BukkitCommandSender(sender);
        CommandContext context = new CommandContext(commandSender, args);
        return commandHandler.suggest(context);
    }

    // 平台实现接口

    @Override
    public @NotNull String getPlatformName(){
        return "Bukkit";
    }

    @Override
    @NotNull
    public Path getDataPath() {
        return getDataFolder().toPath();
    }

    @Override
    public @NonNull List<String> getPlugins() {
        return Arrays.stream(getServer().getPluginManager().getPlugins()).map(p -> p.getName().toLowerCase()).collect(Collectors.toList());
    }

    @SuppressWarnings("deprecation")
    @Override
    @NonNull
    public String getPluginVersion() {
        return this.getDescription().getVersion();
    }

    @Override
    @Nullable
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
    public @NonNull List<String> getLoaders() {
        return Collections.singletonList("bukkit");
    }

    @Override
    public @NonNull List<String> getGameVersions() {
        String version = Bukkit.getVersion();
        return Collections.singletonList(version.substring(version.lastIndexOf("MC: ") + 4, version.length() - 1));
    }

    @Override
    public @NonNull Logger getPlatformLogger() {
        return getLogger();
    }

    @Override
    public @NonNull Config getPlatformConfig() {
        return new BukkitConfig(this);
    }

    @Override
    public Scheduler getScheduler() {
        return new Scheduler() {
            @Override
            public void runTaskAsync(Runnable runnable) {
                getServer().getScheduler().runTaskAsynchronously(BukkitPlugin.this, runnable);
            }

            @Override
            public void runTaskLaterAsync(Runnable runnable, long delay) {
                getServer().getScheduler().runTaskLaterAsynchronously(BukkitPlugin.this, runnable, delay * 50);
            }

            @Override
            public void runTaskLaterAsync(Runnable runnable, Duration delay) {
                getServer().getScheduler().runTaskLaterAsynchronously(BukkitPlugin.this, runnable, delay.toMillis() / 50);
            }

            @Override
            public void runTaskTimerAsync(Runnable runnable, long repeat) {
                getServer().getScheduler().runTaskTimerAsynchronously(BukkitPlugin.this, runnable, 0, repeat * 50);
            }

            @Override
            public void runTaskTimerAsync(Runnable runnable, Duration repeat) {
                getServer().getScheduler().runTaskTimerAsynchronously(BukkitPlugin.this, runnable, 0, repeat.toMillis() / 50);
            }
        };
    }


    @Override
    @Nullable
    public Path getPluginFile(String pluginId) {
        Plugin plugin = getServer().getPluginManager().getPlugin(pluginId);
        if (plugin == null || !plugin.isEnabled()) {
            return null;
        }

        try {
            Method getFileMethod = JavaPlugin.class.getDeclaredMethod("getFile");
            getFileMethod.setAccessible(true);
            File pluginFile = (File) getFileMethod.invoke(plugin);

            if (pluginFile != null) {
                return pluginFile.toPath();
            }
        } catch (NoSuchMethodException e) {
            getLogger().warning("Failed to get plugin file for " + pluginId + ": getFile method not found");
        } catch (IllegalAccessException e) {
            getLogger().warning("Failed to get plugin file for " + pluginId + ": access denied");
        } catch (Exception e) {
            getLogger().warning("Failed to get plugin file for " + pluginId + " using reflection: " + e);
        }

        return null;
    }

    @Override
    public boolean unloadPlugin(String pluginId) {
        Plugin plugin = getServer().getPluginManager().getPlugin(pluginId);
        if(plugin == null) {
            return false;
        }
        getServer().getPluginManager().disablePlugin(plugin);
        return true;
    }

}
