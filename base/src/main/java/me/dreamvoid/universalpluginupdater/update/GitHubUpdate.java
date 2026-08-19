package me.dreamvoid.universalpluginupdater.update;

import com.google.gson.JsonObject;
import me.dreamvoid.universalpluginupdater.Config;
import me.dreamvoid.universalpluginupdater.Utils;
import me.dreamvoid.universalpluginupdater.objects.channel.info.GitHubChannelInfo;
import me.dreamvoid.universalpluginupdater.objects.update.github.GithubAsset;
import me.dreamvoid.universalpluginupdater.objects.update.github.GithubRelease;
import me.dreamvoid.universalpluginupdater.platform.Platform;
import me.dreamvoid.universalpluginupdater.service.UpdateManager;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static me.dreamvoid.universalpluginupdater.service.LanguageManager.tr;

@UpdateChannel("github")
public class GitHubUpdate extends AbstractUpdate {
    private static final String GITHUB_API_URL = "https://api.github.com";

    private final GitHubChannelInfo info;

    private GithubRelease selectedRelease;
    private GithubAsset selectedAsset;
    private String cacheToken;

    public GitHubUpdate(String pluginId, JsonObject config, Platform platform) {
        super(pluginId, platform);
        this.info = applyDefaults(Utils.getGson().fromJson(config, GitHubChannelInfo.class));
        if (this.info.repository() == null || this.info.repository().isEmpty()) {
            throw new IllegalArgumentException("repository 不能为空");
        }
    }

    /**
     * 获取该渠道的默认配置（仅本渠道使用）
     */
    private GitHubChannelInfo defaults() {
        return new GitHubChannelInfo(null, null, List.of("application/java-archive", "application/x-java-archive"), null, "name", null);
    }

    /**
     * 将配置中缺失（为 null）的字段用默认值替换
     */
    private GitHubChannelInfo applyDefaults(GitHubChannelInfo info) {
        GitHubChannelInfo defaults = defaults();
        return new GitHubChannelInfo(
                info.repository(),
                info.auth(),
                info.accept() != null ? info.accept() : defaults.accept(),
                info.filter(),
                info.versionKey() != null ? info.versionKey() : defaults.versionKey(),
                info.versionRegex()
        );
    }

    @Override
    public boolean checkUpdate() {
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
                    logger.info(tr("message.update.ignore", url, tr("tag.update.ignore.response-null")));
                    return false;
                }

                GithubRelease release = Utils.getGson().fromJson(content, GithubRelease.class);
                if (release == null) {
                    logger.info(tr("message.update.ignore", url, tr("tag.update.ignore.response-null")));
                    return false;
                }

                GithubAsset targetAsset = resolveAsset(release.assets());
                if (targetAsset == null) {
                    logger.info(tr("message.update.ignore", url, tr("tag.update.ignore.no-version")));
                    return false;
                }

                this.selectedRelease = release;
                this.selectedAsset = targetAsset;
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

    private GithubAsset resolveAsset(List<GithubAsset> assets) {
        if (assets == null || assets.isEmpty()) return null;

        List<GithubAsset> filteredByType = assets;
        List<String> acceptedTypes = info.accept();
        if (!acceptedTypes.isEmpty()) {
            filteredByType = assets.stream()
                .filter(a -> a.contentType() != null && acceptedTypes.stream().anyMatch(type -> type.equalsIgnoreCase(a.contentType())))
                .toList();
        }

        if (filteredByType.isEmpty()) return null;

        if (info.filter() == null) {
                List<String> loaders = ((Config.Platform_Loaders != null && !Config.Platform_Loaders.isEmpty())
                    ? Config.Platform_Loaders
                    : platform.getLoaders()).stream()
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
        if (selectedRelease == null) return null;
        String version;

        String versionKey = info.versionKey();
        if (versionKey == null || versionKey.isBlank()) {
            version = selectedRelease.name();
        } else {
            version = switch (versionKey) {
                case "tag_name" -> {
                    String value = selectedRelease.tagName();
                    yield (value != null && !value.isBlank()) ? value : selectedRelease.name();
                }
                case "name" -> selectedRelease.name();
                default -> selectedRelease.name();
            };
        }

        if (version == null || version.isBlank()) {
            return null;
        }

        String regex = info.versionRegex();
        if (regex != null && !regex.isBlank()) {
            try {
                Pattern pattern = Pattern.compile(regex);
                Matcher matcher = pattern.matcher(version);
                if (matcher.find()) {
                    if (matcher.groupCount() >= 1) {
                        String group = matcher.group(1);
                        if (group != null && !group.isBlank()) {
                            return group.trim();
                        }
                    }

                    String matched = matcher.group();
                    if (matched != null && !matched.isBlank()) {
                        return matched.trim();
                    }
                }
            } catch (PatternSyntaxException e) {
                logger.warning(tr("message.update.warn.invalid-regex", regex));
            }
        }

        version = version.trim();
        if (version.regionMatches(true, 0, "v", 0, 1)) {
            version = version.substring(1).trim();
        }

        return version.split(" ")[0];
    }

    @Override
    @Nullable
    public Path download() {
        if (selectedRelease == null || selectedAsset == null) {
            logger.warning(tr("message.update.failed", tr("tag.update.github.failed.no-selected-release")));
            return null;
        }

        String downloadUrl = selectedAsset.browserDownloadUrl();
        if (downloadUrl == null) {
            logger.warning(tr("message.update.failed", tr("tag.update.github.failed.no-download-url")));
            return null;
        }

        String originFilename = selectedAsset.name();
        String hashAlgorithm = normalizeDigestAlgorithm(selectedAsset.hashAlgorithm());
        String preferredHash = selectedAsset.hashValue();

        try {
            String desiredFilename = Utils.parseFileName(pluginId, getChannelId());
            String expectedFilename = desiredFilename != null ? desiredFilename : originFilename;

            Path downloadDir = UpdateManager.instance().getDownloadPath();
            Path filePath = downloadDir.resolve(expectedFilename);

            if (filePath.toFile().exists()) {
                if (preferredHash != null && hashAlgorithm != null
                        && Utils.File.verifyHash(filePath, hashAlgorithm, preferredHash)) {
                    logger.info(tr("message.update.hit", downloadUrl));
                    return filePath;
                } else {
                    Files.delete(filePath);
                }
            }

            Utils.Http.DownloadResult result = Utils.Http.download(downloadUrl, downloadDir, desiredFilename);

            if (!result.success()) {
                logger.warning(tr("message.update.error", downloadUrl, result.errorMessage()));
                return null;
            }

            Path downloadedPath = downloadDir.resolve(result.filename());

            if (preferredHash != null && hashAlgorithm != null
                    && !Utils.File.verifyHash(downloadedPath, hashAlgorithm, preferredHash)) {
                logger.warning(tr("message.update.error", downloadUrl, tr("tag.update.error.checksum-mismatch")));
                Files.delete(downloadedPath);
                return null;
            } else {
                logger.info(tr("message.update.get", downloadUrl));
                return downloadedPath;
            }
        } catch (Exception e) {
            logger.warning(tr("message.update.error", downloadUrl, e));
            return null;
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