package me.dreamvoid.universalpluginupdater.objects.update.github;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;

/**
 * GitHub API返回的Asset信息
 */
public record GithubAsset(
        @SerializedName("url") String url,
        @SerializedName("id") long id,
        @SerializedName("name") String name,
        @SerializedName("content_type") String contentType,
        @SerializedName("digest") String digest,
        @SerializedName("size") long size,
        @SerializedName("browser_download_url") String browserDownloadUrl
) {
        @Nullable
        public String hashAlgorithm() {
                if (digest == null) return null;
                int separator = digest.indexOf(':');
                if (separator <= 0) return null;
                return digest.substring(0, separator);
        }

        @Nullable
        public String hashValue() {
                if (digest == null) return null;
                int separator = digest.indexOf(':');
                if (separator <= 0 || separator >= digest.length() - 1) return null;
                return digest.substring(separator + 1);
        }
}
