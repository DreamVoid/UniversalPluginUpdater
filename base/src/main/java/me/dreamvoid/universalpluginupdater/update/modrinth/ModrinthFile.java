package me.dreamvoid.universalpluginupdater.update.modrinth;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

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

    @SerializedName("hashes")
    private Map<String, String> hashes;  // 哈希值映射，如 {"sha1": "...", "sha512": "..."}

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

    public Map<String, String> getHashes() {
        return hashes;
    }

    /**
     * 获取文件哈希值
     * 优先级：sha256 > sha1 > sha512 > md5
     * @return 哈希值，如果不存在返回null
     */
    public String getPreferredHash() {
        if (hashes == null || hashes.isEmpty()) {
            return null;
        }
        if (hashes.containsKey("sha256")) {
            return hashes.get("sha256");
        } else if (hashes.containsKey("sha1")) {
            return hashes.get("sha1");
        } else if (hashes.containsKey("sha512")) {
            return hashes.get("sha512");
        } else if (hashes.containsKey("md5")) {
            return hashes.get("md5");
        }
        return null;
    }

    /**
     * 获取文件哈希算法
     */
    public String getPreferredHashAlgorithm() {
        if (hashes == null || hashes.isEmpty()) {
            return null;
        }
        if (hashes.containsKey("sha256")) {
            return "SHA-256";
        } else if (hashes.containsKey("sha1")) {
            return "SHA-1";
        } else if (hashes.containsKey("sha512")) {
            return "SHA-512";
        } else if (hashes.containsKey("md5")) {
            return "MD5";
        }
        return null;
    }
}
