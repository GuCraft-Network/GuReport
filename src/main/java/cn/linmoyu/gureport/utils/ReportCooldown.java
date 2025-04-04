package cn.linmoyu.gureport.utils;

import cn.linmoyu.gureport.Report;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static cn.linmoyu.gureport.manager.ConfigManager.config;

public class ReportCooldown {
    private static final ConcurrentHashMap<UUID, Long> cooldownMap = new ConcurrentHashMap<>();
    private static final int COOLDOWN_SECONDS = config.getInt("report-cooldown");

    // true为不需要冷却, false则为正在冷却/需要冷却
    public static boolean checkCooldown(ProxiedPlayer reporter) {
        UUID playerId = reporter.getUniqueId();
        long currentTime = System.currentTimeMillis();

        if (reporter.hasPermission(Permissions.REPORT_BYPASS_COOLDOWN_PERMISSION)) return true;

        long lastReportTime = cooldownMap.compute(playerId, (key, value) -> {
            if (value == null || (currentTime - value) > COOLDOWN_SECONDS * 1000L) {
                return currentTime;
            }
            return value;
        });

        if (lastReportTime != currentTime) {
            long remaining = COOLDOWN_SECONDS * 1000L - (currentTime - lastReportTime);
            Report.sendMessageWithPrefix(reporter, "§c请等待在" + formatCooldownTime(remaining) + "后再尝试提交举报.");
            return false;
        }

        Report.getInstance().getProxy().getScheduler().schedule(Report.getInstance(), () -> {
            if ((System.currentTimeMillis() - cooldownMap.getOrDefault(playerId, 0L)) > COOLDOWN_SECONDS * 1000L) {
                cooldownMap.remove(playerId);
            }
        }, COOLDOWN_SECONDS + 10, TimeUnit.SECONDS);

        return true;
    }

    private static String formatCooldownTime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;

        if (minutes > 0) {
            return String.format("%d分%02d秒", minutes, seconds);
        }
        return String.format("%d秒", seconds);
    }

}
