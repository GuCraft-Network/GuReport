package cn.linmoyu.gureport.utils;

import cn.linmoyu.gureport.Report;
import cn.linmoyu.gureport.manager.RedisManager;
import com.imaginarycode.minecraft.redisbungee.RedisBungeeAPI;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.*;
import org.json.JSONArray;
import org.json.JSONObject;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisException;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

import static cn.linmoyu.gureport.manager.ConfigManager.config;

public class BuildReportMessage {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MM/dd HH:mm")
            .withZone(ZoneId.of("Asia/Shanghai"));


    // 我知道的 数据查询不应该扔在这 但是我实在是懒得改了
    public static void buildReportToProxyStaff(String reportId) {
        Report plugin = Report.getInstance();
        plugin.getProxy().getScheduler().runAsync(plugin, () -> {
            JedisPool jedisPool = RedisManager.getInstance().getJedisPool();
            if (jedisPool == null) {
                plugin.getLogger().log(Level.SEVERE, "§cRedis连接池未初始化.");
                return;
            }

            try (Jedis jedis = jedisPool.getResource()) {
                Map<String, String> report = jedis.hgetAll("report:" + reportId);
                RedisBungeeAPI redisBungeeAPI = RedisBungeeAPI.getRedisBungeeApi();

                //统计被举报次数 不要问我为什么写在这, 因为我太懒了==
                String count = jedis.hget(RedisKeys.COUNTS_KEY, report.get("reported"));
                String displayCount = (count == null) ? "0" : count;

                // 构建消息
                TextComponent textComponent = buildReportComponent(report, reportId, displayCount, redisBungeeAPI);

                // 发送消息
                ProxyServer.getInstance().getPlayers().stream()
                        .filter(Permissions::canReceiveReport)
                        .forEach(player -> player.sendMessage(textComponent));

            } catch (JedisException e) {
                plugin.getLogger().log(Level.SEVERE, "Redis查询异常: ", e);
            }
        });
    }

