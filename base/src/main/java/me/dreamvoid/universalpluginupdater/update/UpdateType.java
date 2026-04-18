package me.dreamvoid.universalpluginupdater.update;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

public enum UpdateType {
    URL("url"),
    Plugin("plugin"),
    GitHub("github"),
    Modrinth("modrinth"),
    Hangar("hangar"),
    SpigotMC("spigotmc");

    @Getter
    private final String identifier;

    UpdateType(String identifier) {
        this.identifier = identifier;
    }

    @Nullable
    public static UpdateType fromIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return null;
        }

        for (UpdateType type : values()) {
            if (type.identifier.equalsIgnoreCase(identifier) || type.name().equalsIgnoreCase(identifier)) {
                return type;
            }
        }
        return null;
    }
}
