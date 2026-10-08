package me.dreamvoid.universalpluginupdater.upgrade;

import me.dreamvoid.universalpluginupdater.platform.Platform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static me.dreamvoid.universalpluginupdater.service.LanguageManager.tr;

public class ApplierUpgradeStrategy implements UpgradeStrategy {
    private static final String APPLIER_MAIN_CLASS = ApplierUpgradeStrategy.Main.class.getName();

    private final Platform plugin;
    private final Logger logger;
    private final Path pendingDir;
    private final List<PendingOperation> pendingOperations = new ArrayList<>();

    public ApplierUpgradeStrategy(Platform plugin) {
        this.plugin = plugin;
        this.logger = plugin.getPlatformLogger();
        this.pendingDir = plugin.getDataPath().resolve("pending-upgrades");
    }

    @Override
    public @NotNull String getId() {
        return "applier";
    }

    @Override
    public String getName() {
        return "延迟应用";
    }

    @Override
    public boolean supportSafeUpgrade() {
        return true;
    }

    @Override
    public boolean upgrade(String pluginId, Path newFilePath, @Nullable Path oldFilePath) {
        try {
            Path targetPath = plugin.getDataPath().getParent().resolve(newFilePath.getFileName());

            synchronized (pendingOperations) {
                pendingOperations.add(new PendingOperation(
                    pluginId,
                        newFilePath, targetPath,
                        oldFilePath
                ));
            }

            logger.info(tr("message.strategy.applier.scheduled", pluginId));
            return true;
        } catch (Exception e) {
            logger.warning(tr("message.strategy.applier.exception", pluginId, e));
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

            Path selfJar = plugin.getPluginFile("UniversalPluginUpdater");
            if (selfJar == null || !Files.exists(selfJar)) {
                logger.warning(tr("message.strategy.applier.error.self-jar-missing"));
                return;
            }

            Path javaPath = getJavaExecutablePath();
            if (javaPath == null || !Files.exists(javaPath)) {
                logger.warning(tr("message.strategy.applier.error.executable-java-missing"));
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
                command.add(operation.newFilePath.toAbsolutePath().toString());
                command.add(operation.targetPath.toAbsolutePath().toString());
                command.add(operation.oldFilePath.toAbsolutePath().toString());
            }

            Process process = new ProcessBuilder(command)
                    .directory(plugin.getDataPath().toFile())
                    .start();

            logger.info(tr("message.strategy.applier.process.launched", String.valueOf(process.pid())));
            synchronized (pendingOperations) {
                pendingOperations.clear();
            }
        } catch (Exception e) {
            logger.warning(tr("message.strategy.applier.process.exception", e));
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
            Path newFilePath, 
            Path targetPath,
            Path oldFilePath
    ) { }

    public static final class Main {
        private static final DateTimeFormatter LOG_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        private static Path workingDir;

        private Main() {}

        public static void main(String[] args) {
            if (args.length < 1) {
                return;
            }

            workingDir = Paths.get(args[0]);

            try {
                archiveLogFile();
                List<PendingOperation> operations = parseArgs(args);
                if (operations.isEmpty()) {
                    log("未收到可执行的升级任务，已退出");
                    System.exit(1);
                    return;
                }

                for (PendingOperation operation : operations) {
                    if (!applyOperation(operation)) {
                        log(operation.pluginId() + ": 操作失败！");
                    }
                }
                System.exit(0);
            } catch (Exception e) {
                log("异常退出：" + e);
                System.exit(-1);
            }
        }

        private static List<PendingOperation> parseArgs(String[] args) {
            List<PendingOperation> operations = new ArrayList<>();
            for (int i = 1; i < args.length; i++) {
                if (!"--op".equals(args[i])) {
                    continue;
                }
                if (i + 4 >= args.length) {
                    break;
                }
                String pluginId = args[i + 1];
                String newFilePath = args[i + 2];
                String targetPath = args[i + 3];
                String oldFilePath = args[i + 4];
                operations.add(new PendingOperation(pluginId, Paths.get(newFilePath), Paths.get(targetPath), Paths.get(oldFilePath)));
                i += 4;
            }
            return operations;
        }

        private static void archiveLogFile() throws Exception {
            Path logFile = workingDir.resolve("applier.log");
            if (!Files.exists(logFile)) {
                return;
            }

            Path logsDir = workingDir.resolve("logs");
            Files.createDirectories(logsDir);

            String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            int index = 1;
            Path archiveLogPath;
            do {
                archiveLogPath = logsDir.resolve(date + "-" + index + ".log");
                index++;
            } while (Files.exists(archiveLogPath));

            Files.move(logFile, archiveLogPath, StandardCopyOption.REPLACE_EXISTING);
        }

        private static boolean applyOperation(PendingOperation operation) {
            Path targetPath = operation.targetPath();
            Path sourcePath = operation.newFilePath();
            Path oldFilePath = operation.oldFilePath();

            if (!Files.exists(sourcePath)) {
                return false;
            }

            for (int retry = 0; retry < 60; retry++) {
                try {
                    if(oldFilePath != null){
                        log(operation.pluginId() + ": 删除旧文件" + oldFilePath);
                        Files.deleteIfExists(oldFilePath);
                    }
                    Files.createDirectories(targetPath.getParent());
                    Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    log(operation.pluginId() + ": 移动文件" + sourcePath + "到" + targetPath);
                    return true;
                } catch (Exception e) {
                    log(operation.pluginId() + ": 第" + (retry + 1) + "次尝试失败！source=" + sourcePath + ", target=" + targetPath);
                    try {
                        Thread.sleep(1000L);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }

            return false;
        }


        private static void log(String message) {
            try {
                Files.writeString(
                        Files.createDirectories(workingDir).resolve("applier.log"),
                        LocalDateTime.now().format(LOG_TIME_FORMATTER) + " " + message + System.lineSeparator(),
                        StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.APPEND
                );
            } catch (Exception ignored) {}
        }
    }
}