    public static TextComponent buildReportComponent(Map<String, String> report, String reportId, String displayCount, RedisBungeeAPI redisBungeeAPI) {
        // 举报者UUID
        String reporterUUID = report.get("reporter");
        // 被举报者UUID
        String reportedUUID = report.get("reported");

        TextComponent tc = new TextComponent(Report.getInstance().LINE);
        // 玩家
        TextComponent reportedTC = new TextComponent("\n 玩家: "
                + "§c" + LuckPermsUtils.getPrefix(UUID.fromString(reportedUUID)) + getPlayerName(reportedUUID, redisBungeeAPI));
        reportedTC.setColor(ChatColor.WHITE);
        tc.addExtra(reportedTC);

        // 举报者
        TextComponent reporterTC = new TextComponent(" 举报者: "
                + "§e" + LuckPermsUtils.getPrefix(UUID.fromString(reporterUUID)) + getPlayerName(reporterUUID, redisBungeeAPI));
        reporterTC.setColor(ChatColor.WHITE);
        tc.addExtra(reporterTC);

        // 快捷查看-聊天日志
        TextComponent chatlogTC = new TextComponent(" [R]");
        chatlogTC.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/reportchatlog " + getPlayerName(reportedUUID, redisBungeeAPI)));
        chatlogTC.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, buildChatPreview(report.get("chat_history"))));
        chatlogTC.setColor(ChatColor.GOLD);
        tc.addExtra(chatlogTC);

        // 举报ID
        TextComponent reportIdTC = new TextComponent(" #" + reportId);
        reportIdTC.setColor(ChatColor.DARK_GRAY);
        reportIdTC.setItalic(true);
        tc.addExtra(reportIdTC);

        // 原因
        TextComponent reasonTextTC = new TextComponent("\n 原因: ");
        reasonTextTC.setColor(ChatColor.WHITE);
        TextComponent reasonTC = new TextComponent(report.get("reason"));
        reasonTC.setColor(ChatColor.GRAY);
        reasonTextTC.addExtra(reasonTC);
        tc.addExtra(reasonTextTC);

        // 时间
        TextComponent timeTextTC = new TextComponent(" 时间: ");
        timeTextTC.setColor(ChatColor.WHITE);
        TextComponent timeStamp = new TextComponent(formatTimestamp(report.get("timestamp")));
        timeStamp.setColor(ChatColor.GOLD);
        timeTextTC.addExtra(timeStamp);
        tc.addExtra(timeTextTC);

        // 举报次数, 如果使用方法时没有传递举报次数就不构建
        TextComponent displayCountTextTC = new TextComponent("");
        if (displayCount != null) {
            displayCountTextTC = new TextComponent(" 次数: ");
            displayCountTextTC.setColor(ChatColor.WHITE);
            TextComponent displayCountTC = new TextComponent(displayCount);
            displayCountTC.setColor(ChatColor.YELLOW);
            displayCountTextTC.addExtra(displayCountTC);
        }
        tc.addExtra(displayCountTextTC);

        // 受理/传送
        TextComponent acceptTextTC = new TextComponent("\n \n [受理] ");
        acceptTextTC.setColor(ChatColor.GOLD);
        acceptTextTC.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/reportprocess " + reportId + " accept"));
        acceptTextTC.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§6传送至被举报玩家").create()));
        tc.addExtra(acceptTextTC);

        // 无效/移除
        TextComponent invaildTC = new TextComponent("[无效] ");
        invaildTC.setColor(ChatColor.GRAY);
        invaildTC.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/reportprocess " + reportId + " invalid"));
        invaildTC.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§7使这个举报无效/移除").create()));
        tc.addExtra(invaildTC);

        // 封禁 隔断消息
        TextComponent banTextTC = new TextComponent("§f封禁: ");
        banTextTC.setColor(ChatColor.WHITE);
        tc.addExtra(banTextTC);

        // 封禁 7天
        TextComponent banTempTimeTC = new TextComponent("[7天] ");
        banTempTimeTC.setColor(ChatColor.YELLOW);
        banTempTimeTC.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/reportprocess " + reportId + " truecmd banip " + reportedUUID + " 7d " + config.getString("ban-reason")));
        banTempTimeTC.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§e封禁该玩家7天").create()));
        tc.addExtra(banTempTimeTC);

        // 封禁 永久
        TextComponent banLifeTimeTC = new TextComponent("[永久] ");
        banLifeTimeTC.setColor(ChatColor.RED);
        banLifeTimeTC.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/reportprocess " + reportId + " truecmd banip " + reportedUUID + " " + config.getString("ban-reason")));
        banLifeTimeTC.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§c封禁该玩家永久").create()));
        tc.addExtra(banLifeTimeTC);

        // 禁言 隔断消息
        TextComponent muteTextTC = new TextComponent("§f禁言: ");
        muteTextTC.setColor(ChatColor.WHITE);
        tc.addExtra(muteTextTC);

        // 禁言 7天
        TextComponent muteTempTimeTC = new TextComponent("[7天] ");
        muteTempTimeTC.setColor(ChatColor.YELLOW);
        muteTempTimeTC.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/reportprocess " + reportId + " truecmd mute " + reportedUUID + " 7d " + config.getString("mute-reason")));
        muteTempTimeTC.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§e禁言该玩家7天").create()));
        tc.addExtra(muteTempTimeTC);

        // 禁言 永久
        TextComponent muteLifeTimeTC = new TextComponent("[永久] ");
        muteLifeTimeTC.setColor(ChatColor.RED);
        muteLifeTimeTC.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/reportprocess " + reportId + " truecmd mute " + reportedUUID + " " + config.getString("mute-reason")));
        muteLifeTimeTC.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§c禁言该玩家永久").create()));
        tc.addExtra(muteLifeTimeTC);

        // 移出
        TextComponent kickTC = new TextComponent("[移出] ");
        kickTC.setColor(ChatColor.AQUA);
        kickTC.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/reportprocess " + reportId + " truecmd kick " + reportedUUID + " " + config.getString("kick-reason")));
        kickTC.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§b移出该玩家").create()));
        tc.addExtra(kickTC);

        TextComponent endLF = new TextComponent("\n"); // 不知道为什么设置的LINE没有按照预期换行.
        tc.addExtra(Report.getInstance().LINE);
        return tc;
    }

    // 获取玩家名字
    private static String getPlayerName(String uuidString, RedisBungeeAPI redisBungeeAPI) {
        try {
            UUID uuid = UUID.fromString(uuidString);
            String name = redisBungeeAPI.getNameFromUuid(uuid);
            return name != null ? name : "未知玩家";
        } catch (IllegalArgumentException e) {
            return uuidString;
        }
    }

//    // 判断是否可以删除举报
//    // 别问 问就是史山代码没精力动
//    private static boolean canDeleteReport(ProxiedPlayer player) {
//        return player.hasPermission(Permissions.REPORT_ADMIN_DELETE_PERMISSION);
//    }

    // 格式化时间戳
    private static String formatTimestamp(String timestampStr) {
        try {
            Instant instant = Instant.ofEpochMilli(Long.parseLong(timestampStr));
            return DATE_FORMATTER.format(instant);
        } catch (NumberFormatException e) {
            return timestampStr;
        }
    }

    private static BaseComponent[] buildChatPreview(String chatHistoryJson) {
        ComponentBuilder hoverBuilder = new ComponentBuilder("\n");

        try {
            JSONArray chatLogs = new JSONArray(chatHistoryJson);

            int maxShow = Math.min(config.getInt("chatlog.hover-in-page"), chatLogs.length());
            SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm");
            for (int i = 0; i < maxShow; i++) {
                String logStr = chatLogs.getString(i);
                JSONObject log = new JSONObject(logStr);

                // 时间 | 服务器 | 内容
                hoverBuilder.append("§f" + sdf.format(log.getLong("time")))
                        .append(" §7| ")
                        .append(log.getString("server"))
                        .append(" §7| ")
                        .append(log.getString("chat"))
                        .append("\n");
            }

            if (chatLogs.isEmpty()) {
                hoverBuilder.append("§c暂无其聊天日志.\n");
            } else if (chatLogs.length() > maxShow) {
                hoverBuilder.append("§7.........\n");
            }

        } catch (Exception e) {
            hoverBuilder.append("§c记录加载失败.\n");
        }

        return hoverBuilder.create();
    }

}