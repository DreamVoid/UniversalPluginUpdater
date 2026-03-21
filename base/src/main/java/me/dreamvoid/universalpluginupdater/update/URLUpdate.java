package me.dreamvoid.universalpluginupdater.update;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import me.dreamvoid.universalpluginupdater.Utils;

import java.net.URI;
import java.net.URL;

public class URLUpdate extends AbstractUpdate {
    private final String updateUrl;
    private UpdateInfo updateInfo;
    private String lastModified;
    private static final Gson gson = new Gson();

    public URLUpdate(String updateUrl) {
        this.updateType = UpdateType.URL;
        this.updateUrl = updateUrl;
    }

    /**
     * 从远程URL获取更新信息
     * 使用HTTP缓存机制减少网络请求
     * 返回的JSON格式应为：
     * {
     *     "version": "string（必选）",
     *     "versionCode": "int（可选）",
     *     "downloadUrl": "url（必选）",
     *     "changelog": "url（可选）"
     * }
     */
    private boolean fetchUpdateInfo() {
        try {
            Utils.Http.Response response = Utils.Http.get(updateUrl, lastModified);

            if (response.isNotModified()) {
                // 返回304 Not Modified，使用缓存
                if (updateInfo == null) {
                    return false;
                }
                this.lastModified = response.lastModified;
                return true;
            }

            if (response.isSuccess()) {
                String jsonResponse = response.content;
                if (jsonResponse == null) {
                    return false;
                }

                this.updateInfo = gson.fromJson(jsonResponse, UpdateInfo.class);
                this.lastModified = response.lastModified;

                if (updateInfo == null || updateInfo.version == null || updateInfo.downloadUrl == null) {
                    this.updateInfo = null;
                    return false;
                }

                return true;
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getVersion() {
        // 每次都发起网络请求以检查更新（可能得到304缓存命中）
        fetchUpdateInfo();
        return updateInfo != null ? updateInfo.version : null;
    }

    /**
     * 获取版本代码（若存在）
     */
    public Integer getVersionCode() {
        // 每次都发起网络请求以检查更新（可能得到304缓存命中）
        fetchUpdateInfo();
        return updateInfo != null ? updateInfo.versionCode : null;
    }

    @Override
    public URL getDownloadUrl() {
        // 每次都发起网络请求以检查更新（可能得到304缓存命中）
        fetchUpdateInfo();
        if (updateInfo != null && updateInfo.downloadUrl != null) {
            try {
                return new URI(updateInfo.downloadUrl).toURL();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 获取更新日志链接（若存在）
     */
    public URL getChangelogLink() {
        // 每次都发起网络请求以检查更新（可能得到304缓存命中）
        fetchUpdateInfo();
        if (updateInfo != null && updateInfo.changelog != null) {
            try {
                return new URI(updateInfo.changelog).toURL();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    @Override
    public boolean download() {
        try {
            URL link = getDownloadUrl();
            if (link == null) {
                return false;
            }

            String response = Utils.Http.get(link.toString());
            // 如果能成功获取任何内容，说明连接成功
            return response != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 内部类：表示更新信息JSON结构
     */
    private static class UpdateInfo {
        @SerializedName("version")
        private String version;

        @SerializedName("versionCode")
        private Integer versionCode;

        @SerializedName("downloadUrl")
        private String downloadUrl;

        @SerializedName("changelog")
        private String changelog;
    }
}
