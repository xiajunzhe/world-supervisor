package com.github.vevc.config;

import java.util.List;

/**
 * @author vevc
 */
public record SupervisorConfig(List<ProgramDefinition> programs) {

    public SupervisorConfig(List<ProgramDefinition> programs) {
        this.programs = List.copyOf(programs);
    }
}
