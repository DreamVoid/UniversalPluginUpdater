package me.dreamvoid.universalpluginupdater.bukkit;

import me.dreamvoid.universalpluginupdater.Config;
import me.dreamvoid.universalpluginupdater.LifeCycle;
import me.dreamvoid.universalpluginupdater.bukkit.upgrade.BukkitUpgradeStrategy;
import me.dreamvoid.universalpluginupdater.command.CommandContext;
import me.dreamvoid.universalpluginupdater.command.CommandHandler;
import me.dreamvoid.universalpluginupdater.platform.Platform;
import me.dreamvoid.universalpluginupdater.platform.Scheduler;
import me.dreamvoid.universalpluginupdater.reflection.ClassAccessor;
import me.dreamvoid.universalpluginupdater.reflection.FieldAccessor;
import me.dreamvoid.universalpluginupdater.upgrade.UpgradeStrategyRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Level;
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

        //UpdateManager.registerUpdateInstance(new BukkitPluginUpdate(getName()));

        // 注册 Bukkit 特定的升级策略
        UpgradeStrategyRegistry.instance().registerStrategy(new BukkitUpgradeStrategy(getLogger()));
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
        PluginManager pluginManager = getServer().getPluginManager();
        Plugin plugin = pluginManager.getPlugin(pluginId);

        if(plugin == null) return false;

        // 第一步：关闭插件
        pluginManager.disablePlugin(plugin);

        if(this.getClass() == BukkitPlugin.class){
            try {
                // 第二步：获取插件残留信息（仅Bukkit需要，Paper自动处理）
                List<Plugin> plugins = FieldAccessor.getValue(pluginManager.getClass(), "plugins", pluginManager);
                Map<String, Plugin> names = FieldAccessor.getValue(pluginManager.getClass(), "lookupNames", pluginManager);

                String craftBukkitPrefix = Bukkit.getServer().getClass().getPackage().getName();
                Class<?> craftServerClass = ClassAccessor.getClass(craftBukkitPrefix + ".CraftServer");
                SimpleCommandMap commandMap = FieldAccessor.getValue(craftServerClass, "commandMap", Bukkit.getServer());//FieldAccessor.<SimpleCommandMap>getValue(pluginManager.getClass(), "commandMap", pluginManager);


                // 第三步：清理Listener
                try {
                    Map<Event, SortedSet<RegisteredListener>> listeners = FieldAccessor.getValue(pluginManager.getClass(), "listeners", pluginManager);
                    if (listeners != null) listeners.values().forEach(set -> set.removeIf(value -> value.getPlugin() == plugin));
                } catch (Exception ignored) {
                }

                // 第四步：清理命令
                Map<String, Command> knownCommands = FieldAccessor.getValue(SimpleCommandMap.class, "knownCommands", commandMap);
                CommandMapWrap<Command> commands = new CommandMapWrap<>(knownCommands, TargetCommand::new); //FieldAccessor.<Map<String, org.bukkit.command.Command>>getValue(SimpleCommandMap.class, "knownCommands", commandMap);
                if (commandMap != null) {
                    for (Map.Entry<String, TargetCommand> entry : commands.asMap().entrySet())
                        if (entry.getValue().command() instanceof PluginCommand command) {
                            if (command.getPlugin() == plugin) {
                                command.unregister(commandMap);
                                commands.remove(entry.getKey());
                            }
                        } else try {
                            TargetCommand command = entry.getValue();
                            Command handle = command.command();

                            String pluginField = FieldAccessor.getFirstFieldName(handle.getClass(), Plugin.class);

                            try {
                                Plugin owningPlugin = FieldAccessor.getValue(handle.getClass(), pluginField, handle);
                                if (owningPlugin != null && owningPlugin.getName().equalsIgnoreCase(plugin.getName())) {
                                    handle.unregister(commandMap);
                                    commands.remove(entry.getKey());
                                }
                            } catch (IllegalAccessException exception) {
                                getLogger().log(Level.SEVERE, "Failed to unregister command for plugin: " + plugin.getName(), exception);
                            }
                        } catch (IllegalStateException exception) {
                            if (exception.getMessage().equalsIgnoreCase("zip file closed")) {
                                Command handle = entry.getValue().command();
                                handle.unregister(commandMap);
                                commands.remove(entry.getKey());
                            }
                        }
                }

                syncCommandsRunnable.run();
                //Bukkit.getOnlinePlayers().forEach(Player::updateCommands);

                // 第五步：从插件列表移除插件
                if (plugins != null) plugins.removeIf(otherPlugin -> otherPlugin.getName().equalsIgnoreCase(plugin.getName()));
                if (names != null) names.remove(plugin.getName());

                // 第六步：关闭ClassLoader
                ClassLoader classLoader = plugin.getClass().getClassLoader();
                if (classLoader instanceof URLClassLoader) {
                    try {
                        FieldAccessor.setValue("plugin", classLoader, null);
                        FieldAccessor.setValue("pluginInit", classLoader, null);
                    } catch (SecurityException | IllegalArgumentException | IllegalAccessException exception) {
                        Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error removing class load from plugin", exception);
                    }

                    try {
                        ((Closeable) classLoader).close();
                    } catch (IOException exception) {
                        Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error closing plugin classloader", exception);
                    }
                }

                // 第七步：gc
                System.gc();
                return true;
            } catch (Exception e) {
                return false;
            }
        } else {
            return true;
        }
    }

    private static Runnable syncCommandsRunnable = () -> {};

    static {
        try {
            // Get the server class and syncCommands method
            Class<? extends Server> serverClass = Bukkit.getServer().getClass();
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodHandle syncCommandsHandle = lookup.findVirtual(serverClass, "syncCommands", MethodType.methodType(void.class));

            // Create a lambda using LambdaMetaFactory
            syncCommandsRunnable = (Runnable) LambdaMetafactory.metafactory(
                    lookup,
                    "run",
                    MethodType.methodType(Runnable.class, serverClass),
                    MethodType.methodType(void.class),
                    syncCommandsHandle,
                    MethodType.methodType(void.class)
            ).getTarget().invoke(Bukkit.getServer());

        } catch (Throwable ignored) {
        }
    }

    private record TargetCommand(Command command) { }

    private static class CommandMapWrap<T> {
        private final Map<String, TargetCommand> commands = new HashMap<>();
        private final Map<String, T> knownCommands;

        public CommandMapWrap(Map<String, T> knownCommands, Function<T, ? extends TargetCommand> pluginCommandFactory) {
            this.knownCommands = knownCommands;

            // Note: Never use `forEach` here. The implementation of `forEach` seems to be a no-op
            for (Map.Entry<String, T> entry : knownCommands.entrySet()) commands.put(entry.getKey(), pluginCommandFactory.apply(entry.getValue()));
        }

        public void remove(String key) {
            knownCommands.remove(key);
            commands.remove(key);
        }

        public Map<String, TargetCommand> asMap() {
            return Map.copyOf(commands);
        }
    }
}
