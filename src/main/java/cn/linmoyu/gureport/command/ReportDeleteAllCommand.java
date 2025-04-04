package cn.linmoyu.gureport.command;

import cn.linmoyu.gureport.Report;
import cn.linmoyu.gureport.listener.RedisListener;
import cn.linmoyu.gureport.manager.RedisManager;
import cn.linmoyu.gureport.utils.LuckPermsUtils;
import cn.linmoyu.gureport.utils.Permissions;
import cn.linmoyu.gureport.utils.RedisKeys;
import com.imaginarycode.minecraft.redisbungee.RedisBungeeAPI;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class ReportDeleteAllCommand extends Command {

    public HashSet<ProxiedPlayer> confirmAdmin = new HashSet<>();

    public ReportDeleteAllCommand() {
        super("reportdeleteall", Permissions.REPORT_ADMIN_DELETE_ALL_PERMISSION);
    }


    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof ProxiedPlayer)) return;

        ProxiedPlayer player = (ProxiedPlayer) sender;
        // 判断有没有其他参数和在不在confirmAdmin里 要二次确认
        if (args.length != 1 && !confirmAdmin.contains(player)) {
            Report.sendMessageWithPrefix(player, "§c§l这是一个很危险的行为! 将会删除本插件存储的所有数据(举报数据、聊天日志等). 如果确定执行请加上§e\" confirm\"§c§l后重试.");
            confirmAdmin.add(player);
            Report.getInstance().getProxy().getScheduler().schedule(Report.getInstance(), () ->
                    confirmAdmin.remove(player), 15, TimeUnit.SECONDS);
            return;
        }
        String message = args[0].toLowerCase();
        // 二次确认
        if (!(message.equals("confirm") && confirmAdmin.contains(player))) return;
        Report.getInstance().getProxy().getScheduler().runAsync(Report.getInstance(), () -> {
            try (Jedis jedis = RedisManager.getInstance().getJedisPool().getResource()) {
                Set<String> reports = jedis.smembers(RedisKeys.ACTIVE_REPORTS_KEY);

                Map<String, String> reportIdToUUID = new HashMap<>();
                for (String reportId : reports) {
                    String reportedUUID = jedis.hget(RedisKeys.REPORT_KEY_PREFIX + reportId, "reported");
                    reportIdToUUID.put(reportId, reportedUUID);
                }

                try (Pipeline pipeline = jedis.pipelined()) {
                    for (String reportId : reports) {
                        String reportedUUID = reportIdToUUID.get(reportId);
                        if (reportedUUID != null) {
                            pipeline.hdel(RedisKeys.COUNTS_KEY, reportedUUID);
                        }
                        pipeline.del(RedisKeys.REPORT_KEY_PREFIX + reportId);
                        pipeline.srem(RedisKeys.ACTIVE_REPORTS_KEY, reportId);
                    }
                    pipeline.sync();
                }

                String pattern = RedisKeys.CHAT_RECORD_PREFIX + "*";
                ScanParams scanParams = new ScanParams().match(pattern).count(100);
                String cursor = "0";
                do {
                    ScanResult<String> scanResult = jedis.scan(cursor, scanParams);
                    List<String> keys = scanResult.getResult();
                    if (!keys.isEmpty()) {
                        jedis.del(keys.toArray(new String[0]));
                    }
                    cursor = scanResult.getCursor();
                } while (!"0".equals(cursor));

                Report.sendMessageWithPrefix(player, "§a成功删除插件所有数据.");
                RedisBungeeAPI.getRedisBungeeApi().sendChannelMessage(
                        Report.getInstance().BUNGEE_CHANNEL_NAME,
                        RedisListener.SEND_MESSAGE_PREFIX + LuckPermsUtils.getPrefix(player.getUniqueId()) + player.getDisplayName() + " §f移除了所有的举报记录数据."
                );

            } catch (Exception e) {
                Report.sendMessageWithPrefix(player, "§c操作失败. " + e.getMessage());
            }
            confirmAdmin.remove(player);
        });
    }
}
