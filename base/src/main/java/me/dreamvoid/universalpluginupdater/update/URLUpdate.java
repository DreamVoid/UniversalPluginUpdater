package me.dreamvoid.universalpluginupdater.update;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import me.dreamvoid.universalpluginupdater.Utils;

import java.net.URL;

public class URLUpdate extends AbstractUpdate {
    private final String updateUrl;
    private UpdateInfo updateInfo;
    private boolean infoFetched = false;
    private static final Gson gson = new Gson();

    public URLUpdate(String updateUrl) {
        this.updateType = UpdateType.URL;
        this.updateUrl = updateUrl;
    }

    /**
     * 从远程URL获取更新信息
     * 返回的JSON格式应为：
     * {
     *     "version": "string（必选）",
     *     "versionCode": "int（可选）",
     *     "downloadUrl": "url（必选）",
     *     "changelog": "url（可选）"
     * }
     */
    private boolean fetchUpdateInfo() {
        if (infoFetched) {
            return updateInfo != null;
        }

        try {
            String jsonResponse = Utils.Http.get(updateUrl);
            if (jsonResponse == null) {
                infoFetched = true;
                return false;
            }

            this.updateInfo = gson.fromJson(jsonResponse, UpdateInfo.class);
            infoFetched = true;

            if (updateInfo == null || updateInfo.version == null || updateInfo.downloadUrl == null) {
                this.updateInfo = null;
                return false;
            }

            return true;
        } catch (Exception e) {
            infoFetched = true;
            return false;
        }
    }

    @Override
    public String getVersion() {
        if (!infoFetched) {
            fetchUpdateInfo();
        }
        return updateInfo != null ? updateInfo.version : null;
    }

    /**
     * 获取版本代码（若存在）
     */
    public Integer getVersionCode() {
        if (!infoFetched) {
            fetchUpdateInfo();
        }
        return updateInfo != null ? updateInfo.versionCode : null;
    }

    @Override
    public URL getDownloadLink() {
        if (!infoFetched) {
            fetchUpdateInfo();
        }
        if (updateInfo != null && updateInfo.downloadUrl != null) {
            try {
                return new URL(updateInfo.downloadUrl);
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
        if (!infoFetched) {
            fetchUpdateInfo();
        }
        if (updateInfo != null && updateInfo.changelog != null) {
            try {
                return new URL(updateInfo.changelog);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    @Override
    public boolean download() {
        try {
            URL link = getDownloadLink();
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
