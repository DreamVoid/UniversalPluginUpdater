package me.dreamvoid.universalpluginupdater.objects.channel.info;

import com.google.gson.annotations.SerializedName;

/**
 * GitHub渠道的配置信息
 */
public record GitHubChannelInfo(
        @SerializedName("repository") String repository,
        @SerializedName("auth") String auth,
        @SerializedName("accept") String accept,
        @SerializedName("filter") String filter
) { }
