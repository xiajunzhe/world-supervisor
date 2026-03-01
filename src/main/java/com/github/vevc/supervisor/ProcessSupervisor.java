package com.github.vevc.supervisor;

import com.github.vevc.config.ProgramDefinition;
import com.github.vevc.config.RestartPolicy;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author vevc
 */
public final class ProcessSupervisor {

    private final JavaPlugin plugin;
    private final ProgramDefinition program;
    private final AtomicBoolean keepRunning = new AtomicBoolean(true);

    private volatile Process currentProcess;
    private Thread monitorThread;

    public ProcessSupervisor(JavaPlugin plugin, ProgramDefinition program) {
        this.plugin = plugin;
        this.program = program;
    }

    public void start() {
        monitorThread = Thread.ofVirtual()
                .name("supervisor-" + program.name())
                .start(this::runLoop);
        plugin.getLogger().info("Program started: " + program.name());
    }

    public void stop() {
        keepRunning.set(false);
        if (monitorThread != null) {
            monitorThread.interrupt();
        }
        stopProcessTree(currentProcess, program.stopWaitSeconds());
    }

    private void runLoop() {
        int retryCount = 0;
        while (keepRunning.get()) {
            try {
                int exitCode = startAndWaitProcess();

                if (!keepRunning.get()) {
                    break;
                }

                boolean isExpectedExit = program.exitCodes().contains(exitCode);
                if (!shouldRestart(isExpectedExit)) {
                    plugin.getLogger().info("Program exited without restart: " + program.name() + " (exitCode=" + exitCode + ")");
                    break;
                }

                if (!canRetry(retryCount, "Program retry limit reached")) {
                    break;
                }

                retryCount++;
                plugin.getLogger().warning("Program exited unexpectedly: " + program.name() + " (exitCode=" + exitCode + "). Retrying in " + program.restartDelaySeconds() + "s");
                if (!sleepOrStop(program.restartDelaySeconds())) {
                    break;
                }
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception exception) {
                plugin.getLogger().severe("Program execution failed: " + program.name() + " (" + exception.getMessage() + ")");
                if (!keepRunning.get()) {
                    break;
                }
                if (!canRetry(retryCount, "Program retry limit reached after exception")) {
                    break;
                }
                retryCount++;
                if (!sleepOrStop(program.failureRetryDelaySeconds())) {
                    break;
                }
            }
        }
    }

    private int startAndWaitProcess() throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(program.command());
        processBuilder.directory(resolveWorkDirectory());
        configureLogging(processBuilder);
        processBuilder.environment().putAll(program.environment());

        currentProcess = processBuilder.start();
        try {
            return currentProcess.waitFor();
        } finally {
            currentProcess = null;
        }
    }

    private void configureLogging(ProcessBuilder processBuilder) {
        String configuredLogFile = program.logFile();
        if (configuredLogFile == null
                || configuredLogFile.isBlank()
                || "/dev/null".equalsIgnoreCase(configuredLogFile)) {
            processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);
            return;
        }

        File logFile = new File(configuredLogFile);
        if (!logFile.isAbsolute()) {
            logFile = new File(plugin.getDataFolder(), configuredLogFile);
        }
        File parent = logFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));
        processBuilder.redirectError(ProcessBuilder.Redirect.appendTo(logFile));
    }

    private File resolveWorkDirectory() {
        String configuredDirectory = program.directory();
        if (configuredDirectory == null || configuredDirectory.isBlank() || ".".equals(configuredDirectory)) {
            return plugin.getDataFolder();
        }

        File directory = new File(configuredDirectory);
        if (!directory.isAbsolute()) {
            directory = new File(plugin.getDataFolder(), configuredDirectory);
        }
        if (!directory.exists()) {
            directory.mkdirs();
        }
        return directory;
    }

    private boolean shouldRestart(boolean expectedExit) {
        RestartPolicy restartPolicy = program.restartPolicy();
        return switch (restartPolicy) {
            case ALWAYS -> true;
            case NEVER -> false;
            case UNEXPECTED -> !expectedExit;
        };
    }

    private void stopProcessTree(Process process, int stopWaitSeconds) {
        if (process == null || !process.isAlive()) {
            return;
        }

        plugin.getLogger().info("Stopping process tree: " + program.name());
        ProcessHandle processHandle = process.toHandle();
        processHandle.descendants().forEach(ProcessHandle::destroy);
        processHandle.destroy();

        try {
            boolean stopped = process.waitFor(Math.max(1, stopWaitSeconds), TimeUnit.SECONDS);
            if (!stopped) {
                plugin.getLogger().warning("Force killing process tree: " + program.name());
                processHandle.descendants().forEach(ProcessHandle::destroyForcibly);
                processHandle.destroyForcibly();
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private void sleepSeconds(int seconds) throws InterruptedException {
        if (seconds <= 0) {
            return;
        }
        Thread.sleep(Duration.ofSeconds(seconds));
    }

    private boolean sleepOrStop(int seconds) {
        try {
            sleepSeconds(seconds);
            return true;
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean canRetry(int retryCount, String warningPrefix) {
        if (retryCount < program.startRetries()) {
            return true;
        }
        plugin.getLogger().warning(warningPrefix + ": " + program.name() + " (maxRetries=" + program.startRetries() + ")");
        return false;
    }
}
