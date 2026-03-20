package me.dreamvoid.universalpluginupdater.plugin;

import com.google.gson.annotations.SerializedName;

/**
 * URL渠道的配置信息
 */
public class UrlChannelInfo {
    @SerializedName("url")
    private String url;

    public UrlChannelInfo(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
