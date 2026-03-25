package me.dreamvoid.universalpluginupdater.objects.update.modrinth;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

/**
 * Modrinth API返回的文件信息
 */
public record ModrinthFile (
    @SerializedName("id") String id,
    @SerializedName("url") String url,
    @SerializedName("filename") String filename,
    @SerializedName("primary") boolean primary,
    @SerializedName("size") long size,
    @SerializedName("hashes") Map<String, String> hashes  // 哈希值映射，如 {"sha1": "...", "sha512": "..."}
) {
    /**
     * 获取文件哈希值
     * 优先级：sha256 > sha1 > sha512 > md5
     * @return 哈希值，如果不存在返回null
     */
    public String getHash() {
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
    public String getHashAlgorithm() {
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
