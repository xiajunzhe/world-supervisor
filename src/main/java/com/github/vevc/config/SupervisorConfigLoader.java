package com.github.vevc.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * @author vevc
 */
public final class SupervisorConfigLoader {

    private static final String KEY_PROGRAMS = "programs";
    private static final String KEY_NAME = "name";
    private static final String KEY_COMMAND = "command";
    private static final String KEY_AUTOSTART = "autostart";
    private static final String KEY_AUTORESTART = "autorestart";
    private static final String KEY_START_RETRIES = "startretries";
    private static final String KEY_EXIT_CODES = "exitcodes";
    private static final String KEY_STOP_WAIT_SECS = "stopwaitsecs";
    private static final String KEY_DIRECTORY = "directory";
    private static final String KEY_LOGFILE = "logfile";
    private static final String KEY_RESTART_DELAY_SECS = "restart_delay_secs";
    private static final String KEY_FAILURE_RETRY_DELAY_SECS = "failure_retry_delay_secs";
    private static final String KEY_ENVIRONMENT = "environment";

    private static final boolean DEFAULT_AUTOSTART = true;
    private static final RestartPolicy DEFAULT_RESTART_POLICY = RestartPolicy.UNEXPECTED;
    private static final int DEFAULT_START_RETRIES = 3;
    private static final List<Integer> DEFAULT_EXIT_CODES = List.of(0);
    private static final int DEFAULT_STOP_WAIT_SECS = 10;
    private static final String DEFAULT_DIRECTORY = ".";
    private static final String DEFAULT_LOGFILE = "{name}.log";
    private static final int DEFAULT_RESTART_DELAY_SECS = 3;
    private static final int DEFAULT_FAILURE_RETRY_DELAY_SECS = 5;

    private SupervisorConfigLoader() {
    }

    public static SupervisorConfig load(File configFile, Logger logger) throws IOException {
        ensureConfigFile(configFile, logger);

        YamlConfiguration root = YamlConfiguration.loadConfiguration(configFile);

        List<ProgramDefinition> programs = new ArrayList<>();
        List<Map<?, ?>> rawPrograms = root.getMapList(KEY_PROGRAMS);
        for (Map<?, ?> rawProgram : rawPrograms) {
            ProgramDefinition program = parseProgram(rawProgram);
            if (program != null) {
                programs.add(program);
            } else {
                logger.warning("Skipping invalid program entry: " + rawProgram);
            }
        }
        return new SupervisorConfig(programs);
    }

    private static void ensureConfigFile(File configFile, Logger logger) throws IOException {
        if (configFile.exists()) {
            return;
        }
        File parent = configFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        Files.writeString(configFile.toPath(), defaultConfigContent());
        logger.info("Generated default supervisor.yml config");
    }

    private static ProgramDefinition parseProgram(Map<?, ?> raw) {
        String name = text(raw.get(KEY_NAME));
        List<String> command = toStringList(raw.get(KEY_COMMAND));
        if (name == null || name.isBlank() || command.isEmpty()) {
            return null;
        }

        boolean autostart = toBoolean(raw.get(KEY_AUTOSTART), DEFAULT_AUTOSTART);
        RestartPolicy restartPolicy = RestartPolicy.parse(textOr(raw.get(KEY_AUTORESTART), DEFAULT_RESTART_POLICY.name()));
        int startRetries = Math.max(0, toInt(raw.get(KEY_START_RETRIES), DEFAULT_START_RETRIES));
        List<Integer> exitCodes = toIntegerList(raw.get(KEY_EXIT_CODES));
        if (exitCodes.isEmpty()) {
            exitCodes = DEFAULT_EXIT_CODES;
        }
        int stopWaitSecs = Math.max(1, toInt(raw.get(KEY_STOP_WAIT_SECS), DEFAULT_STOP_WAIT_SECS));
        String directory = textOr(raw.get(KEY_DIRECTORY), DEFAULT_DIRECTORY);

        String rawLogFile = textOr(raw.get(KEY_LOGFILE), DEFAULT_LOGFILE);
        String logFile = rawLogFile.replace("{name}", name);

        int restartDelaySecs = Math.max(0, toInt(raw.get(KEY_RESTART_DELAY_SECS), DEFAULT_RESTART_DELAY_SECS));
        int failureRetryDelaySecs = Math.max(0, toInt(raw.get(KEY_FAILURE_RETRY_DELAY_SECS), DEFAULT_FAILURE_RETRY_DELAY_SECS));
        Map<String, String> environment = toStringMap(raw.get(KEY_ENVIRONMENT));

        return new ProgramDefinition(
                name,
                command,
                autostart,
                restartPolicy,
                startRetries,
                exitCodes,
                stopWaitSecs,
                directory,
                logFile,
                restartDelaySecs,
                failureRetryDelaySecs,
                environment
        );
    }

    private static String defaultConfigContent() {
        return """
                programs:
                  # Empty by default
                  # Bootstrap script should generate concrete programs for each environment
                  # Example:
                  # - name: sample-service
                  #   command: ["/bin/sh", "/path/to/run.sh"]
                  #   autorestart: unexpected
                  []
                """;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value).trim();
    }

    private static String textOr(Object value, String fallback) {
        String parsed = text(value);
        return parsed == null || parsed.isBlank() ? fallback : parsed;
    }

    private static boolean toBoolean(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static int toInt(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static List<String> toStringList(Object value) {
        if (!(value instanceof List<?> listValue)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : listValue) {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    private static List<Integer> toIntegerList(Object value) {
        if (!(value instanceof List<?> listValue)) {
            return List.of();
        }
        List<Integer> result = new ArrayList<>();
        for (Object item : listValue) {
            if (item instanceof Number numberValue) {
                result.add(numberValue.intValue());
                continue;
            }
            if (item == null) {
                continue;
            }
            try {
                result.add(Integer.parseInt(String.valueOf(item)));
            } catch (NumberFormatException ignored) {
                // skip invalid integer
            }
        }
        return result;
    }

    private static Map<String, String> toStringMap(Object value) {
        if (!(value instanceof Map<?, ?> mapValue)) {
            return Map.of();
        }
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                result.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }
        return result;
    }
}
