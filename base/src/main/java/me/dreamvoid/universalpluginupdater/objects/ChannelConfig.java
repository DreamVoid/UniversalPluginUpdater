package me.dreamvoid.universalpluginupdater.objects;

import com.google.gson.annotations.SerializedName;

/**
 * 单个更新渠道的配置
 */
public record ChannelConfig(
        @SerializedName("type") String type,
        @SerializedName("config") Object config
) { }
