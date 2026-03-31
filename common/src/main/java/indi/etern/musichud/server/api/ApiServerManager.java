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
    private static ApiServerManager register;
    private static final Logger apiLogger = LogManager.getLogger(MusicHud.LOGGER_BASE_NAME + "/API");
    private static final ServerConfig serverConfig = ServerConfig.getInstance();
    private static Process process;
    private static boolean continueRestart = true;
    @Getter
    private static BinaryApiServerStatus binaryApiServerStatus = BinaryApiServerStatus.STOPPED;
    @Getter
    private static final List<Consumer<BinaryApiServerStatus>> apiStatusListeners = new ArrayList<>();
    private static final int maxTries = 5;
    private static int triedCount = 0;

    public enum BinaryApiServerStatus {
        STOPPED, LAUNCHING, RUNNING;

        public String i18nKey() {
            return MusicHud.MOD_ID + ".text.binaryApiServerStatus." + name();
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
        register = this;
        MusicHud.EXECUTOR.execute(() -> {
            Thread.currentThread().setName("API Server Launcher");
            boolean apiAvailable = ApiClient.checkAvailable();
            if (serverConfig.getStartupBinaryApiServerWhenLaunch() && !apiAvailable) {
                triedCount = 0;
                startEmbeddedApiServer();
                Environment.Side side = MusicHud.getCurrentEnvironment().getSide();
                if (side == Environment.Side.CLIENT) {
                    IClientEventService.getInstance().registerClientLifecycleStopping(ApiServerManager::stopApiServer);
                } else if (side == Environment.Side.SERVER) {
                    IServerEventService.getInstance().registerServerLifecycleStopping(ApiServerManager::stopApiServer);
                }
            } else if (apiAvailable) {
                apiLogger.info("API Server has been launched externally");
            }
        });
    }

    public static void stopApiServer() {
        if (process != null) {
            continueRestart = false;
            process.destroy();
        }
    }

    public static void restartApiServer() {
        if (register != null) {
            triedCount = 0;
            stopApiServer();
            register.startEmbeddedApiServer();
        }
    }

    private void startEmbeddedApiServer() {
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
                        Thread.currentThread().setName("API Console");
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.contains("Server started successfully") && binaryApiServerStatus == BinaryApiServerStatus.LAUNCHING) {
                                    setApiStatus(BinaryApiServerStatus.RUNNING);
                                    apiLogger.info("Api server started");
                                }
                                log(line, false);
                            }
                        } catch (IOException e) {
                            apiLogger.error("Error reading stdout", e);
                        }
                    });

                    MusicHud.EXECUTOR.execute(() -> {
                        Thread.currentThread().setName("API Console");
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
                        Thread.currentThread().setName("API Daemon");
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
}
