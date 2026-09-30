package me.dreamvoid.universalpluginupdater.bukkit.update;

import me.dreamvoid.universalpluginupdater.platform.Platform;
import me.dreamvoid.universalpluginupdater.update.AbstractPluginUpdate;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public class BukkitPluginUpdate extends AbstractPluginUpdate {
    private String newVersion;

    public BukkitPluginUpdate(String pluginId, Platform platform) {
        super(pluginId.toLowerCase(), platform);
    }

    @Override
    public boolean checkUpdate() {
        this.newVersion = "2.0";
        return true;
    }

    @Override
    @Nullable
    public Path download() {
        return null;
    }

    @Override
    public String getVersion() {
        return newVersion;
    }
}
