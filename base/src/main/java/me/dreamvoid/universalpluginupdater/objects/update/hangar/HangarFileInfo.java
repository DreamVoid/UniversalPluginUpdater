package me.dreamvoid.universalpluginupdater.objects.update.hangar;

public record HangarFileInfo(
        String name,
        Long sizeBytes,
        String sha256Hash
) {
}
