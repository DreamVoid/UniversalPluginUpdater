package me.dreamvoid.universalpluginupdater.paper;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.dreamvoid.universalpluginupdater.bukkit.BukkitPlugin;
import me.dreamvoid.universalpluginupdater.command.CommandContext;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static me.dreamvoid.universalpluginupdater.service.LanguageService.tr;

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

        getLogger().info(tr("message.lifecycle.paper.postload.register-command"));
        registerCommand("universalpluginupdater", "UniversalPluginUpdater 主命令", Collections.singleton("upu"), new BasicCommand() {
            @Override
            public void execute(@NonNull CommandSourceStack stack, String @NonNull [] args) {
                PaperCommandSender sender = new PaperCommandSender(stack.getSender());
                CommandContext context = new CommandContext(sender, args);
                commandHandler.execute(context);
            }

            @Override
            public String permission() {
                return "universalpluginupdater.command";
            }

            @Override
            public @NonNull Collection<String> suggest(@NonNull CommandSourceStack stack, String @NonNull [] args) {
                PaperCommandSender sender = new PaperCommandSender(stack.getSender());
                CommandContext context = new CommandContext(sender, args);
                return commandHandler.suggest(context);
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

    @Override
    public void runTaskAsync(Runnable runnable) {
        getServer().getAsyncScheduler().runNow(this, task -> runnable.run());
    }
}
