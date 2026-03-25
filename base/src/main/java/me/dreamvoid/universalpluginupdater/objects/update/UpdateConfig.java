package me.dreamvoid.universalpluginupdater.objects.update;

import com.google.gson.annotations.SerializedName;
import me.dreamvoid.universalpluginupdater.objects.ChannelConfig;

import java.util.List;

/**
 * 插件的完整更新配置
 * 对应于channels文件夹下的单个插件配置文件
 */
public record UpdateConfig(
    @SerializedName("channels") List<ChannelConfig> channels,
    @SerializedName("selectedChannel") String selectedChannel
) { }
