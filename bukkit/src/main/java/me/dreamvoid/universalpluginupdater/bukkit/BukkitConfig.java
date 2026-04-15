package me.dreamvoid.universalpluginupdater.bukkit;

import me.dreamvoid.universalpluginupdater.Config;

import java.util.List;
import java.util.logging.Logger;

public class BukkitConfig extends Config {
    private final BukkitPlugin plugin;

    BukkitConfig(BukkitPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
    }

    @Override
    public Logger getLogger() {
        return plugin.getLogger();
    }

    @Override
    public String getString(String path, String def) {
        return plugin.getConfig().getString(path, def);
    }

    @Override
    public int getInt(String path, int def) {
        return plugin.getConfig().getInt(path, def);
    }

    @Override
    public long getLong(String path, long def) {
        return plugin.getConfig().getLong(path, def);
    }

    @Override
    public boolean getBoolean(String path, boolean def) {
        return plugin.getConfig().getBoolean(path, def);
    }

    @Override
    public List<String> getStringList(String path) {
        return plugin.getConfig().getStringList(path);
    }
}
