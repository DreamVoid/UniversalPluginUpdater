package me.dreamvoid.universalpluginupdater.objects.update.modrinth;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Modrinth API返回的版本信息
 */
public record ModrinthVersion (
    @SerializedName("id") String id,
    @SerializedName("project_id") String projectId,
    @SerializedName("name") String name,
    @SerializedName("version_number") String versionNumber,
    @SerializedName("changelog") String changelog,
    @SerializedName("date_published") String datePublished,
    @SerializedName("version_type") String versionType,
    @SerializedName("status") String status,
    @SerializedName("files") List<ModrinthFile> files,
    @SerializedName("game_versions") List<String> gameVersions,
    @SerializedName("loaders") List<String> loaders
) {

    /**
     * 获取主要的（primary=true）文件
     * @return 主要文件，如果没有找到返回第一个文件，如果没有文件返回null
     */
    public ModrinthFile getPrimaryFile() {
        if (files == null || files.isEmpty()) {
            return null;
        }

        for (ModrinthFile file : files) {
            if (file.primary()) {
                return file;
            }
        }

        // 如果没有标记为primary的文件，返回第一个文件
        return files.get(0);
    }
}
