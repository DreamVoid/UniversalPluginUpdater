package me.dreamvoid.universalpluginupdater.velocity.upgrade;

import me.dreamvoid.universalpluginupdater.upgrade.UpgradeStrategy;
import me.dreamvoid.universalpluginupdater.velocity.VelocityPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static me.dreamvoid.universalpluginupdater.service.LanguageManager.tr;

public final class VelocityUpgradeStrategy implements UpgradeStrategy {
    static final String APPLIER_MAIN_CLASS = "me.dreamvoid.universalpluginupdater.velocity.upgrade.VelocityUpgradeApplierMain";

    private final VelocityPlugin plugin;
    private final Logger logger;
    private final Path pendingDir;
    private final List<PendingOperation> pendingOperations = new ArrayList<>();

    public VelocityUpgradeStrategy(VelocityPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getPlatformLogger();
        this.pendingDir = plugin.getDataPath().resolve("pending-upgrades");
    }

    @Override
    public @NotNull String getId() {
        return "velocity";
    }

    @Override
    public String getName() {
        return "Velocity 延迟应用";
    }

    @Override
    public boolean supportSafeUpgrade() {
        return true;
    }

    @Override
    public boolean upgrade(String pluginId, Path newFilePath, @Nullable Path oldFilePath) {
        try {
            Path targetPath;
            if (oldFilePath != null) {
                targetPath = oldFilePath;
            } else {
                Path pluginDirectory = plugin.getDataPath().getParent();
                if (pluginDirectory == null) {
                    logger.warning(tr("message.strategy.velocity.error.plugin-directory-missing"));
                    return false;
                }
                targetPath = pluginDirectory.resolve(newFilePath.getFileName());
            }

            synchronized (pendingOperations) {
                pendingOperations.add(new PendingOperation(
                    pluginId,
                    targetPath.toAbsolutePath().toString(),
                    newFilePath.toAbsolutePath().toString()
                ));
            }

            logger.info(tr("message.strategy.velocity.scheduled", pluginId));
            return true;
        } catch (Exception e) {
            logger.warning(tr("message.strategy.velocity.exception", pluginId, e));
            return false;
        }
    }

    public void launch() {
        try {
            List<PendingOperation> operations;
            synchronized (pendingOperations) {
                operations = new ArrayList<>(pendingOperations);
            }

            if (operations.isEmpty()) {
                return;
            }

            Path selfJar = plugin.getPluginFile("universalpluginupdater");
            if (selfJar == null || !Files.exists(selfJar)) {
                logger.warning(tr("message.strategy.velocity.error.self-jar-missing"));
                return;
            }

            Path javaPath = getJavaExecutablePath();
            if (javaPath == null || !Files.exists(javaPath)) {
                logger.warning(tr("message.strategy.velocity.error.executable-java-missing"));
                return;
            }

            Files.createDirectories(pendingDir);

            List<String> command = new ArrayList<>();
            command.add(javaPath.toString());
            command.add("-cp");
            command.add(selfJar.toAbsolutePath().toString());
            command.add(APPLIER_MAIN_CLASS);
            command.add(pendingDir.toAbsolutePath().toString());

            for (PendingOperation operation : operations) {
                command.add("--op");
                command.add(operation.pluginId);
                command.add(operation.targetPath);
                command.add(operation.sourcePath);
            }

            Process process = new ProcessBuilder(command)
                    .directory(plugin.getDataPath().toFile())
                    .start();

            logger.info(tr("message.strategy.velocity.launched", process.pid()));
            synchronized (pendingOperations) {
                pendingOperations.clear();
            }
        } catch (Exception e) {
            logger.warning(tr("message.strategy.velocity.process.exception", e));
        }
    }

    private Path getJavaExecutablePath() {
        try {
            var currentCommand = ProcessHandle.current().info().command();
            if (currentCommand.isPresent()) {
                Path currentJava = Path.of(currentCommand.get());
                if (Files.exists(currentJava)) {
                    return currentJava;
                }
            }
        } catch (Exception ignored) {
        }

        String javaHome = System.getProperty("java.home");
        if (javaHome == null || javaHome.isBlank()) {
            return null;
        }

        Path javaBin = Paths.get(javaHome, "bin", "java");
        if (Files.exists(javaBin)) {
            return javaBin;
        }

        Path javaBinExe = Paths.get(javaHome, "bin", "java.exe");
        if (Files.exists(javaBinExe)) {
            return javaBinExe;
        }

        return null;
    }


    record PendingOperation(
            String pluginId,
            String targetPath,
            String sourcePath
    ) { }
}
