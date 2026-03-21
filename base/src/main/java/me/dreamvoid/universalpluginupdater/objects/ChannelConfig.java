package me.dreamvoid.universalpluginupdater.objects;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;

/**
 * 单个更新渠道的配置
 */
@Getter
@Setter
public final class ChannelConfig {
    @SerializedName("type")
    private String type;

    @SerializedName("config")
    private Object config;

    public ChannelConfig(String type, Object config) {
        this.type = type;
        this.config = config;
    }
}
