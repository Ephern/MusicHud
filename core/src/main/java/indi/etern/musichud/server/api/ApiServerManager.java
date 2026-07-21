package indi.etern.musichud.server.api;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.interfaces.*;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.utils.http.ApiClient;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@RegisterMark
public class ApiServerManager implements ServerRegister {
    private static final ServerConfig serverConfig = ServerConfig.getInstance();
    private static final Path LOG_DIR = Paths.get("music-hud", "logs");
    private static final DateTimeFormatter LOG_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static ClientConfig clientConfig;
    @Getter
    private static ApiServerManager instance;

    static {
        if (MusicHud.getCurrentEnvironment().getSide() == Environment.Side.CLIENT) {
            try {
                clientConfig = ClientConfig.getInstance();
            } catch (UnsupportedOperationException e) {
                clientConfig = null;
            }
        }
    }

    private final Logger apiLogger = LogManager.getLogger(MusicHud.LOGGER_BASE_NAME + "/API");
    @Getter
    private final List<Consumer<BinaryApiServerStatus>> apiStatusListeners = new ArrayList<>();
    private volatile Process process;
    @Getter
    private BinaryApiServerStatus binaryApiServerStatus = BinaryApiServerStatus.STOPPED;
    private int triedCount = 0;
    private boolean initialized = false;
    private Thread hook;
    private CompletableFuture<Integer> processFuture;
    private boolean continueRestart;

