package me.dreamvoid.universalpluginupdater.objects.channel.info;

import com.google.gson.annotations.SerializedName;

/**
 * URL渠道的配置信息
 */
public record UrlChannelInfo(
        @SerializedName("url") String url
) { }
