package com.github.vevc.bootstrap;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * @author vevc
 */
public final class BootstrapService {

    private static final String BOOTSTRAP_DIR = "bootstrap";
    private static final String MARKER_FILE = "bootstrap.done";
    private static final String LOCK_FILE = "bootstrap.lock";
    private static final String LOG_FILE = "bootstrap.log";
    private static final String LOCAL_SCRIPT = "install.sh";
    private static final String ROOT_SCRIPT = "world-supervisor.bootstrap.sh";
    private static final Duration SCRIPT_TIMEOUT = Duration.ofMinutes(15);

    private BootstrapService() {
    }

    public static boolean runIfNeeded(JavaPlugin plugin, File configFile) {
        File dataFolder = plugin.getDataFolder();
        File bootstrapDir = new File(dataFolder, BOOTSTRAP_DIR);
        File marker = new File(bootstrapDir, MARKER_FILE);
        File lock = new File(bootstrapDir, LOCK_FILE);
        File logFile = new File(bootstrapDir, LOG_FILE);

        if (marker.exists()) {
            plugin.getLogger().info("Bootstrap marker found, skipping first-install script");
            return true;
        }

        if (!isLinux()) {
            plugin.getLogger().warning("Bootstrap script runner is Linux-only, skipping bootstrap script");
            return true;
        }

        File script = resolveScript(plugin);
        if (script == null) {
            plugin.getLogger().info("No bootstrap script found, continuing with regular configuration flow");
            return true;
        }

        if (!bootstrapDir.exists() && !bootstrapDir.mkdirs()) {
            plugin.getLogger().severe("Failed to create bootstrap directory: " + bootstrapDir.getAbsolutePath());
            return false;
        }
        if (lock.exists()) {
            plugin.getLogger().warning("Bootstrap lock detected, another installation may be in progress");
            return false;
        }

        try {
            Files.writeString(
                    lock.toPath(),
                    "startedAt=" + Instant.now() + System.lineSeparator(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );

            plugin.getLogger().info("Running bootstrap script: " + script.getAbsolutePath());
            int exitCode = runScript(plugin, script, logFile, configFile);
            if (exitCode != 0) {
                plugin.getLogger().severe("Bootstrap script failed with exit code " + exitCode + ", log: " + logFile.getAbsolutePath());
                return false;
            }

            if (!configFile.exists()) {
                plugin.getLogger().severe("Bootstrap script completed but supervisor config was not generated: " + configFile.getAbsolutePath());
                return false;
            }

            Files.writeString(
                    marker.toPath(),
                    "completedAt=" + Instant.now() + System.lineSeparator()
                            + "script=" + script.getAbsolutePath() + System.lineSeparator(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            plugin.getLogger().info("Bootstrap completed successfully");
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Bootstrap execution failed: " + e.getMessage());
            return false;
        } finally {
            try {
                Files.deleteIfExists(lock.toPath());
            } catch (IOException ignored) {
                plugin.getLogger().warning("Failed to remove bootstrap lock: " + lock.getAbsolutePath());
            }
        }
    }

    private static int runScript(JavaPlugin plugin, File script, File logFile, File configFile) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("/bin/bash", script.getAbsolutePath());
        pb.directory(plugin.getDataFolder());
        pb.redirectErrorStream(true);

        File logParent = logFile.getParentFile();
        if (logParent != null && !logParent.exists()) {
            logParent.mkdirs();
        }
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));

        pb.environment().put("WS_PROCESS_CWD", Path.of("").toAbsolutePath().normalize().toString());
        pb.environment().put("WS_PLUGIN_DIR", plugin.getDataFolder().getAbsolutePath());
        pb.environment().put("WS_CONFIG_PATH", configFile.getAbsolutePath());

        Process process = pb.start();
        boolean finished = process.waitFor(SCRIPT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            plugin.getLogger().severe("Bootstrap script timed out after " + SCRIPT_TIMEOUT.toMinutes() + " minute(s)");
            return -1;
        }
        return process.exitValue();
    }

    private static File resolveScript(JavaPlugin plugin) {
        File localScript = new File(new File(plugin.getDataFolder(), BOOTSTRAP_DIR), LOCAL_SCRIPT);
        if (localScript.exists() && localScript.isFile()) {
            return localScript;
        }

        File pluginsDir = plugin.getDataFolder().getParentFile();
        if (pluginsDir == null) {
            return null;
        }
        File rootScript = new File(pluginsDir, ROOT_SCRIPT);
        if (rootScript.exists() && rootScript.isFile()) {
            return rootScript;
        }
        return null;
    }

    private static boolean isLinux() {
        String osName = System.getProperty("os.name");
        if (osName == null) {
            return false;
        }
        return osName.toLowerCase(Locale.ROOT).contains("linux");
    }
}