    public void clearLogs() {
        try (var stream = Files.list(getLogDir())) {
            stream.filter(p -> p.getFileName().toString().endsWith(".log"))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    public void log(String s, boolean error) {
        if (error || s.contains("ERROR")) {
            apiLogger.error(s.replace("[ERROR]", ""));
        } else {
            apiLogger.debug(s.replace("[INFO]", ""));
        }
    }

    @Override
    public void register() {
        instance = this;
        if (initialized) {
            return;
        }
        initialized = true;
        if (MusicHud.getCurrentEnvironment().getSide() == Environment.Side.CLIENT && !clientConfig.getEnabledInIntegratedServer()) {
            return;
        }
        if (serverConfig.getStartupBinaryApiServerWhenLaunch()) {
            launchApiServerInternal();
        }
    }

    public void stopApiServer() {
        if (process != null) {
            continueRestart = false;
            process.destroy();
            process = null;
            removeShutdownHook();
        }
    }

    public void restartApiServer() {
        triedCount = 0;
        stopApiServer();
        if (processFuture != null) {
            processFuture.thenRun(this::launchApiServerInternal);
        } else {
            launchApiServerInternal();
        }
    }

    private void addShutdownHook() {
        if (hook == null) {
            hook = new Thread(this::stopApiServer);
            ICommonEventService.getInstance().registerCommonLifecycleStopping(this::stopApiServer);
            Runtime.getRuntime().addShutdownHook(hook);
        }
    }

    private void removeShutdownHook() {
        if (hook != null) {
            Runtime.getRuntime().removeShutdownHook(hook);
            hook = null;
        }
    }

    private void launchApiServerInternal() {
        MusicHud.EXECUTOR.execute(() -> {
            Thread.currentThread().setName("MHWorker-API-Launcher");
            boolean apiAvailable = ApiClient.checkAvailable();
            if (!apiAvailable) {
                triedCount = 0;
                startEmbeddedApiServer();
                Environment.Side side = MusicHud.getCurrentEnvironment().getSide();
                if (side == Environment.Side.CLIENT) {
                    addShutdownHook();
                } else if (side == Environment.Side.SERVER) {
                    addShutdownHook();
                }
            } else {
                apiLogger.info("API Server (version: {}) has been launched externally", ApiClient.getVersion());
            }
        });
    }

    private synchronized void startEmbeddedApiServer() {
        if (process != null) {
            return;
        }
        int maxTries = 5;
        if (triedCount >= maxTries) {
            apiLogger.error("Embedded API Server has been stopped due to maximum tries reached.");
            return;
        }
        String binaryExecutableApiServerPathString = serverConfig.getServerApiBinaryExecutablePath();
        Path binaryExecutableApiServerPath = Paths.get(binaryExecutableApiServerPathString);
        Path windowsExePath = Paths.get(binaryExecutableApiServerPathString + ".exe");
        boolean executable = Files.isExecutable(binaryExecutableApiServerPath) || Files.isExecutable(windowsExePath);
        boolean exists = Files.exists(binaryExecutableApiServerPath) || Files.exists(windowsExePath);
        if (exists) {
            if (executable) {
                triedCount++;
                try {
                    continueRestart = true;
                    setApiStatus(BinaryApiServerStatus.LAUNCHING);

                    Path executablePath;
                    if (Files.exists(windowsExePath) && Files.isExecutable(windowsExePath)) {
                        executablePath = windowsExePath;
                    } else {
                        executablePath = binaryExecutableApiServerPath;
                    }

                    ProcessBuilder processBuilder = new ProcessBuilder(executablePath.toString());
                    Map<String, String> env = processBuilder.environment();
                    env.put("CORS_ALLOW_ORIGIN", serverConfig.getCorsAllowOrigin());
                    env.put("ENABLE_PROXY", String.valueOf(serverConfig.getEnableProxy()));
                    env.put("PROXY_URL", serverConfig.getProxyUrl());
                    env.put("ENABLE_RANDOM_CN_IP", String.valueOf(serverConfig.getUseRandomCnIp()));
                    env.put("ENABLE_GENERAL_UNBLOCK", String.valueOf(serverConfig.getEnableGeneralUnblock()));
                    env.put("ENABLE_FLAC", String.valueOf(serverConfig.getEnableFlac()));
                    env.put("SELECT_MAX_BR", String.valueOf(serverConfig.getSelectMaxBr()));
                    env.put("FOLLOW_SOURCE_ORDER", String.valueOf(serverConfig.getFollowSourceOrder()));
                    env.put("PORT", String.valueOf(serverConfig.getPort()));
                    process = processBuilder.start();

                    Path logFile;
                    PrintWriter logWriter = null;
                    try {
                        Files.createDirectories(LOG_DIR);
                        logFile = LOG_DIR.resolve("api-server-" + LocalDateTime.now().format(LOG_TIMESTAMP) + ".log");
                        logWriter = new PrintWriter(new FileWriter(logFile.toFile(), true), true);
                    } catch (IOException e) {
                        apiLogger.error("Failed to create log file", e);
                    }

                    final PrintWriter writer = logWriter;

                    CompletableFuture<Integer> future = new CompletableFuture<>();
                    processFuture = future;

                    MusicHud.EXECUTOR.execute(() -> {
                        Thread.currentThread().setName("MHWorker-API-Console");
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (writer != null) writer.println(line);
                                if ((line.contains("Server started successfully") || line.contains("ncm_api_rs::server"))
                                        && binaryApiServerStatus == BinaryApiServerStatus.LAUNCHING) {
                                    boolean available = ApiClient.checkAvailable();
                                    setApiStatus(BinaryApiServerStatus.RUNNING);
                                    if (available) {
                                        apiLogger.info("Api server started, version: {}", ApiClient.getVersion());
                                        try {
                                            Path execPath = Paths.get(serverConfig.getServerApiBinaryExecutablePath());
                                            Path parent = execPath.getParent();
                                            if (parent != null) {
                                                ApiBinaryUpdateService.getInstance().fixUnknownVersion(
                                                        parent, ApiClient.getVersion());
                                            }
                                        } catch (Exception ignored) {
                                        }
                                    } else {
                                        apiLogger.info("Api server started, but unavailable, restarting");
                                        restartApiServer();
                                        return;
                                    }
                                }
                                log(line, false);
                            }
                        } catch (IOException e) {
                            apiLogger.error("Error reading stdout", e);
                        }
                    });

                    MusicHud.EXECUTOR.execute(() -> {
                        Thread.currentThread().setName("MHWorker-API-Console");
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (writer != null) writer.println(line);
                                log(line, true);
                            }
                        } catch (IOException e) {
                            apiLogger.error("Error reading stderr", e);
                        }
                    });

                    MusicHud.EXECUTOR.execute(() -> {
                        Thread.currentThread().setName("MHWorker-API-Daemon");
                        try {
                            int exitCode = process.waitFor();
                            if (writer != null) writer.close();
                            setApiStatus(BinaryApiServerStatus.STOPPED);
                            if (continueRestart) {
                                apiLogger.warn("Api server unexpectedly stopped with code:{}, restarting...", exitCode);
                                future.complete(exitCode);
                                startEmbeddedApiServer();
                            } else {
                                apiLogger.info("Api server stopped with code:{}", exitCode);
                                future.complete(exitCode);
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            apiLogger.error("Process wait interrupted", e);
                            future.completeExceptionally(e);
                        }
                    });
                } catch (Exception e) {
                    MusicHud.LOGGER.error("Failed to call binary server at path: \"{}\"", binaryExecutableApiServerPathString, e);
                    if (processFuture != null) {
                        processFuture.completeExceptionally(e);
                    }
                }
            }
        }
    }

    public Path getLogDir() {
        return LOG_DIR;
    }

    public long[] getLogStats() {
        try {
            Files.createDirectories(LOG_DIR);
        } catch (IOException ignored) {
        }
        try (var stream = Files.list(LOG_DIR)) {
            long[] result = {0, 0};
            stream.filter(p -> p.getFileName().toString().endsWith(".log")).forEach(p -> {
                result[0]++;
                try {
                    result[1] += Files.size(p);
                } catch (IOException ignored) {
                }
            });
            return result;
        } catch (IOException e) {
            return new long[]{0, 0};
        }
    }

    private void setApiStatus(BinaryApiServerStatus status) {
        binaryApiServerStatus = status;
        apiStatusListeners.forEach(l -> l.accept(status));
    }

    public enum BinaryApiServerStatus {
        STOPPED, LAUNCHING, RUNNING;

        public String i18nKey() {
            return MusicHud.MOD_ID + ".text.binaryApiServerStatus." + name();
        }
    }
}
