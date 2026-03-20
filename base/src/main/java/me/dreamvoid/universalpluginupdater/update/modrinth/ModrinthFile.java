package me.dreamvoid.universalpluginupdater.update.modrinth;

import com.google.gson.annotations.SerializedName;

/**
 * Modrinth API返回的文件信息
 */
public class ModrinthFile {
    @SerializedName("id")
    private String id;

    @SerializedName("url")
    private String url;

    @SerializedName("filename")
    private String filename;

    @SerializedName("primary")
    private boolean primary;

    @SerializedName("size")
    private long size;

    public String getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public String getFilename() {
        return filename;
    }

    public boolean isPrimary() {
        return primary;
    }

    public long getSize() {
        return size;
    }
}
