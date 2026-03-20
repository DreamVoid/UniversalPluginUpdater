package me.dreamvoid.universalpluginupdater.update;

public enum UpdateType {
    Custom("custom"),
    Plugin("plugin"),
    GitHub("github"),
    Modrinth("modrinth"),
    Hangar("hangar"),
    SpigotMC("spigotmc"),
    Maven("maven");

    UpdateType(String s) {}
}
