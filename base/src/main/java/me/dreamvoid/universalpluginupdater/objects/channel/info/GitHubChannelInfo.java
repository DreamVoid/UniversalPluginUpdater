package me.dreamvoid.universalpluginupdater.objects.channel.info;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * GitHub渠道的配置信息
 */
public record GitHubChannelInfo(
        @SerializedName("repository") String repository,
        @SerializedName("auth") String auth,
        @SerializedName("accept") List<String> accept,
        @SerializedName("filter") String filter,
        @SerializedName("version-key") String versionKey,
        @SerializedName("version-regex") String versionRegex
) { }
