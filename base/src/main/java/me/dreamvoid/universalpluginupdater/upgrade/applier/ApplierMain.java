package me.dreamvoid.universalpluginupdater.upgrade.applier;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 独立的升级执行入口<br>
 * 此包内的类会被单独解压执行，仅可使用 JDK API
 */
public final class ApplierMain {
    private static final DateTimeFormatter LOG_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static Path workingDir;

    private ApplierMain() {}

    /**
     * 执行升级操作
     *
     * @param args 第一个参数为工作目录，其余为 {@code --op} 操作参数
     */
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
            operations.add(new PendingOperation(
                    pluginId,
                    Paths.get(newFilePath),
                    Paths.get(targetPath),
                    oldFilePath.isBlank() ? null : Paths.get(oldFilePath)
            ));
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
            archiveLogPath = logsDir.resolve(date + "-" + index++ + ".log");
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
                if (oldFilePath != null) {
                    log(operation.pluginId() + ": 删除旧文件" + oldFilePath);
                    Files.deleteIfExists(oldFilePath);
                }
                Files.createDirectories(targetPath.getParent());
                Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                log(operation.pluginId() + ": 移动文件" + sourcePath + "到" + targetPath);
                return true;
            } catch (Exception e) {
                log(operation.pluginId() + ": 第" + (retry + 1) + "次尝试失败！source=" + sourcePath + ", target=" + targetPath);
                log(operation.pluginId() + ": " + e);
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
                    "[" + LocalDateTime.now().format(LOG_TIME_FORMATTER) + "] " + message + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (Exception ignored) {}
    }
}
