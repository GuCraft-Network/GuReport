package cn.linmoyu.gureport.command;

import cn.linmoyu.gureport.Report;
import cn.linmoyu.gureport.listener.RedisListener;
import cn.linmoyu.gureport.manager.ConfigManager;
import cn.linmoyu.gureport.manager.RedisManager;
import cn.linmoyu.gureport.utils.Permissions;
import cn.linmoyu.gureport.utils.RedisKeys;
import cn.linmoyu.gureport.utils.ReportCooldown;
import com.imaginarycode.minecraft.redisbungee.RedisBungeeAPI;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;
import org.json.JSONArray;
import redis.clients.jedis.Jedis;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static cn.linmoyu.gureport.manager.ConfigManager.config;

public class ReportCommand extends Command implements TabExecutor {
    // 定义举报ID可用的字符和上限生成次数
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int MAX_ID_GENERATION_ATTEMPTS = 10;

    public ReportCommand() {
        super("report", null, "举报", "wdr", "jb", "jubao", "watchdogreport");
    }

    // 抽选、生成随机举报ID
    private static String generateRandomString() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder(6); // 增加长度到6位
        for (int i = 0; i < 6; i++) {
            sb.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof ProxiedPlayer)) return;

        ProxiedPlayer reporter = (ProxiedPlayer) sender;

        if (args.length == 1 && args[0].equalsIgnoreCase("reload") && reporter.hasPermission(Permissions.REPORT_ADMIN_RELOAD_PERMISSION)) {
            ConfigManager.reloadConfig();
            Report.sendMessageWithPrefix(reporter, "§c使用重载命令并不会重新加载Redis及其相关配置, 如有此需要请重启反代.");
            Report.sendMessageWithPrefix(reporter, "§c建议重启反代以应用配置, 重载命令不一定管用.");
            return;
        }
        if (args.length < 1) {
            Report.sendMessageWithPrefix(reporter, "§c用法: /report <玩家> [原因]");
            return;
        }

        String targetName = args[0];

        RedisBungeeAPI redisBungee = RedisBungeeAPI.getRedisBungeeApi();
        UUID targetUUID = redisBungee.getUuidFromName(targetName, true);
        if (!redisBungee.isPlayerOnline(targetUUID)) {
            Report.sendMessageWithPrefix(reporter, "§c错误: 玩家不在线.");
            return;
        }
        if (reporter.getUniqueId() == targetUUID) {
            Report.sendMessageWithPrefix(reporter, "§c你不能举报你自己!");
            return;
        }

        if (!ReportCooldown.checkCooldown(reporter)) return;

        // 获取举报原因, 如果没有arg[1]就取config.yml里的默认原因
        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        if (args.length < 2) {
            reason = config.getString("default-report-reason");
        }
        String finalReason = reason; // lambda 表达式中使用的变量应为 final 或有效 final

        Report.getInstance().getProxy().getScheduler().runAsync(Report.getInstance(), () -> {
            try {
                String reportId = generateUniqueReportId();
                if (reportId == null) {
                    Report.sendMessageWithPrefix(reporter, "§c错误: 在生成举报ID时出现问题, 请重试.");
                    return;
                }

                // 保存数据
                try (Jedis jedis = RedisManager.getInstance().getJedisPool().getResource()) {
                    Map<String, String> reportData = new HashMap<>();
                    reportData.put("reporter", reporter.getUniqueId().toString());
                    reportData.put("reported", targetUUID.toString());
                    reportData.put("reason", finalReason);
                    reportData.put("timestamp", String.valueOf(System.currentTimeMillis()));

                    // 提交被举报者当前聊天日志
                    List<String> chatHistory = jedis.lrange(RedisKeys.CHAT_RECORD_PREFIX + targetUUID, 0, config.getInt("chatlog.hover-in-page") - 1);  // stop +1 = 存储?条历史记录
                    reportData.put("chat_history", new JSONArray(chatHistory).toString());

                    jedis.hset(RedisKeys.REPORT_KEY_PREFIX + reportId, reportData);
                    jedis.sadd(RedisKeys.ACTIVE_REPORTS_KEY, reportId);
                    jedis.hincrBy(RedisKeys.COUNTS_KEY, targetUUID.toString(), 1);

                    jedis.sadd(RedisKeys.REPORT_KEY_PREFIX + targetUUID, reportId);

                    jedis.expire(RedisKeys.REPORT_KEY_PREFIX + targetUUID, 86400);
                }

                Report.sendMessageWithPrefix(reporter, "§f举报已提交! 原因: " + finalReason + ", 举报ID: §8§o#" + reportId + "§f.\n" +
                        Report.getInstance().PREFIX + "§f我们非常理解您的心情, 请耐心等待在线工作人员处理. ");
                redisBungee.sendChannelMessage(Report.getInstance().BUNGEE_CHANNEL_NAME, RedisListener.SEND_REPORT_PREFIX + reportId);
            } catch (Exception e) {
                Report.sendMessageWithPrefix(reporter, "§c在举报提交时发生错误, 请联系管理员. " + e.getMessage());
            }

        });
    }

    // 生成特定举报ID, 并检查数据里有没有对应ID, 如果有就重试.
    private String generateUniqueReportId() {
        try (Jedis jedis = RedisManager.getInstance().getJedisPool().getResource()) {
            for (int i = 0; i < MAX_ID_GENERATION_ATTEMPTS; i++) {
                String candidate = generateRandomString();
                if (!jedis.exists(RedisKeys.REPORT_KEY_PREFIX + candidate)) {
                    return candidate.toUpperCase(Locale.ROOT);
                }
            }
        }
        return null;
    }

    @Override
    public HashSet<String> onTabComplete(CommandSender sender, String[] args) {
        final HashSet<String> matches = new HashSet<>();
        if (args.length == 0) {
            for (ProxiedPlayer p : ProxyServer.getInstance().getPlayers()) {
                matches.add(p.getName());
            }
        } else {
            for (ProxiedPlayer p : ProxyServer.getInstance().getPlayers()) {
                if (p.getName().startsWith(args[0])) {
                    matches.add(p.getName());
                }
            }
        }

        return matches;
    }

}