package me.dreamvoid.universalpluginupdater.update.modrinth;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Modrinth API返回的版本信息
 */
public class ModrinthVersion {
    @SerializedName("id")
    private String id;

    @SerializedName("project_id")
    private String projectId;

    @SerializedName("name")
    private String name;

    @SerializedName("version_number")
    private String versionNumber;

    @SerializedName("changelog")
    private String changelog;

    @SerializedName("date_published")
    private String datePublished;

    @SerializedName("version_type")
    private String versionType;

    @SerializedName("status")
    private String status;

    @SerializedName("files")
    private List<ModrinthFile> files;

    @SerializedName("game_versions")
    private List<String> gameVersions;

    @SerializedName("loaders")
    private List<String> loaders;

    public String getId() {
        return id;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getName() {
        return name;
    }

    public String getVersionNumber() {
        return versionNumber;
    }

    public String getChangelog() {
        return changelog;
    }

    public String getDatePublished() {
        return datePublished;
    }

    public String getVersionType() {
        return versionType;
    }

    public String getStatus() {
        return status;
    }

    public List<ModrinthFile> getFiles() {
        return files;
    }

    public List<String> getGameVersions() {
        return gameVersions;
    }

    public List<String> getLoaders() {
        return loaders;
    }

    /**
     * 获取主要的（primary=true）文件
     * @return 主要文件，如果没有找到返回第一个文件，如果没有文件返回null
     */
    public ModrinthFile getPrimaryFile() {
        if (files == null || files.isEmpty()) {
            return null;
        }

        for (ModrinthFile file : files) {
            if (file.isPrimary()) {
                return file;
            }
        }

        // 如果没有标记为primary的文件，返回第一个文件
        return files.get(0);
    }
}
