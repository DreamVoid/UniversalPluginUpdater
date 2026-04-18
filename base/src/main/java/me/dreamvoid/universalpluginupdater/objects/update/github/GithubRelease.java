package me.dreamvoid.universalpluginupdater.objects.update.github;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * GitHub API返回的Release信息
 */
public record GithubRelease(
        @SerializedName("url") String url,
        @SerializedName("assets_url") String assetsUrl,
        @SerializedName("html_url") String htmlUrl,
        @SerializedName("id") long id,
        @SerializedName("tag_name") String tagName,
        @SerializedName("name") String name,
        @SerializedName("body") String body,
        @SerializedName("assets") List<GithubAsset> assets
) { }
