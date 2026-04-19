package me.dreamvoid.universalpluginupdater.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyReloadEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import me.dreamvoid.universalpluginupdater.BuildConstants;
import me.dreamvoid.universalpluginupdater.Config;
import me.dreamvoid.universalpluginupdater.LifeCycle;
import me.dreamvoid.universalpluginupdater.command.CommandContext;
import me.dreamvoid.universalpluginupdater.command.CommandHandler;
import me.dreamvoid.universalpluginupdater.platform.Platform;
import me.dreamvoid.universalpluginupdater.platform.Scheduler;
import me.dreamvoid.universalpluginupdater.upgrade.UpgradeStrategyRegistry;
import me.dreamvoid.universalpluginupdater.velocity.upgrade.VelocityUpgradeStrategy;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static me.dreamvoid.universalpluginupdater.service.LanguageManager.tr;

@Plugin(
        id = "universalpluginupdater",
        name = "UniversalPluginUpdater",
        version = BuildConstants.VERSION,
        description = "A plugin that updates other plugins on the server.",
        url = "https://www.mineblock.cc",
        authors = {"DreamVoid"}
)
public class VelocityPlugin implements Platform {
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private final java.util.logging.Logger julLogger;
    private final LifeCycle lifeCycle;
    private final CommandHandler commandHandler;

    @Inject
    public VelocityPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        julLogger = LoggerConverter.fromSLF4JLogger("UniversalPluginUpdater", logger);
        lifeCycle =  new LifeCycle(this);
        commandHandler = new  CommandHandler(this);

        lifeCycle.startUp(julLogger);
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        lifeCycle.preLoad();

        UpgradeStrategyRegistry.instance().registerStrategy(new VelocityUpgradeStrategy(this));

        lifeCycle.postLoad();

        logger.info(tr("message.lifecycle.velocity.postload.register-command"));
        CommandManager commandManager = server.getCommandManager();
        CommandMeta commandMeta = commandManager.metaBuilder("universalpluginupdater")
                .aliases("upu")
                .plugin(this)
                .build();

        commandManager.register(commandMeta, new SimpleCommand() {
            @Override
            public void execute(Invocation invocation) {
                commandHandler.execute(new CommandContext(new VelocityCommandSender(invocation.source()), invocation.arguments()));
            }

            @Override
            public List<String> suggest(Invocation invocation) {
                return commandHandler.suggest(new CommandContext(new VelocityCommandSender(invocation.source()), invocation.arguments()));
            }

            @Override
            public boolean hasPermission(Invocation invocation) {
                return invocation.source().hasPermission("universalpluginupdater.command");
            }
        });
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        lifeCycle.unload();

        if (UpgradeStrategyRegistry.instance().getActiveStrategy() instanceof VelocityUpgradeStrategy strategy) {
            strategy.launch();
        }
    }

    @Subscribe
    public void onProxyReload(ProxyReloadEvent event) {
        lifeCycle.reload();
    }

    @Override
    public @NonNull String getPlatformName() {
        return "Velocity";
    }

    @Override
    public @Nullable List<String> getGameVersions() {
        return null;
    }

    @Override
    public @NonNull List<String> getLoaders() {
        return List.of("velocity");
    }

    @Override
    public java.util.logging.@NonNull Logger getPlatformLogger() {
        return julLogger;
    }

    @Override
    public @NonNull Config getPlatformConfig() {
        return new VelocityConfig(this);
    }

    @Override
    public Scheduler getScheduler() {
        return new Scheduler() {
            @Override
            public void runTaskAsync(Runnable runnable) {
                server.getScheduler().buildTask(VelocityPlugin.this, runnable).schedule();
            }

            @Override
            public void runTaskLaterAsync(Runnable runnable, long delay) {
                server.getScheduler().buildTask(VelocityPlugin.this, runnable).delay(delay, TimeUnit.SECONDS).schedule();
            }

            @Override
            public void runTaskLaterAsync(Runnable runnable, Duration delay) {
                server.getScheduler().buildTask(VelocityPlugin.this, runnable).delay(delay).schedule();
            }

            @Override
            public void runTaskTimerAsync(Runnable runnable, long repeat) {
                server.getScheduler().buildTask(VelocityPlugin.this, runnable).repeat(repeat, TimeUnit.SECONDS).schedule();
            }

            @Override
            public void runTaskTimerAsync(Runnable runnable, Duration repeat) {
                server.getScheduler().buildTask(VelocityPlugin.this, runnable).repeat(repeat).schedule();
            }
        };
    }

    @Override
    public @NonNull Path getDataPath() {
        return dataDirectory;
    }

    @Override
    public @NonNull List<String> getPlugins() {
        return server.getPluginManager().getPlugins().stream().map(p -> p.getDescription().getId()).collect(Collectors.toList());
    }

    @Override
    public @NonNull String getPluginVersion() {
        return BuildConstants.VERSION;
    }

    @Override
    public @Nullable String getPluginVersion(String pluginName) {
        return server.getPluginManager().getPlugin(pluginName)
                .flatMap(p -> p.getDescription().getVersion())
                .orElse(null);
    }

    @Override
    public @Nullable Path getPluginFile(String pluginId) {
        return server.getPluginManager().getPlugin(pluginId).flatMap(p -> p.getDescription().getSource()).orElse(null);
    }

    @Override
    public boolean unloadPlugin(String pluginId) {
        logger.warn("Velocity 不支持卸载插件，建议使用 velocity 升级策略替代 native 策略。");
        return false;
    }
}
