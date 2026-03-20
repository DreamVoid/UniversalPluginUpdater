package me.dreamvoid.universalpluginupdater.update;

import com.google.gson.Gson;
import me.dreamvoid.universalpluginupdater.Utils;
import me.dreamvoid.universalpluginupdater.platform.IPlatformProvider;
import me.dreamvoid.universalpluginupdater.update.modrinth.ModrinthFile;
import me.dreamvoid.universalpluginupdater.update.modrinth.ModrinthVersion;

import java.net.URL;
import java.util.List;
import java.util.logging.Logger;

public class ModrinthUpdate extends AbstractUpdate {
    private static final String MODRINTH_API = "https://api.modrinth.com/v2";
    private static final Gson gson = new Gson();
    private static final Logger logger = Utils.getLogger();

    private final String projectId;
    private final IPlatformProvider platformProvider;
    private ModrinthVersion selectedVersion;
    private boolean versionFetched = false;

    public ModrinthUpdate(String projectId, IPlatformProvider platformProvider) {
        this.updateType = UpdateType.Modrinth;
        this.projectId = projectId;
        this.platformProvider = platformProvider;
    }

    /**
     * 从Modrinth API获取版本信息并选择合适的版本
     */
    private boolean fetchVersionInfo() {
        if (versionFetched) {
            return selectedVersion != null;
        }

        try {
            String url = buildApiUrl();
            String response = Utils.Http.get(url);

            if (response == null) {
                versionFetched = true;
                return false;
            }

            // 解析JSON数组
            ModrinthVersion[] versions = gson.fromJson(response, ModrinthVersion[].class);
            if (versions == null || versions.length == 0) {
                versionFetched = true;
                return false;
            }

            // 选择第一个版本（Modrinth API已按时间排序，最新的在前）
            this.selectedVersion = versions[0];
            versionFetched = true;
            return true;
        } catch (Exception e) {
            if (logger != null) {
                logger.warning("Failed to fetch Modrinth version info for project: " + projectId);
            }
            versionFetched = true;
            return false;
        }
    }

    /**
     * 构建Modrinth API URL
     */
    private String buildApiUrl() {
        StringBuilder url = new StringBuilder();
        url.append(MODRINTH_API)
                .append("/project/")
                .append(projectId)
                .append("/versions");

        // 构建查询参数
        StringBuilder query = new StringBuilder();

        // 添加加载器参数
        List<String> loaders = platformProvider.getLoaders();
        if (loaders != null && !loaders.isEmpty()) {
            query.append("loaders=[");
            for (int i = 0; i < loaders.size(); i++) {
                if (i > 0) query.append(",");
                query.append("\"").append(loaders.get(i)).append("\"");
            }
            query.append("]");
        }

        // 添加游戏版本参数
        List<String> gameVersions = platformProvider.getGameVersions();
        if (gameVersions != null && !gameVersions.isEmpty()) {
            if (query.length() > 0) query.append("&");
            query.append("game_versions=[");
            for (int i = 0; i < gameVersions.size(); i++) {
                if (i > 0) query.append(",");
                query.append("\"").append(gameVersions.get(i)).append("\"");
            }
            query.append("]");
        }

        // 添加featured参数（默认true，优先选择推荐版本）
        if (query.length() > 0) query.append("&");
        query.append("featured=true");

        // 添加changelog参数（不需要更新日志）
        query.append("&include_changelog=false");

        if (query.length() > 0) {
            url.append("?").append(query);
        }

        return url.toString();
    }

    @Override
    public String getVersion() {
        if (!versionFetched) {
            fetchVersionInfo();
        }
        return selectedVersion != null ? selectedVersion.getVersionNumber() : null;
    }

    @Override
    public URL getDownloadLink() {
        if (!versionFetched) {
            fetchVersionInfo();
        }

        if (selectedVersion != null) {
            ModrinthFile file = selectedVersion.getPrimaryFile();
            if (file != null && file.getUrl() != null) {
                try {
                    return new URL(file.getUrl());
                } catch (Exception e) {
                    if (logger != null) {
                        logger.warning("Invalid download URL from Modrinth: " + file.getUrl());
                    }
                }
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
            return response != null;
        } catch (Exception e) {
            return false;
        }
    }
}
