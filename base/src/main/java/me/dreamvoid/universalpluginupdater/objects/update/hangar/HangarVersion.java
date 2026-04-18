package me.dreamvoid.universalpluginupdater.objects.update.hangar;

import java.util.Map;

public record HangarVersion(
        String name,
        Map<String, HangarPlatformDownload> downloads
) {
}
