package cn.linmoyu.gureport.manager;

import cn.linmoyu.gureport.Report;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {
    public static Configuration config = null;

    public static void saveDefaultConfig() {
        final Report plugin = Report.getInstance();
        final Path dataFolder = plugin.getDataFolder().toPath();

        try {
            Files.createDirectories(dataFolder);

            final Path configPath = dataFolder.resolve("config.yml");
            if (Files.exists(configPath)) return;

            try (InputStream selfConfig = plugin.getResourceAsStream("config.yml")) {
                if (selfConfig == null) {
                    throw new IOException("§c找不到插件配置文件!");
                }

                Files.copy(selfConfig, configPath);
            }
        } catch (IOException e) {
            throw new IllegalStateException("§c未能生成配置文件!", e);
        }
    }

    public static void reloadConfig() {
        try {
            config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(new File(Report.getInstance().getDataFolder(), "config.yml"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

//    public static void saveConfig() {
//        try {
//            ConfigurationProvider.getProvider(YamlConfiguration.class).save(config, new File(Report.getInstance().getDataFolder(), "config.yml"));
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }

}