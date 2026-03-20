package me.dreamvoid.universalpluginupdater.plugin;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * 插件的完整更新配置
 * 对应于channels文件夹下的单个插件配置文件
 */
public class PluginUpdateConfig {
    @SerializedName("channels")
    private List<ChannelConfig> channels;

    @SerializedName("selectedChannel")
    private String selectedChannel;

    public PluginUpdateConfig(List<ChannelConfig> channels) {
        this.channels = channels;
    }

    public PluginUpdateConfig(List<ChannelConfig> channels, String selectedChannel) {
        this.channels = channels;
        this.selectedChannel = selectedChannel;
    }

    public List<ChannelConfig> getChannels() {
        return channels;
    }

    public void setChannels(List<ChannelConfig> channels) {
        this.channels = channels;
    }

    public String getSelectedChannel() {
        return selectedChannel;
    }

    public void setSelectedChannel(String selectedChannel) {
        this.selectedChannel = selectedChannel;
    }
}
