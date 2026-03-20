package me.dreamvoid.universalpluginupdater.update;

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

    public String getIdentifier() {
        return identifier;
    }
}
