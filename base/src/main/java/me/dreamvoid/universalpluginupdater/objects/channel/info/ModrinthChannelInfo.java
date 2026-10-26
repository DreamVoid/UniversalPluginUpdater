package me.dreamvoid.universalpluginupdater.objects.channel.info;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Modrinth渠道的配置信息
 */
public record ModrinthChannelInfo(
        @SerializedName("projectId") String projectId,
        @SerializedName("featured") boolean featured,
        @SerializedName("version-key") String versionKey,
        @SerializedName("version-regex") String versionRegex,
        @SerializedName("version-type") List<String> versionTypes
) { }
