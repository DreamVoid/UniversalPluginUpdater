package me.dreamvoid.universalpluginupdater.update;

import me.dreamvoid.universalpluginupdater.Utils;
import me.dreamvoid.universalpluginupdater.objects.channel.info.GitHubChannelInfo;
import me.dreamvoid.universalpluginupdater.objects.update.github.GithubAsset;
import me.dreamvoid.universalpluginupdater.objects.update.github.GithubRelease;
import me.dreamvoid.universalpluginupdater.platform.Platform;
import me.dreamvoid.universalpluginupdater.service.UpgradeManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static me.dreamvoid.universalpluginupdater.service.LanguageManager.tr;

public class GitHubUpdate extends AbstractUpdate {
    private static final String GITHUB_API_URL = "https://api.github.com";

    private final Logger logger;
    private final String pluginId;
    private final GitHubChannelInfo info;
    private final Platform platform;

    private GithubRelease selectedRelease;
    private GithubAsset selectedAsset;
    private String cacheToken;
    private Path downloadedFilePath;

    public GitHubUpdate(String pluginId, GitHubChannelInfo info, Platform platform) {
        if (info.repository() == null || info.repository().isEmpty()) {
            throw new IllegalArgumentException("repository 不能为空");
        }

        this.updateType = UpdateType.GitHub;
        this.pluginId = pluginId;
        this.info = info;
        this.platform = platform;
        this.logger = platform.getPlatformLogger();
    }

    @Override
    public boolean update() {
        String url = GITHUB_API_URL + "/repos/" + info.repository() + "/releases/latest";
        try {
            Utils.Http.Response response = Utils.Http.get(url, cacheToken, "Bearer " + info.auth());

            if (response.statusCode() == 304) {
                if (selectedRelease != null) {
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
                    logger.warning(tr("message.update.error", url, tr("tag.update.error.response-null")));
                    return false;
                }

                GithubRelease release = Utils.getGson().fromJson(content, GithubRelease.class);
                if (release == null) {
                    logger.warning(tr("message.update.error", url, tr("tag.update.error.response-null")));
                    return false;
                }

                GithubAsset targetAsset = resolveAsset(release.assets());
                if (targetAsset == null) {
                    logger.warning(tr("message.update.error", url, "未找到符合条件的文件 (filter/accept mismatched)"));
                    return false;
                }

                this.selectedRelease = release;
                this.selectedAsset = targetAsset;
                this.cacheToken = response.cacheToken();
                logger.info(tr("message.update.get", url));
                return true;
            } else {
                logger.info(tr("message.update.ignore", url, tr("tag.update.error.status-code", response.statusCode())));
                return false;
            }
        } catch (Exception e) {
            logger.warning(tr("message.update.error", url, e));
            return false;
        }
    }

    private GithubAsset resolveAsset(List<GithubAsset> assets) {
        if (assets == null || assets.isEmpty()) return null;

        List<GithubAsset> filteredByType = assets;
        if (info.accept() != null && !info.accept().isBlank()) {
            filteredByType = assets.stream()
                .filter(a -> info.accept().equalsIgnoreCase(a.contentType()))
                .toList();
        }

        if (filteredByType.isEmpty()) return null;

        if (info.filter() == null) {
            List<String> loaders = platform.getLoaders().stream()
                .map(String::toLowerCase)
                .toList();

            for (GithubAsset asset : filteredByType) {
                if (asset.name() == null) continue;
                String lowercaseName = asset.name().toLowerCase();
                for (String loader : loaders) {
                    if (lowercaseName.contains(loader)) {
                        return asset;
                    }
                }
            }
            return filteredByType.get(0);
        } else {
            String filterRaw = info.filter();
            String filterLower = filterRaw.toLowerCase();
            Pattern pattern = null;
            try {
                pattern = Pattern.compile(filterRaw);
            } catch (Exception ignored) { }

            for (GithubAsset asset : filteredByType) {
                if (asset.name() == null) continue;
                String assetName = asset.name();
                if (assetName.toLowerCase().contains(filterLower)) {
                    return asset;
                }
                if (pattern != null && pattern.matcher(assetName).find()) {
                    return asset;
                }
            }

            return null;
        }
    }

    @Override
    public String getVersion() {
        if (selectedRelease == null || selectedRelease.name() == null) return null;
        return selectedRelease.name().split(" ")[0];
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
        if (selectedRelease == null || selectedAsset == null) {
            logger.warning(tr("message.update.error", "No selected github release or asset (未选择到版本或附件)"));
            return false;
        }

        String downloadUrl = selectedAsset.browserDownloadUrl();
        if (downloadUrl == null) {
            logger.warning(tr("message.update.error", "No valid browser_download_url (未找到下载链接)"));
            return false;
        }

        String originFilename = selectedAsset.name();
        String hashAlgorithm = normalizeDigestAlgorithm(selectedAsset.hashAlgorithm());
        String preferredHash = selectedAsset.hashValue();

        try {
            String desiredFilename = Utils.parseFileName(pluginId, updateType);
            String expectedFilename = desiredFilename != null ? desiredFilename : originFilename;

            Path downloadDir = platform.getDataPath().resolve("downloads");
            Path filePath = downloadDir.resolve(expectedFilename);

            if (filePath.toFile().exists()) {
                if (preferredHash != null && hashAlgorithm != null
                        && Utils.File.verifyHash(filePath, hashAlgorithm, preferredHash)) {
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

            if (preferredHash != null && hashAlgorithm != null
                    && !Utils.File.verifyHash(downloadedPath, hashAlgorithm, preferredHash)) {
                logger.warning(tr("message.update.error", downloadUrl, tr("tag.update.error.checksum-mismatch")));
                Files.delete(downloadedPath);
                this.downloadedFilePath = null;
                return false;
            } else {
                this.downloadedFilePath = downloadedPath;
                logger.info(tr("message.update.get", downloadUrl));
                return true;
            }
        } catch (Exception e) {
            logger.warning(tr("message.update.error.reason", downloadUrl, e));
            return false;
        }
    }

    private String normalizeDigestAlgorithm(String algorithm) {
        if (algorithm == null || algorithm.isBlank()) {
            return null;
        }

        String normalized = algorithm.trim().replace("_", "-").toUpperCase();
        return switch (normalized) {
            case "SHA256" -> "SHA-256";
            case "SHA384" -> "SHA-384";
            case "SHA512" -> "SHA-512";
            case "SHA1" -> "SHA-1";
            case "MD5" -> "MD5";
            default -> normalized;
        };
    }
}