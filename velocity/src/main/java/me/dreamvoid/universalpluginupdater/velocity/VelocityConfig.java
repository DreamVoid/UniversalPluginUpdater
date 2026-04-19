package me.dreamvoid.universalpluginupdater.velocity;

import me.dreamvoid.universalpluginupdater.Config;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

public class VelocityConfig extends Config {
    private final VelocityPlugin plugin;
    private Map<String, Object> config = Collections.emptyMap();

    public VelocityConfig(VelocityPlugin plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void loadConfig() throws IOException {
        saveDefaultConfig();
        Path configPath = plugin.getDataPath().resolve("config.yml");

        try (InputStream in = Files.newInputStream(configPath)) {
            Object loaded = new Yaml().load(in);
            if (loaded instanceof Map<?, ?> map) {
                this.config = (Map<String, Object>) map;
            } else {
                this.config = Collections.emptyMap();
            }
        }

    }

    @Override
    public Logger getLogger() {
        return plugin.getPlatformLogger();
    }

    @Override
    public String getString(String path, String def) {
        Object value = getPathValue(path);
        return value != null ? String.valueOf(value) : def;
    }

    @Override
    public int getInt(String path, int def) {
        Object value = getPathValue(path);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value != null ? Integer.parseInt(String.valueOf(value)) : def;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @Override
    public long getLong(String path, long def) {
        Object value = getPathValue(path);
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value != null ? Long.parseLong(String.valueOf(value)) : def;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @Override
    public boolean getBoolean(String path, boolean def) {
        Object value = getPathValue(path);
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return def;
    }

    @Override
    public List<String> getStringList(String path) {
        Object value = getPathValue(path);
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
            return result;
        }
        return List.of();
    }

    @Override
    public Object getObject(String path) {
        return getPathValue(path);
    }

    private void saveDefaultConfig() throws IOException {
        Path configPath = Files.createDirectories(plugin.getDataPath()).resolve("config.yml");
        if (Files.exists(configPath)) {
            return;
        }

        try (InputStream in = plugin.getClass().getClassLoader().getResourceAsStream("config.yml")) {
            if (in == null) {
                throw new IOException("无法从类路径加载默认配置文件 config.yml");
            }
            Files.copy(in, configPath);
        }
    }

    private Object getPathValue(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }

        Object current = config;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(part);
            if (Objects.isNull(current)) {
                return null;
            }
        }
        return current;
    }
}
