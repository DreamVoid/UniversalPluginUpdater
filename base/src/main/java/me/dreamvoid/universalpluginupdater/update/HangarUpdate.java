package me.dreamvoid.universalpluginupdater.update;

import me.dreamvoid.universalpluginupdater.Utils;
import me.dreamvoid.universalpluginupdater.objects.channel.info.HangarChannelInfo;
import me.dreamvoid.universalpluginupdater.objects.update.hangar.HangarFileInfo;
import me.dreamvoid.universalpluginupdater.objects.update.hangar.HangarPlatformDownload;
import me.dreamvoid.universalpluginupdater.objects.update.hangar.HangarResponse;
import me.dreamvoid.universalpluginupdater.objects.update.hangar.HangarVersion;
import me.dreamvoid.universalpluginupdater.platform.Platform;
import me.dreamvoid.universalpluginupdater.service.UpgradeManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import static java.net.URLEncoder.*;
import static me.dreamvoid.universalpluginupdater.service.LanguageManager.tr;

public class HangarUpdate extends AbstractUpdate {
    private static final String HANGAR_API = "https://hangar.papermc.io/api/v1";

    private final Logger logger;
    private final String pluginId;
    private final HangarChannelInfo info;
    private final Platform platform;

    private HangarVersion selectedVersion;
    private String selectedPlatformKey; // eg "PAPER"
    private String cacheToken;
    private Path downloadedFilePath;

    public HangarUpdate(String pluginId, HangarChannelInfo info, Platform platform) {
        if (info.author() == null || info.author().isEmpty() || info.slugOrId() == null || info.slugOrId().isEmpty()) {
            throw new IllegalArgumentException("author, slugOrId 不存在或为空");
        }

        this.updateType = UpdateType.Hangar;
        this.pluginId = pluginId;
        this.info = info;
        this.platform = platform;
        this.logger = platform.getPlatformLogger();
    }

    @Override
    public boolean update() {
        String url = buildUrl();
        try {
            Utils.Http.Response response = Utils.Http.get(url, cacheToken);

            if (response.statusCode() == 304) {
                if (selectedVersion != null && selectedPlatformKey != null) {
                    this.cacheToken = response.cacheToken();
                    logger.info(tr("message.update.hit", url));
                    return true;
                } else {
                    logger.warning(tr("message.update.error", url, tr("tag.update.error.no-cache-304")));
                    return false;
                }
            } else if (response.statusCode() == 200) {
                String content = response.content();
                if (content == null) {
                    logger.info(tr("message.update.ignore", url, tr("tag.update.ignore.response-null")));
                    return false;
                }

                HangarResponse hangarResponse = Utils.getGson().fromJson(content, HangarResponse.class);
                if (hangarResponse == null || hangarResponse.result() == null || hangarResponse.result().isEmpty()) {
                    logger.info(tr("message.update.ignore", url, tr("tag.update.ignore.no-version")));
                    return false;
                }

                HangarVersion latestVersion = hangarResponse.result().get(0);
                Map<String, HangarPlatformDownload> downloads = latestVersion.downloads();
                if (downloads == null || downloads.isEmpty()) {
                    logger.info(tr("message.update.ignore", url, tr("tag.update.ignore.no-version")));
                    return false;
                }

                // 寻找匹配的加载器
                List<String> loaders = platform.getLoaders();
                String targetPlatform = null;
                for (String platformKey : downloads.keySet()) {
                    for (String loader : loaders) {
                        if (platformKey.equalsIgnoreCase(loader)) {
                            targetPlatform = platformKey;
                            break;
                        }
                    }
                    if (targetPlatform != null) break;
                }

                if (targetPlatform == null) {
                    logger.info(tr("message.update.ignore", url, tr("tag.update.ignore.no-version")));
                    return false;
                }

                this.selectedVersion = latestVersion;
                this.selectedPlatformKey = targetPlatform;
                this.cacheToken = response.cacheToken();
                logger.info(tr("message.update.get", url));
                return true;
            } else {
                logger.info(tr("message.update.ignore", url, tr("tag.update.ignore.status-code", response.statusCode())));
                return false;
            }
        } catch (Exception e) {
            logger.warning(tr("message.update.error", url, e));
            return false;
        }
    }

