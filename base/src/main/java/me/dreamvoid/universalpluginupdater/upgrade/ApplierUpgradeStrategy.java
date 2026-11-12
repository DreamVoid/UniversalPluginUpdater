package me.dreamvoid.universalpluginupdater.upgrade;

import me.dreamvoid.universalpluginupdater.Utils;
import me.dreamvoid.universalpluginupdater.platform.Platform;
import me.dreamvoid.universalpluginupdater.upgrade.applier.PendingOperation;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static me.dreamvoid.universalpluginupdater.service.LanguageManager.tr;

/**
 * 延迟应用升级策略<br>
 * 在插件卸载阶段启动独立进程，待服务端进程结束后替换插件文件
 *
 * @author DreamVoid
 */
public class ApplierUpgradeStrategy implements UpgradeStrategy {
    // 使用字符串常量，避免缺少 Applier 包时在类初始化阶段失败
    private static final String APPLIER_MAIN_CLASS = "me.dreamvoid.universalpluginupdater.upgrade.applier.ApplierMain";
    private static final String APPLIER_PACKAGE_PATH = APPLIER_MAIN_CLASS.substring(0, APPLIER_MAIN_CLASS.lastIndexOf('.')).replace('.', '/');
    private static final String APPLIER_MAIN_FILE = APPLIER_MAIN_CLASS.replace('.', '/') + ".class";

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
    public ExecutionPhase getExecutionPhase() {
        return ExecutionPhase.SHUTDOWN;
    }

    @NotNull
    @Override
    public List<UpgradeResult> upgrade(@NotNull List<UpgradeTask> tasks) {
        if (tasks.isEmpty()) {
            return List.of();
        }

        Path pluginDirectory = plugin.getDataPath().getParent();
        if (pluginDirectory == null) {
            logger.warning(tr("message.strategy.applier.error.plugin-directory-missing"));
            return tasks.stream().map(task -> new UpgradeResult(task.pluginId(), false)).toList();
        }

        try {
            synchronized (pendingOperations) {
                for (UpgradeTask task : tasks) {
                    Path targetPath = pluginDirectory.resolve(task.newFilePath().getFileName());
                    pendingOperations.add(new PendingOperation(task.pluginId(), task.newFilePath(), targetPath, task.oldFilePath()));
                }
            }

            logger.info(tr("message.strategy.applier.scheduled", tasks.size()));

            // --now 在运行期调用时与关闭阶段调用一样，都需要立刻启动外部进程
            boolean launched = launch();
            List<UpgradeResult> results = new ArrayList<>(tasks.size());
            for (UpgradeTask task : tasks) {
                results.add(new UpgradeResult(task.pluginId(), launched));
            }
            return results;
        } catch (Exception e) {
            logger.warning(tr("message.strategy.applier.exception", tasks.size(), e));
            List<UpgradeResult> results = new ArrayList<>(tasks.size());
            for (UpgradeTask task : tasks) {
                results.add(new UpgradeResult(task.pluginId(), false));
            }
            return results;
        }
    }

    private boolean launch() {
        try {
            List<PendingOperation> operations;
            synchronized (pendingOperations) {
                operations = new ArrayList<>(pendingOperations);
            }

            if (operations.isEmpty()) {
                return false;
            }

            Path selfJar = plugin.getPluginFile("UniversalPluginUpdater");
            if (selfJar == null || !Files.isRegularFile(selfJar)) {
                logger.warning(tr("message.strategy.applier.error.self-jar-missing"));
                return false;
            }

            Path javaPath = getJavaExecutablePath();
            if (javaPath == null || !Files.exists(javaPath)) {
                logger.warning(tr("message.strategy.applier.error.executable-java-missing"));
                return false;
            }

            Files.createDirectories(pendingDir);
            Path applierClasspath = null; // 不能直接用自身 Jar 作为 classpath，否则 applier 进程会一直占用该文件

            // 清理之前放置的临时目录
            try (Stream<Path> stream = Files.list(pendingDir)) {
                for (Path path : stream.toList()) {
                    String name = path.getFileName().toString();
                    if (name.startsWith("applier-runtime-") && Files.isDirectory(path) ||
                            name.startsWith("applier-") && name.endsWith(".jar") && Files.isRegularFile(path)) {
                        deleteRecursively(path);
                    }
                }
            } catch (Exception e1) {
                Utils.debug("Failed to clean stale applier artifacts in {0}: {1}", pendingDir, e1);
            }

            Path runtimeDir = pendingDir.resolve("applier-runtime-" + System.currentTimeMillis());
            Files.createDirectories(runtimeDir);

            try (JarFile jar = new JarFile(selfJar.toFile())) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (!entry.isDirectory() && name.startsWith(APPLIER_PACKAGE_PATH + "/") && name.endsWith(".class")) {
                        Path target = runtimeDir.resolve(name);
                        Files.createDirectories(target.getParent());
                        try (InputStream in = jar.getInputStream(entry)) {
                            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
            }

            if (Files.isRegularFile(runtimeDir.resolve(APPLIER_MAIN_FILE))) {
                applierClasspath = runtimeDir;
            } else {
                deleteRecursively(runtimeDir);

                Utils.debug("无法从文件 {0} 解压出更新类，改为复制插件文件本身", selfJar);
                Path copy = pendingDir.resolve("applier-" + System.currentTimeMillis() + ".jar");
                Files.copy(selfJar, copy, StandardCopyOption.REPLACE_EXISTING);
                applierClasspath = copy;
            }

            if(!Files.exists(applierClasspath)){
                logger.warning(tr("message.strategy.applier.error.class-missing"));
                return false;
            }

            List<String> command = new ArrayList<>();
            command.add(javaPath.toString());
            command.add("-cp");
            command.add(applierClasspath.toAbsolutePath().toString());
            command.add(APPLIER_MAIN_CLASS);
            command.add(pendingDir.toAbsolutePath().toString());

            for (PendingOperation operation : operations) {
                command.add("--op");
                command.add(operation.pluginId());
                command.add(operation.newFilePath().toAbsolutePath().toString());
                command.add(operation.targetPath().toAbsolutePath().toString());
                command.add(operation.oldFilePath() != null ? operation.oldFilePath().toAbsolutePath().toString() : "");
            }

            Process process = new ProcessBuilder(command)
                    .directory(plugin.getDataPath().toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(pendingDir.resolve("applier-bootstrap.log").toFile()))
                    .start();

            logger.info(tr("message.strategy.applier.process.launched", String.valueOf(process.pid())));
            synchronized (pendingOperations) {
                pendingOperations.clear();
            }
            return true;
        } catch (Exception e) {
            logger.warning(tr("message.strategy.applier.process.exception", e));
            return false;
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

    private static void deleteRecursively(Path path) {
        if (!Files.exists(path)) return;

        try (Stream<Path> stream = Files.walk(path)) {
            for (Path current : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(current);
            }
        } catch (Exception ignored) {
        }
    }
}
