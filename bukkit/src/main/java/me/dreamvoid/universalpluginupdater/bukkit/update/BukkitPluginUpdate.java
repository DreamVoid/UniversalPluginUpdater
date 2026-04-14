package me.dreamvoid.universalpluginupdater.bukkit.update;

import me.dreamvoid.universalpluginupdater.update.AbstractUpdate;

public class BukkitPluginUpdate extends AbstractUpdate {
    private final String pluginId;
    private String newVersion;

    public BukkitPluginUpdate(String pluginId) {
        this.pluginId = pluginId.toLowerCase();
    }

    @Override
    public boolean update() {
        this.newVersion = "2.0";
        return true;
    }

    @Override
    public boolean download() {
        return false;
    }

    @Override
    public boolean upgrade(boolean now) {
        return download();
    }

    @Override
    public String getVersion() {
        return newVersion;
    }

    @Override
    public String getPluginId() {
        return pluginId;
    }
}