    private String buildUrl() {
        StringBuilder url = new StringBuilder();
        url.append(HANGAR_API).append("/projects/").append(info.author()).append("/").append(info.slugOrId()).append("/versions");

        Set<String> queries = new HashSet<>();

        // limit
        queries.add("limit=1");

        // channel (from info)
        if (info.channel() != null && !info.channel().isEmpty()) {
            queries.add("channel=" + encode(info.channel(), java.nio.charset.StandardCharsets.UTF_8));
        }

        // platform (from info) -> this is parameter "platform=Paper" for API query filtering (optional if users specify in config)
        if (info.platform() != null && !info.platform().isEmpty()) {
            queries.add("platform=" + encode(info.platform(), java.nio.charset.StandardCharsets.UTF_8));
            
            // platformVersion -> gameVersions
            // Hangar API requires platform to be specified when platformVersion is provided
            List<String> gameVersions = platform.getGameVersions();
            if (gameVersions != null && !gameVersions.isEmpty()) {
                queries.add("platformVersion=" + encode(String.join(",", gameVersions), java.nio.charset.StandardCharsets.UTF_8));
            }
        }

        url.append("?").append(String.join("&", queries));
        return url.toString();
    }

    @Override
    public String getVersion() {
        return selectedVersion != null ? selectedVersion.name() : null;
    }

    @Override
    public String getPluginId() {
        return pluginId;
    }

    @Override
    public boolean upgrade(boolean now) {
        try {
            if (!download()) return false;

            Path currentPluginFile = platform.getPluginFile(pluginId);
            Path newPluginFile = downloadedFilePath;

            if (newPluginFile == null || !Files.exists(newPluginFile)) {
                logger.warning(tr("message.update.error.downloaded-file-missing", newPluginFile));
                return false;
            }

            return UpgradeManager.instance().upgrade(pluginId, newPluginFile, currentPluginFile, now);
        } catch (Exception e) {
            logger.warning(tr("message.update.failed", e));
            return false;
        }
    }

    @Override
    public boolean download() {
        if (selectedVersion == null || selectedPlatformKey == null) {
            logger.warning(tr("message.update.failed", tr("tag.update.hangar.failed.no-selected-version")));
            return false;
        }

        HangarPlatformDownload downloadInf = selectedVersion.downloads().get(selectedPlatformKey);
        if (downloadInf == null || downloadInf.downloadUrl() == null || downloadInf.downloadUrl().isEmpty()) {
            logger.warning(tr("message.update.failed", tr("tag.update.hangar.failed.no-download-url")));
            return false;
        }

        String downloadUrl = downloadInf.downloadUrl();
        HangarFileInfo fileInfo = downloadInf.fileInfo();
        String originFilename = fileInfo != null ? fileInfo.name() : null;
        String preferredHash = fileInfo != null ? fileInfo.sha256Hash() : null;
        String hashAlgorithm = "SHA-256";

        try {
            String desiredFilename = Utils.parseFileName(pluginId, updateType);
            String expectedFilename = desiredFilename != null ? desiredFilename : (originFilename != null ? originFilename : pluginId + "-" + selectedVersion.name() + ".jar");

            Path downloadDir = platform.getDataPath().resolve("downloads");
            Path filePath = downloadDir.resolve(expectedFilename);

            if (filePath.toFile().exists()) {
                if (preferredHash != null && !preferredHash.isEmpty() && Utils.File.verifyHash(filePath, hashAlgorithm, preferredHash)) {
                    this.downloadedFilePath = filePath;
                    logger.info(tr("message.update.hit", downloadUrl));
                    return true;
                } else {
                    Files.delete(filePath);
                }
            }

            Utils.Http.DownloadResult result = Utils.Http.download(downloadUrl, downloadDir, desiredFilename);

            if (!result.success()) {
                logger.warning(tr("message.update.error", downloadUrl, result.errorMessage()));
                return false;
            }

            Path downloadedPath = downloadDir.resolve(result.filename());

            if (preferredHash != null && !preferredHash.isEmpty()) {
                if (Utils.File.verifyHash(downloadedPath, hashAlgorithm, preferredHash)) {
                    this.downloadedFilePath = downloadedPath;
                    logger.info(tr("message.update.get", downloadUrl));
                    return true;
                } else {
                    logger.warning(tr("message.update.error", downloadUrl, tr("tag.update.error.checksum-mismatch")));
                    Files.delete(downloadedPath);
                    this.downloadedFilePath = null;
                    return false;
                }
            } else {
                this.downloadedFilePath = downloadedPath;
                logger.info(tr("message.update.get", downloadUrl));
                return true;
            }
        } catch (Exception e) {
            logger.warning(tr("message.update.error", downloadUrl, e));
            return false;
        }
    }
}
