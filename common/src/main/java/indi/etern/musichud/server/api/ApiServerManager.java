package indi.etern.musichud.server.api;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.interfaces.*;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.utils.http.ApiClient;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@RegisterMark
public class ApiServerManager implements ServerRegister {
    private static final ServerConfig serverConfig = ServerConfig.getInstance();
    private static ClientConfig clientConfig;
    @Getter
    private static ApiServerManager instance;
    private final Logger apiLogger = LogManager.getLogger(MusicHud.LOGGER_BASE_NAME + "/API");
    @Getter
    private final List<Consumer<BinaryApiServerStatus>> apiStatusListeners = new ArrayList<>();
    private volatile Process process;
    private boolean continueRestart = true;
    @Getter
    private BinaryApiServerStatus binaryApiServerStatus = BinaryApiServerStatus.STOPPED;
    private int triedCount = 0;
    private boolean initialized = false;
    private Thread hook;

    static {
        if (MusicHud.getCurrentEnvironment().getSide() == Environment.Side.CLIENT) {
            try {
                clientConfig = ClientConfig.getInstance();
            } catch (UnsupportedOperationException e) {
                clientConfig = null;
            }
        }
    }

    public void log(String s, boolean error) {
        if (error || s.contains("[ERROR]")) {
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
        launchApiServerInternal();
    }

    private void addShutdownHook() {
        if (hook == null) {//first call
            hook = new Thread(this::stopApiServer);
            if (MusicHud.getCurrentEnvironment().getSide() == Environment.Side.CLIENT) {
                IClientEventService.getInstance().registerClientLifecycleStopping(this::stopApiServer);
            } else {
                IServerEventService.getInstance().registerServerLifecycleStopping(this::stopApiServer);
            }
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
                    process = Runtime.getRuntime().exec(new String[]{binaryExecutableApiServerPath.toString()});

                    // 使用虚拟线程池分别读取 stdout 和 stderr
                    MusicHud.EXECUTOR.execute(() -> {
                        Thread.currentThread().setName("MHWorker-API-Console");
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.contains("Server started successfully") && binaryApiServerStatus == BinaryApiServerStatus.LAUNCHING) {
                                    setApiStatus(BinaryApiServerStatus.RUNNING);
                                    boolean available = ApiClient.checkAvailable();
                                    if (available) {
                                        apiLogger.info("Api server started, version: {}", ApiClient.getVersion());
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
                                log(line, true);
                            }
                        } catch (IOException e) {
                            apiLogger.error("Error reading stderr", e);
                        }
                    });

                    // 在另一个虚拟线程中等待进程结束，并处理重启逻辑
                    MusicHud.EXECUTOR.execute(() -> {
                        Thread.currentThread().setName("MHWorker-API-Daemon");
                        try {
                            int exitCode = process.waitFor();
                            setApiStatus(BinaryApiServerStatus.STOPPED);
                            if (continueRestart) {
                                apiLogger.warn("Api server unexpectedly stopped with code:{}, restarting...", exitCode);
                                startEmbeddedApiServer();
                            } else {
                                apiLogger.info("Api server stopped with code:{}", exitCode);
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            apiLogger.error("Process wait interrupted", e);
                        }
                    });
                } catch (Exception e) {
                    MusicHud.LOGGER.error("Failed to call binary server at path: \"{}\"", binaryExecutableApiServerPathString, e);
                }
            }
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
