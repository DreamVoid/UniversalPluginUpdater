package me.dreamvoid.universalpluginupdater.bungee;

import me.dreamvoid.universalpluginupdater.Config;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

public class BungeeConfig extends Config {
    private final BungeePlugin plugin;
    private Configuration config;

    BungeeConfig(BungeePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void loadConfig() throws IOException {
        saveDefaultConfig();
        config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(new File(plugin.getDataFolder(), "config.yml"));
    }

    @Override
    public Logger getLogger() {
        return plugin.getLogger();
    }

    @Override
    public String getString(String path, String def) {
        return config.getString(path, def);
    }

    @Override
    public int getInt(String path, int def) {
        return config.getInt(path, def);
    }

    @Override
    public long getLong(String path, long def) {
        return config.getLong(path, def);
    }

    @Override
    public boolean getBoolean(String path, boolean def) {
        return config.getBoolean(path, def);
    }

    @Override
    public List<String> getStringList(String path) {
        return config.getStringList(path);
    }

    private void saveDefaultConfig() throws IOException{
        Path configPath = Files.createDirectories(plugin.getDataPath()).resolve("config.yml");
        if(!Files.exists(configPath)){
            try(InputStream in = plugin.getResourceAsStream("config.yml")){
                Files.copy(in, configPath);
            }
        }
    }
}
