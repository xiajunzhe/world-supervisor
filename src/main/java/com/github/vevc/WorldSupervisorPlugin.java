package com.github.vevc;

import com.github.vevc.bootstrap.BootstrapService;
import com.github.vevc.config.ProgramDefinition;
import com.github.vevc.config.SupervisorConfig;
import com.github.vevc.config.SupervisorConfigLoader;
import com.github.vevc.supervisor.ProcessSupervisor;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * @author vevc
 */
public final class WorldSupervisorPlugin extends JavaPlugin {

    private static final String CONFIG_FILE_NAME = "supervisor.yml";
    private final List<ProcessSupervisor> runningSupervisors = new ArrayList<>();

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        getLogger().info("Server process cwd: " + Path.of("").toAbsolutePath().normalize());

        File configFile = new File(getDataFolder(), CONFIG_FILE_NAME);
        if (!BootstrapService.runIfNeeded(this, configFile)) {
            getLogger().severe("Bootstrap failed, supervisor startup is skipped");
            return;
        }
        loadAndStartPrograms(configFile);
    }

    @Override
    public void onDisable() {
        getLogger().info("Stopping managed programs");
        for (ProcessSupervisor supervisor : runningSupervisors) {
            supervisor.stop();
        }
        runningSupervisors.clear();
        getLogger().info("All managed programs stopped");
    }

    private void loadAndStartPrograms(File configFile) {
        try {
            SupervisorConfig config = SupervisorConfigLoader.load(configFile, getLogger());
            if (config.programs().isEmpty()) {
                getLogger().info("No programs configured");
                return;
            }

            for (ProgramDefinition program : config.programs()) {
                if (!program.autostart()) {
                    getLogger().info("Program " + program.name() + " is configured with autostart=false");
                    continue;
                }

                ProcessSupervisor supervisor = new ProcessSupervisor(this, program);
                supervisor.start();
                runningSupervisors.add(supervisor);
            }

            getLogger().info("Loaded and started " + runningSupervisors.size() + " program supervisor(s)");
        } catch (Exception e) {
            getLogger().severe("Failed to load " + CONFIG_FILE_NAME + ": " + e.getMessage());
        }
    }
}
