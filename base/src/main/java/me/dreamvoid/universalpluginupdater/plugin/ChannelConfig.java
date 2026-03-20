package me.dreamvoid.universalpluginupdater.plugin;

import com.google.gson.annotations.SerializedName;

/**
 * 单个更新渠道的配置
 */
public class ChannelConfig {
    @SerializedName("type")
    private String type;

    @SerializedName("config")
    private Object config;

    public ChannelConfig(String type, Object config) {
        this.type = type;
        this.config = config;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Object getConfig() {
        return config;
    }

    public void setConfig(Object config) {
        this.config = config;
    }
}
