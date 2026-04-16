package me.dreamvoid.universalpluginupdater.objects.channel.info;

import com.google.gson.annotations.SerializedName;

/**
 * Modrinth渠道的配置信息
 */
public record ModrinthChannelInfo(
        @SerializedName("projectId") String projectId,
        @SerializedName("featured") boolean featured
) { }
