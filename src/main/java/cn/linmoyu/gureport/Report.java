package cn.linmoyu.gureport;

import cn.linmoyu.gureport.command.*;
import cn.linmoyu.gureport.listener.BlockMessagePlayerListener;
import cn.linmoyu.gureport.listener.DisconnectCleanData;
import cn.linmoyu.gureport.listener.RecordPlayerChat;
import cn.linmoyu.gureport.listener.RedisListener;
import cn.linmoyu.gureport.manager.ConfigManager;
import cn.linmoyu.gureport.manager.RedisManager;
import lombok.Getter;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;

import java.util.HashSet;
import java.util.logging.Level;

import static cn.linmoyu.gureport.manager.ConfigManager.config;

@Getter
public class Report extends Plugin implements Listener {

    @Getter
    private static Report instance;

    public HashSet<ProxiedPlayer> blockReportStaffs = new HashSet<>();
    public String PREFIX = "§b§l举报系统 §7» ";
    public String LINE = "\n§6-§6-§6-§6-§6-§6-§6-§6-§6-§6-§6-§6-§6-§6-§6-§6-§6-§6-§6-§6-§6-§6-§6-§6-§6-§6-§6-§6-§6-§6-§6-§6-§6-§6-§6-§6-§6-§6-§6-§6-§6-§6-\n";
    public String BUNGEE_CHANNEL_NAME = "GUSTAFF-REPORT";

    public static void sendMessageWithPrefix(ProxiedPlayer player, String message) {
        player.sendMessage(TextComponent.fromLegacy(Report.getInstance().PREFIX + message));
    }

    @Override
    public void onEnable() {
        // 这是一坨史山插件, 谢谢你, 没学过Java, 不建议以此为参考.
        // 所有数据部分操作全部来自DeepSeek.
        instance = this;
        ConfigManager.saveDefaultConfig();
        ConfigManager.reloadConfig();

        // Redis连接
        try {
            RedisManager.initialize(config.getSection("redis"));
        } catch (RedisManager.RedisInitException e) {
            getLogger().log(Level.SEVERE, e.getMessage());
            return;
        }

        getProxy().getPluginManager().registerListener(this, new BlockMessagePlayerListener());
        getProxy().getPluginManager().registerListener(this, new DisconnectCleanData());
        getProxy().getPluginManager().registerListener(this, new RecordPlayerChat());
        getProxy().getPluginManager().registerListener(this, new RedisListener());

        getProxy().getPluginManager().registerCommand(this, new ReportBlockCommand());
        getProxy().getPluginManager().registerCommand(this, new ReportChatLogCommand());
        getProxy().getPluginManager().registerCommand(this, new ReportCommand());
        getProxy().getPluginManager().registerCommand(this, new ReportDeleteAllCommand());
        getProxy().getPluginManager().registerCommand(this, new ReportListCommand());
        getProxy().getPluginManager().registerCommand(this, new ReportProcessCommand());

    }

    @Override
    public void onDisable() {
        if (RedisManager.getInstance() != null) {
            RedisManager.getInstance().closePool();
        }
    }

}