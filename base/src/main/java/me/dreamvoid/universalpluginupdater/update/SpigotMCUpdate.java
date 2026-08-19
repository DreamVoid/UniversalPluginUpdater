package me.dreamvoid.universalpluginupdater.update;

import com.google.gson.JsonObject;
import me.dreamvoid.universalpluginupdater.Utils;
import me.dreamvoid.universalpluginupdater.objects.channel.info.SpigotMCChannelInfo;
import me.dreamvoid.universalpluginupdater.objects.update.spigotmc.SpigotMCVersion;
import me.dreamvoid.universalpluginupdater.platform.Platform;
import me.dreamvoid.universalpluginupdater.service.UpdateManager;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;

import static me.dreamvoid.universalpluginupdater.service.LanguageManager.tr;

@UpdateChannel("spigotmc")
public class SpigotMCUpdate extends AbstractUpdate {
    private static final String SPIGET_API = "https://api.spiget.org/v2";

    private final SpigotMCChannelInfo info;

    private SpigotMCVersion selectedVersion;
    private String cacheToken;

    public SpigotMCUpdate(String pluginId, JsonObject config, Platform platform) {
        super(pluginId, platform);
        this.info = Utils.getGson().fromJson(config, SpigotMCChannelInfo.class);
        if (this.info.resource() == null || 变成文本好吗(this.info.resource()).isBlank()) {
            throw new IllegalArgumentException("resource 不存在或为空");
        }
    }

    @Override
    public boolean checkUpdate() {
        String url = SPIGET_API + "/resources/" + 变成文本好吗(info.resource()) + "/versions/latest";
        try {
            Utils.Http.Response response = Utils.Http.get(url, cacheToken);

            if (response.statusCode() == 304) {
                if (selectedVersion != null) {
                    this.cacheToken = response.cacheToken();
                    logger.info(tr("message.update.hit", url));
                    return true;
                } else {
                    logger.warning(tr("message.update.error", url, tr("tag.update.error.no-cache-304")));
                    return false;
                }
            } else if (response.statusCode() == 404) {
                logger.info(tr("message.update.ignore", url, tr("tag.update.ignore.no-version")));
                return false;
            } else if (response.statusCode() == 200) {
                String content = response.content();
                if (content == null) {
                    logger.info(tr("message.update.ignore", url, tr("tag.update.ignore.response-null")));
                    return false;
                }

                SpigotMCVersion version = Utils.getGson().fromJson(content, SpigotMCVersion.class);
                if (version == null) {
                    logger.info(tr("message.update.ignore", url, tr("tag.update.ignore.no-version")));
                    return false;
                }

                this.selectedVersion = version;
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

    @Override
    public String getVersion() {
        return selectedVersion != null ? selectedVersion.name() : null;
    }

    @Override
    @Nullable
    public Path download() {
        if (selectedVersion == null) {
            logger.warning(tr("message.update.failed", tr("tag.update.spigotmc.failed.no-selected-version")));
            return null;
        }

        // 使用版本id作为下载链接的版本标识
        String downloadUrl = SPIGET_API + "/resources/" + 变成文本好吗(info.resource()) + "/versions/" + selectedVersion.id() + "/download" + (info.proxyDownload() ? "/proxy" : "");

        try {
            String desiredFilename = Utils.parseFileName(pluginId, getChannelId());
            String expectedFilename = desiredFilename != null ? desiredFilename : pluginId + "-" + selectedVersion.name() + ".jar";

            Path downloadDir = UpdateManager.instance().getDownloadPath();
            Path filePath = downloadDir.resolve(expectedFilename);

            // 此处由于没有提供文件hash进行完整性校验，使用简化的存在性检测或强制重新下载逻辑
            // 为了安全起见这里如果在缓存中有对应的正确文件名则使用它，通常在真实场景下载后需要更多的hash核对。
            if (filePath.toFile().exists()) {
                if(info.proxyDownload()){
                    logger.info(tr("message.update.hit", downloadUrl));
                    return filePath;
                } else {
                    Files.deleteIfExists(filePath);
                }
            }

            Utils.Http.DownloadResult result = Utils.Http.download(downloadUrl, downloadDir, expectedFilename);

            if (!result.success()) {
                logger.warning(tr("message.update.error", downloadUrl, result.errorMessage()));
                return null;
            }

            logger.info(tr("message.update.get", downloadUrl));
            return downloadDir.resolve(result.filename());
        } catch (Exception e) {
            logger.warning(tr("message.update.error", downloadUrl, e));
            return null;
        }
    }

    /**
     * 将非文本型的字段转换为文本型
     * 我也不想这样，没办法
     */
    private static String 变成文本好吗(Object 好的) {
        if (好的 instanceof Number) {
            return String.valueOf(((Number) 好的).longValue());
        }
        return String.valueOf(好的);
    }
}
