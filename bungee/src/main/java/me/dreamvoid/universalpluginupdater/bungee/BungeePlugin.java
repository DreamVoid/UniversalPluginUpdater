package me.dreamvoid.universalpluginupdater.bungee;

import me.dreamvoid.universalpluginupdater.Config;
import me.dreamvoid.universalpluginupdater.LifeCycle;
import me.dreamvoid.universalpluginupdater.Utils;
import me.dreamvoid.universalpluginupdater.reflection.FieldAccessor;
import me.dreamvoid.universalpluginupdater.command.CommandContext;
import me.dreamvoid.universalpluginupdater.command.CommandHandler;
import me.dreamvoid.universalpluginupdater.platform.Platform;
import me.dreamvoid.universalpluginupdater.platform.Scheduler;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.command.PlayerCommand;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static me.dreamvoid.universalpluginupdater.service.LanguageManager.tr;

@SuppressWarnings("deprecation")
public final class BungeePlugin extends Plugin implements Platform {
    private final LifeCycle lifeCycle;
    private final CommandHandler commandHandler;

    public BungeePlugin() {
        lifeCycle = new LifeCycle(this);
        commandHandler = new CommandHandler(this);

        lifeCycle.startUp(getLogger());
    }

    @Override
    public void onLoad() {
        lifeCycle.preLoad();
    }

    @Override
    public void onEnable() {
        lifeCycle.postLoad();

        getLogger().info(tr("message.lifecycle.bungee.postload.register-command"));
        getProxy().getPluginManager().registerCommand(this, new PlayerCommand("universalpluginupdater", "universalpluginupdater.command", "upu") {
            @Override
            public void execute(CommandSender sender, String[] args) {
                commandHandler.execute(new CommandContext(new BungeeCommandSender(sender), args));
            }

            @Override
            public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
                return commandHandler.suggest(new CommandContext(new BungeeCommandSender(sender), args));
            }
        });
    }

    @Override
    public void onDisable() {
        lifeCycle.unload();
    }

    @Override
    public @NonNull String getPlatformName() {
        return Utils.findClass("io.github.waterfallmc.waterfall.QueryResult") ? "Waterfall" : "BungeeCord";
    }

    @Override
    public @NonNull List<String> getGameVersions() {
        String version = getProxy().getVersion();
        String[] parts = version.split(":");
        if (parts.length >= 3) {
            version = parts[2].split("-")[0];
        }
        return Collections.singletonList(version);
    }

    @Override
    public @NonNull List<String> getLoaders() {
        List<String> loaders = new ArrayList<>();
        loaders.add("bungeecord");
        if(getPlatformName().equals("Waterfall")){
            loaders.add("waterfall");
        }
        return loaders;
    }

    @Override
    public @NonNull Logger getPlatformLogger() {
        return getLogger();
    }

    @Override
    public @NonNull Config getPlatformConfig() {
        return new BungeeConfig(this);
    }

    @Override
    public Scheduler getScheduler() {
        return new Scheduler() {
            @Override
            public void runTaskAsync(Runnable runnable) {
                getProxy().getScheduler().runAsync(BungeePlugin.this, runnable);
            }

            @Override
            public void runTaskLaterAsync(Runnable runnable, long delay) {
                getProxy().getScheduler().schedule(BungeePlugin.this, runnable, delay, TimeUnit.SECONDS);
            }

            @Override
            public void runTaskLaterAsync(Runnable runnable, Duration delay) {
                getProxy().getScheduler().schedule(BungeePlugin.this, runnable, delay.toMillis(), TimeUnit.MILLISECONDS);
            }

            @Override
            public void runTaskTimerAsync(Runnable runnable, long repeat) {
                // 我也知道这是神人操作，但没办法，只能这样
                getProxy().getScheduler().runAsync(BungeePlugin.this, () -> {
                    while(true){
                        runnable.run();
                        try {
                            wait(repeat * 1000);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                });
            }

            @Override
            public void runTaskTimerAsync(Runnable runnable, Duration repeat) {
                getProxy().getScheduler().runAsync(BungeePlugin.this, () -> {
                    while(true){
                        runnable.run();
                        try {
                            wait(repeat.toMillis());
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                });
            }
        };
    }

    @Override
    public @NonNull Path getDataPath() {
        return getDataFolder().toPath();
    }

    @Override
    public @NonNull List<String> getPlugins() {
        return getProxy().getPluginManager().getPlugins().stream().map(plugin -> plugin.getDescription().getName()).collect(Collectors.toList());
    }

    @Override
    public @NonNull String getPluginVersion() {
        return getDescription().getVersion();
    }

    @Override
    public @Nullable String getPluginVersion(String pluginName) {
        Plugin plugin = getProxy().getPluginManager().getPlugins().stream().filter(p -> p.getDescription().getName().equalsIgnoreCase(pluginName)).findFirst().orElse(null);
        return plugin != null ? plugin.getDescription().getVersion() : null;
    }

    @Override
    public @Nullable Path getPluginFile(String pluginId) {
        Plugin plugin = getProxy().getPluginManager().getPlugins().stream().filter(p -> p.getDescription().getName().equalsIgnoreCase(pluginId)).findFirst().orElse(null);
        return plugin != null ? plugin.getFile().toPath() : null;
    }

    @Override
    public boolean unloadPlugin(String pluginId) {
        Plugin plugin = getProxy().getPluginManager().getPlugins().stream().filter(p -> p.getDescription().getName().equalsIgnoreCase(pluginId)).findFirst().orElse(null);

        if(plugin == null){
            getLogger().log(Level.SEVERE, "插件为空");
            return false;
        }

        plugin.onDisable();
        for (var handler : plugin.getLogger().getHandlers()) handler.close();

        var pluginManager = ProxyServer.getInstance().getPluginManager();
        pluginManager.unregisterCommands(plugin);
        pluginManager.unregisterListeners(plugin);
        ProxyServer.getInstance().getScheduler().cancel(plugin);
        plugin.getExecutorService().shutdownNow();
        Map<String, Plugin> plugins;

        try {
            plugins = FieldAccessor.getValue(net.md_5.bungee.api.plugin.PluginManager.class, "plugins", pluginManager);
        } catch (IllegalAccessException e) {
            getLogger().log(Level.SEVERE, "Failed to access plugins field from plugin manager", e);
            return false;
        }

        if (plugins == null) {
            getLogger().log(Level.SEVERE, "插件列表为空");
            return false;
        }

        plugins.remove(plugin.getDescription().getName());

        var cl = plugin.getClass().getClassLoader();

        if (cl instanceof URLClassLoader) {
            try {
                FieldAccessor.setValue(cl.getClass(), "plugin", cl, null);
                FieldAccessor.setValue(cl.getClass(), "desc", cl, null);

                var allLoaders = FieldAccessor.<Set<?>>getValue(cl.getClass(), "allLoaders", cl);
                if (allLoaders != null) allLoaders.remove(cl);

            } catch (IllegalAccessException ex) {
                getLogger().log(Level.SEVERE, null, ex);
                return false;
            }

            try {
                ((Closeable) cl).close();
            } catch (IOException ex) {
                getLogger().log(Level.SEVERE, null, ex);
                return false;
            }
        }

        System.gc();
        return true;
    }
}
