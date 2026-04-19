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

    private VelocityUpgradeApplierMain() {
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            return;
        }

        Path pendingDir = Paths.get(args[0]);
        Path applierLog = pendingDir.resolve("applier.log");

        try {
            archiveOldLogIfExists(applierLog);
            List<VelocityUpgradeStrategy.PendingOperation> operations = parseOperationsFromArgs(args);
            if (operations.isEmpty()) {
                appendLog(applierLog, "未收到可执行的升级任务，已退出");
                return;
            }

            List<VelocityUpgradeStrategy.PendingOperation> failed = new ArrayList<>();

            for (VelocityUpgradeStrategy.PendingOperation operation : operations) {
                if (!applyOperation(operation)) {
                    failed.add(operation);
                }
            }

            if (failed.isEmpty()) {
                appendLog(applierLog, "延迟应用完成：所有待处理文件已替换成功");
            } else {
                for (VelocityUpgradeStrategy.PendingOperation operation : failed) {
                    appendLog(applierLog, "替换失败：插件=" + operation.pluginId() + "，目标=" + operation.targetPath() + "，来源=" + operation.sourcePath());
                }
                appendLog(applierLog, "延迟应用完成：仍有 " + failed.size() + " 个文件替换失败，已保留到清单等待下次重试");
            }
        } catch (Exception e) {
            try {
                appendLog(applierLog, "延迟应用器异常退出：" + e.getMessage());
            } catch (Exception ignored) {
            }
        }
    }

    private static List<VelocityUpgradeStrategy.PendingOperation> parseOperationsFromArgs(String[] args) {
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

    private static void archiveOldLogIfExists(Path applierLog) throws Exception {
        if (!Files.exists(applierLog)) {
            return;
        }

        Path pendingDir = applierLog.getParent();
        Path logsDir = pendingDir.resolve("logs");
        Files.createDirectories(logsDir);

        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        int index = 1;
        Path archivePath;
        do {
            archivePath = logsDir.resolve(date + "-" + index + ".log");
            index++;
        } while (Files.exists(archivePath));

        Files.move(applierLog, archivePath, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void appendLog(Path logFile, String message) throws Exception {
        Files.createDirectories(logFile.getParent());
        Files.writeString(
                logFile,
                LocalDateTime.now().format(LOG_TIME_FORMATTER) + " " + message + System.lineSeparator(),
                StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND
        );
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
                return true;
            } catch (Exception e) {
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
}
