package com.github.vevc.config;

import java.util.List;
import java.util.Map;

/**
 * @author vevc
 */
public record ProgramDefinition(
        String name, List<String> command, boolean autostart, RestartPolicy restartPolicy,
        int startRetries, List<Integer> exitCodes, int stopWaitSeconds, String directory,
        String logFile, int restartDelaySeconds, int failureRetryDelaySeconds,
        Map<String, String> environment) {

    public ProgramDefinition(
            String name,
            List<String> command,
            boolean autostart,
            RestartPolicy restartPolicy,
            int startRetries,
            List<Integer> exitCodes,
            int stopWaitSeconds,
            String directory,
            String logFile,
            int restartDelaySeconds,
            int failureRetryDelaySeconds,
            Map<String, String> environment
    ) {
        this.name = name;
        this.command = List.copyOf(command);
        this.autostart = autostart;
        this.restartPolicy = restartPolicy;
        this.startRetries = startRetries;
        this.exitCodes = List.copyOf(exitCodes);
        this.stopWaitSeconds = stopWaitSeconds;
        this.directory = directory;
        this.logFile = logFile;
        this.restartDelaySeconds = restartDelaySeconds;
        this.failureRetryDelaySeconds = failureRetryDelaySeconds;
        this.environment = Map.copyOf(environment);
    }
}
