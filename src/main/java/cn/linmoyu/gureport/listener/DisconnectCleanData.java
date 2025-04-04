package cn.linmoyu.gureport.listener;

import cn.linmoyu.gureport.Report;
import cn.linmoyu.gureport.manager.RedisManager;
import cn.linmoyu.gureport.utils.RedisKeys;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;
import redis.clients.jedis.resps.ScanResult;

import java.util.*;

import static cn.linmoyu.gureport.manager.ConfigManager.config;

public class DisconnectCleanData implements Listener {

    @EventHandler
    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();
        String playerUUIDStr = playerUUID.toString();
        Report.getInstance().getProxy().getScheduler().runAsync(Report.getInstance(), () -> {
            try (Jedis jedis = RedisManager.getInstance().getJedisPool().getResource()) {
                Set<String> activeReports = new HashSet<>();
                String cursor = "0";
                do {
                    ScanResult<String> scanResult = jedis.sscan(RedisKeys.ACTIVE_REPORTS_KEY, cursor);
                    activeReports.addAll(scanResult.getResult());
                    cursor = scanResult.getCursor();
                } while (!"0".equals(cursor));

                List<String> reportIds = new ArrayList<>(activeReports);
                Pipeline pipeline = jedis.pipelined();
                List<Response<String>> reportedResponses = new ArrayList<>(reportIds.size());

                for (String reportId : reportIds) {
                    reportedResponses.add(pipeline.hget(RedisKeys.REPORT_KEY_PREFIX + reportId, "reported"));
                }
                pipeline.sync();

                List<String> toRemove = new ArrayList<>();
                for (int i = 0; i < reportIds.size(); i++) {
                    String reportedUUID = reportedResponses.get(i).get();
                    if (playerUUIDStr.equals(reportedUUID)) {
                        toRemove.add(reportIds.get(i));
                    }
                }

                if (!toRemove.isEmpty()) {
                    Pipeline deletePipeline = jedis.pipelined();
                    for (String reportId : toRemove) {
                        deletePipeline.del(RedisKeys.REPORT_KEY_PREFIX + reportId);
                        deletePipeline.srem(RedisKeys.ACTIVE_REPORTS_KEY, reportId);
                    }
                    deletePipeline.sync();

                    jedis.hdel(RedisKeys.COUNTS_KEY, playerUUIDStr);
                }

                if (config.getBoolean("after-disconnect-cleanlog")) {
                    jedis.del(RedisKeys.CHAT_RECORD_PREFIX + playerUUID);
                }
            }
        });
    }
}
