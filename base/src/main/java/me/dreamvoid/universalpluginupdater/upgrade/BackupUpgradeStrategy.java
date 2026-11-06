package me.dreamvoid.universalpluginupdater.upgrade;

import me.dreamvoid.universalpluginupdater.Config;
import me.dreamvoid.universalpluginupdater.platform.Platform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

import static me.dreamvoid.universalpluginupdater.service.LanguageManager.tr;

/**
 * 备份旧 JAR 的文件更新策略，不卸载或重载运行中的插件
 */
public final class BackupUpgradeStrategy implements UpgradeStrategy {
    private final Platform platform;
    private final Set<String> reserved = new HashSet<>();
    private final Set<String> reservedNames = new HashSet<>();
    private boolean recoveryBlocked;

    /**
     * 创建备份策略
     * @param platform 平台服务
     */
    public BackupUpgradeStrategy(@NotNull Platform platform) {
        this.platform = platform;
    }

    @Override
    @NotNull
    public String getId() {
        return "backup";
    }

    @Override
    @NotNull
    public String getName() {
        return "Backup";
    }

    private Path root() throws IOException {
        return platform.getDataPath().toAbsolutePath().getParent().toRealPath();
    }

    private BackupFileTransaction engine() throws IOException {
        return new BackupFileTransaction(root(), descriptor());
    }

    private Path index() throws IOException {
        Path index = platform.getDataPath().resolve("backup-journals").toAbsolutePath().normalize();
        if (!index.startsWith(root()) || Files.isSymbolicLink(platform.getDataPath()) || Files.isSymbolicLink(index)) {
            throw new IOException("Unsafe backup journal index");
        }
        return Files.createDirectories(index);
    }

    /**
     * 验证并固定本次更新，同时登记持久化事务
     * @param pluginId 插件标识符
     * @param download 下载文件
     * @param oldFile 当前插件文件，可为 {@code null}
     * @return 事务目录
     * @throws IOException 验证失败或事务已存在
     */
    @NotNull
    public synchronized Path prepare(@NotNull String pluginId, @NotNull Path download, @Nullable Path oldFile) throws IOException {
        String id = pluginId.toLowerCase(Locale.ROOT);
        if (recoveryBlocked) throw new IOException("Unresolved recovery error; inspect backup journals before updating");
        String targetName = download.getFileName().toString().toLowerCase(Locale.ROOT);
        if (reservedNames.contains(targetName)) throw new IOException("Destination already reserved by an update: " + targetName);
        if (reserved.contains(id)) throw new IOException("Plugin already queued or replaced; restart before updating it again: " + id);
        BackupFileTransaction engine = engine();
        Path root = root();
        Path old = oldFile == null ? null : oldFile.toAbsolutePath().normalize();
        // Paper 可能返回重映射缓存中的文件，应按描述匹配 plugins 中的原始文件
        if (old == null || !root.equals(old.getParent())) {
            List<Path> candidates = new ArrayList<>();
            try (Stream<Path> stream = Files.list(root)) {
                for (Path candidate : stream.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")).toList()) {
                    try {
                        if (BackupJar.id(candidate, descriptor(), false).equals(id)) candidates.add(candidate);
                    } catch (IOException ignored) {
                    }
                }
            }
            if (candidates.size() > 1) throw new IOException("Multiple installed JARs for " + id);
            old = candidates.isEmpty() ? null : candidates.get(0);
            if (old == null && (oldFile != null || platform.getPlugins().stream().anyMatch(id::equalsIgnoreCase))) {
                throw new IOException("Cannot locate original JAR directly inside plugins: " + id);
            }
        }
        Path receipt = engine.prepare(id, download, old, Config.Updater_Backup_Directory);
        // 索引保留历史目录位置，修改 backup.directory 后仍能发现中断事务
        Files.writeString(index().resolve(receipt.getFileName() + ".txt"), root.relativize(receipt).toString(),
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, StandardOpenOption.SYNC);
        reserved.add(id);
        reservedNames.add(targetName);
        return receipt;
    }

    private String descriptor() {
        String name = platform.getPlatformName().toLowerCase(Locale.ROOT);
        return name.contains("velocity") ? "velocity-plugin.json"
                : name.contains("bungee") || name.contains("waterfall") ? "bungee.yml"
                : name.contains("paper") || name.contains("purpur") || name.contains("folia") ? "paper-plugin.yml" : "plugin.yml";
    }

    /**
     * 执行已固定的更新
     * @param pluginId 插件标识符
     * @param receipt 事务目录
     * @return 是否成功
     */
    public synchronized boolean executePrepared(@NotNull String pluginId, @NotNull Path receipt) {
        try {
            engine().execute(receipt);
            platform.getPlatformLogger().info(tr("message.strategy.backup.success", pluginId, receipt));
            return true;
        } catch (Exception e) {
            // 同时输出 cause 与 suppressed，保留恢复失败的诊断信息
            platform.getPlatformLogger().log(java.util.logging.Level.SEVERE,
                    tr("message.strategy.backup.failed", pluginId, receipt, e.getMessage()), e);
            recoveryBlocked = true;
            return false;
        }
    }

    @Override
    public synchronized boolean upgrade(String pluginId, Path newFilePath, @Nullable Path oldFilePath) {
        try {
            return executePrepared(pluginId, prepare(pluginId, newFilePath, oldFilePath));
        } catch (Exception e) {
            platform.getPlatformLogger().warning(tr("message.strategy.backup.failed", pluginId, newFilePath, e.getMessage()));
            return false;
        }
    }

    /**
     * 检查持久化事务，恢复可明确判断的中断操作
     */
    public synchronized void recover() {
        try {
            BackupFileTransaction engine = engine();
            try (Stream<Path> entries = Files.list(index())) {
                for (Path entry : entries.filter(p -> p.getFileName().toString().endsWith(".txt")).toList()) {
                    try {
                        if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS) || Files.size(entry) > 4096) {
                            throw new IOException("Unsafe journal index entry");
                        }
                        Path receipt = root().resolve(Files.readString(entry)).normalize();
                        if (!receipt.startsWith(root()) || receipt.equals(root())) throw new IOException("Index escapes plugins");
                        String result = engine.recover(receipt);
                        platform.getPlatformLogger().info(tr("message.strategy.backup.recovered", receipt, result));
                    } catch (Exception e) {
                        recoveryBlocked = true;
                        platform.getPlatformLogger().log(java.util.logging.Level.SEVERE,
                                tr("message.strategy.backup.recovery-failed", entry, e.getMessage()), e);
                    }
                }
            }
        } catch (Exception e) {
            recoveryBlocked = true;
            platform.getPlatformLogger().severe(tr("message.strategy.backup.recovery-failed", platform.getDataPath(), e.getMessage()));
        }
    }
}
