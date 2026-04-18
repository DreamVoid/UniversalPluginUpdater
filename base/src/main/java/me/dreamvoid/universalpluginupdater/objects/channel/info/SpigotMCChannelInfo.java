package me.dreamvoid.universalpluginupdater.objects.channel.info;

import com.google.gson.annotations.SerializedName;

public record SpigotMCChannelInfo(
        Object resource,
        @SerializedName("proxy-download") boolean proxyDownload
) {
}
