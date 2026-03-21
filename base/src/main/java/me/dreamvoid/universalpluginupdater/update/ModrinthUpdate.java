package me.dreamvoid.universalpluginupdater.update;

import com.google.gson.Gson;
import me.dreamvoid.universalpluginupdater.Utils;
import me.dreamvoid.universalpluginupdater.platform.IPlatformProvider;
import me.dreamvoid.universalpluginupdater.update.modrinth.ModrinthFile;
import me.dreamvoid.universalpluginupdater.update.modrinth.ModrinthVersion;

import java.net.URI;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class ModrinthUpdate extends AbstractUpdate {
    private static final String MODRINTH_API = "https://api.modrinth.com/v2";
    private static final Gson gson = new Gson();
    private static final Logger logger = Utils.getLogger();

    private final String projectId;
    private final IPlatformProvider platform;
    private ModrinthVersion selectedVersion;
    private String lastModified;

    public ModrinthUpdate(String projectId, IPlatformProvider platform) {
        this.updateType = UpdateType.Modrinth;
        this.projectId = projectId;
        this.platform = platform;
    }

    /**
     * 从Modrinth API获取版本信息并选择合适的版本
     * 使用HTTP缓存机制减少网络请求和Modrinth负载
     */
    private boolean fetchVersionInfo() {
        String url = buildApiUrl();
        try {
            Utils.Http.Response response = Utils.Http.get(url, lastModified);

            if (response.isNotModified()) {
                // 返回304 Not Modified，使用缓存
                if (selectedVersion == null) {
                    logger.warning("Err: " + url + " [304 but no cache]");
                    return false;
                }
                logger.info("Hit: " + url);
                this.lastModified = response.lastModified;
                return true;
            }

            if (response.isSuccess()) {
                String content = response.content;
                if (content == null) {
                    logger.warning("Err: " + url + " [response is null]");
                    return false;
                }

                // 解析JSON数组
                ModrinthVersion[] versions = gson.fromJson(content, ModrinthVersion[].class);
                if (versions == null || versions.length == 0) {
                    logger.warning("Err: " + url + " [no versions]");
                    return false;
                }

                // 选择第一个版本（Modrinth API已按时间排序，最新的在前）
                this.selectedVersion = versions[0];
                this.lastModified = response.lastModified;
                logger.info("Get: " + url);
                return true;
            }

            logger.warning("Err: " + url + " [status code: " + response.statusCode + "]");
            return false;
        } catch (Exception e) {
            logger.warning("Err: " + url + " [" + e + "]");
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
                .append("/version");

        // 构建查询参数
        Set<String> queries = new HashSet<>();

        // 添加changelog参数（不需要更新日志）
        queries.add("include_changelog=false");

        // 添加featured参数（默认true，优先选择推荐版本）
        if (true) { // TODO: 由用户控制是否featured
            queries.add("featured=true");
        }

        // 添加加载器参数
        List<String> loaders = platform.getLoaders();
        if (loaders != null && !loaders.isEmpty()) {
            queries.add("loaders=[" + String.join(",", loaders) + "]");
        }

        // 添加游戏版本参数
        List<String> gameVersions = platform.getGameVersions();
        if (gameVersions != null && !gameVersions.isEmpty()) {
            queries.add("game_versions=[" + String.join(",", gameVersions) + "]");
        }

        url.append("?").append(String.join("&", queries));
        return url.toString();
    }

    @Override
    public String getVersion() {
        // 每次都发起网络请求以检查更新（可能得到304缓存命中）
        fetchVersionInfo();
        // 返回版本名而不是版本号
        // 原因：本地插件版本号暂无法获取，且Modrinth版本号通常非纯数字
        // 仅当版本号为纯数字时才适合直接比较大小
        // 其他平台的更新渠道实现时应注意此点
        return selectedVersion != null ? selectedVersion.getName() : null;
    }

    @Override
    public URL getDownloadUrl() {
        // 每次都发起网络请求以检查更新（可能得到304缓存命中）
        fetchVersionInfo();

        if (selectedVersion != null) {
            ModrinthFile file = selectedVersion.getPrimaryFile();
            if (file != null && file.getUrl() != null) {
                try {
                    return new URI(file.getUrl()).toURL();
                } catch (Exception e) {
                    logger.warning("Invalid download URL from Modrinth: " + file.getUrl());
                }
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
            return response != null;
        } catch (Exception e) {
            return false;
        }
    }
}
