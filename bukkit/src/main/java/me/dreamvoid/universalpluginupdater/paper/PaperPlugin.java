package me.dreamvoid.universalpluginupdater.paper;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.dreamvoid.universalpluginupdater.bukkit.BukkitPlugin;
import me.dreamvoid.universalpluginupdater.command.CommandContext;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Paper平台的插件主类
 * 继承BukkitPlugin并添加Paper特定的功能
 * 使用Paper的LifecycleManager通过代码注册命令
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
                CommandContext context = new CommandContext("universalpluginupdater", args, sender);
                commandHandler.handleCommand(context);
            }

            @Override
            public String permission() {
                return "universalpluginupdater.command";
            }

            @Override
            public @NonNull Collection<String> suggest(@NonNull CommandSourceStack stack, String @NonNull [] args) {
                PaperCommandSender sender = new PaperCommandSender(stack.getSender());
                CommandContext context = new CommandContext("universalpluginupdater", args, sender);
                String[] completions = commandHandler.getTabCompletion(context);
                return List.of(completions);
            }
        });
    }

    // 平台实现接口

    @Override
    public List<String> getLoaders() {
        return Arrays.asList("bukkit", "paper");
    }
}
