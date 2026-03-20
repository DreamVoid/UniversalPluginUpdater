package me.dreamvoid.universalpluginupdater.plugin;

import com.google.gson.annotations.SerializedName;

/**
 * Modrinth渠道的配置信息
 */
public class ModrinthChannelInfo {
    @SerializedName("projectId")
    private String projectId;

    public ModrinthChannelInfo(String projectId) {
        this.projectId = projectId;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }
}
