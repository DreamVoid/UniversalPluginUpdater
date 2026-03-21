package me.dreamvoid.universalpluginupdater.objects.channel;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;

/**
 * URL渠道的配置信息
 */
@Setter
@Getter
public final class UrlChannelInfo {
    @SerializedName("url")
    private String url;

    public UrlChannelInfo(String url) {
        this.url = url;
    }
}
