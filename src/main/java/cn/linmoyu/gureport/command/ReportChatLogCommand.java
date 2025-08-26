package cn.linmoyu.gureport.command;

import cn.linmoyu.gureport.Report;
import cn.linmoyu.gureport.manager.RedisManager;
import cn.linmoyu.gureport.utils.LuckPermsUtils;
import cn.linmoyu.gureport.utils.Permissions;
import cn.linmoyu.gureport.utils.RedisKeys;
import com.imaginarycode.minecraft.redisbungee.RedisBungeeAPI;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;
import org.json.JSONException;
import org.json.JSONObject;
import redis.clients.jedis.Jedis;

import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static cn.linmoyu.gureport.manager.ConfigManager.config;

public class ReportChatLogCommand extends Command implements TabExecutor {
    private static final int PER_PAGE = config.getInt("chatlog.command-in-page");

    public ReportChatLogCommand() {
        super("reportchatlog", Permissions.REPORT_STAFF_CHATLOG_PERMISSION, "举报聊天日志");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof ProxiedPlayer)) return;

        ProxiedPlayer staff = (ProxiedPlayer) sender;
        if (args.length < 1) {
            Report.sendMessageWithPrefix(staff, "§c用法: /reportchatlog <玩家> [页码]");
            return;
        }

        String targetName = args[0];
        int page = args.length > 1 ? parseInt(args[1]) : 1;

        Report.getInstance().getProxy().getScheduler().runAsync(Report.getInstance(), () -> processReportChatlogRequest(staff, targetName, page));
    }

    private void processReportChatlogRequest(ProxiedPlayer staff, String targetName, int page) {
        try (Jedis jedis = RedisManager.getInstance().getJedisPool().getResource()) {
            UUID targetUUID = RedisBungeeAPI.getRedisBungeeApi().getUuidFromName(targetName, true);

            if (targetUUID == null) {
                Report.sendMessageWithPrefix(staff, "§c尚未存储该玩家的聊天日志.\n" + Report.getInstance().PREFIX + "§c服务器可能尚未缓存该昵称数据, 请注意核对该玩家昵称大小写.");
                return;
            }

            List<String> chatLogs = jedis.lrange(RedisKeys.CHAT_RECORD_PREFIX + targetUUID, 0, -1);
            sendReportChatlogPage(staff, targetName, targetUUID, chatLogs, page);

        } catch (Exception e) {
            Report.sendMessageWithPrefix(staff, "§c查询聊天日志失败. " + e.getMessage());
        }
    }

    private void sendReportChatlogPage(ProxiedPlayer staff, String targetName, UUID targetUUID,
                                       List<String> chatLogs, int page) {
        if (chatLogs.isEmpty()) {
            Report.sendMessageWithPrefix(staff, "§c尚未存储该玩家的聊天日志.");
            return;
        }
        int total = chatLogs.size();
        int totalPages = (int) Math.ceil((double) total / PER_PAGE);
        page = Math.max(1, Math.min(page, totalPages));
        int start = (page - 1) * PER_PAGE;
        int end = Math.min(start + PER_PAGE, total);
        ComponentBuilder cb = new ComponentBuilder(Report.getInstance().LINE)
                .append(LuckPermsUtils.getPrefix(targetUUID) + targetName + "§f的聊天日志 §7(第" + page + "页/第" + totalPages + "页)\n\n");

        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm");

        for (int i = start; i < end; i++) {
            String logEntry = chatLogs.get(i);
            JSONObject log = parseLogEntry(logEntry); // 解析JSON

            cb.append("§6" + sdf.format(log.getLong("time")))
                    .append(" §7| §e")
                    .append(log.getString("server"))
                    .append(" §7| ")
                    .append(log.getString("chat"))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, buildHoverDetail(log)))
                    .append("\n");
        }

        if (page > 1) {
            cb.append(" §6[上一页]")
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/reportchatlog " + targetName + " " + (page - 1)))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§7切换至上一页(第" + (page - 1) + "页)").create()));
        }
        if (page < totalPages) {
            cb.append(" §a[下一页]")
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/reportchatlog " + targetName + " " + (page + 1)))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§7切换至下一页(第" + (page + 1) + "页)").create()));
        }

        staff.sendMessage(cb.append(Report.getInstance().LINE).create());
    }

    private JSONObject parseLogEntry(String logEntry) {
        try {
            return new JSONObject(logEntry);
        } catch (JSONException e) {
            return new JSONObject()
                    .put("time", System.currentTimeMillis())
                    .put("server", "unknown")
                    .put("chat", "§c数据格式错误");
        }
    }

    private BaseComponent[] buildHoverDetail(JSONObject log) {
        SimpleDateFormat fullSdf = new SimpleDateFormat("yy/MM/dd HH:mm:ss");
        return new ComponentBuilder()
                .append("§f时间: §6" + fullSdf.format(log.getLong("time")) + "\n")
                .append("§f子服: §e" + log.getString("server") + "\n")
                .append("§f聊天:\n§f" + log.getString("chat"))
                .create();
    }

    private int parseInt(String input) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            return 1;
        }
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