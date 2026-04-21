package me.dreamvoid.universalpluginupdater.velocity.upgrade;

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

public final class VelocityUpgradeApplierMain {
    private static final DateTimeFormatter LOG_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static Path workingDir;

    private VelocityUpgradeApplierMain() {}

    public static void main(String[] args) {
        if (args.length < 1) {
            return;
        }

        workingDir = Paths.get(args[0]);

        try {
            archiveLogFile();
            List<VelocityUpgradeStrategy.PendingOperation> operations = parseArgs(args);
            if (operations.isEmpty()) {
                log("未收到可执行的升级任务，已退出");
                System.exit(1);
                return;
            }

            for (VelocityUpgradeStrategy.PendingOperation operation : operations) {
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

    private static List<VelocityUpgradeStrategy.PendingOperation> parseArgs(String[] args) {
        List<VelocityUpgradeStrategy.PendingOperation> operations = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            if (!"--op".equals(args[i])) {
                continue;
            }
            if (i + 3 >= args.length) {
                break;
            }
            String pluginId = args[i + 1];
            String targetPath = args[i + 2];
            String sourcePath = args[i + 3];
            operations.add(new VelocityUpgradeStrategy.PendingOperation(pluginId, targetPath, sourcePath));
            i += 3;
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

    private static boolean applyOperation(VelocityUpgradeStrategy.PendingOperation operation) {
        Path targetPath = Paths.get(operation.targetPath());
        Path sourcePath = Paths.get(operation.sourcePath());

        if (!Files.exists(sourcePath)) {
            return false;
        }

        for (int retry = 0; retry < 60; retry++) {
            try {
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
