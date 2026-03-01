package com.github.vevc.config;

import java.util.Locale;

/**
 * @author vevc
 */
public enum RestartPolicy {

    /**
     * Always restart the program
     */
    ALWAYS,
    /**
     * Never restart the program
     */
    NEVER,
    /**
     * Restart the program if it exits with an unexpected exit code
     */
    UNEXPECTED;

    public static RestartPolicy parse(String value) {
        if (value == null || value.isBlank()) {
            return UNEXPECTED;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "true", "always" -> ALWAYS;
            case "false", "never" -> NEVER;
            default -> UNEXPECTED;
        };
    }
}
