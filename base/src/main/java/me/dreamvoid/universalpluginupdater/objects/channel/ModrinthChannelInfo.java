package me.dreamvoid.universalpluginupdater.objects.channel;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;

/**
 * Modrinth渠道的配置信息
 */
@Setter
@Getter
public final class ModrinthChannelInfo {
    @SerializedName("projectId")
    private String projectId;

    public ModrinthChannelInfo(String projectId) {
        this.projectId = projectId;
    }
}
