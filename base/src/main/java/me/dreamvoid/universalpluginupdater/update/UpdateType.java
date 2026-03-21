package me.dreamvoid.universalpluginupdater.update;

import lombok.Getter;

@Getter
public enum UpdateType {
    URL("url"),
    Plugin("plugin"),
    GitHub("github"),
    Modrinth("modrinth"),
    Hangar("hangar"),
    SpigotMC("spigotmc"),
    Maven("maven");

    private final String identifier;
    UpdateType(String identifier) {
        this.identifier = identifier;
    }
}
