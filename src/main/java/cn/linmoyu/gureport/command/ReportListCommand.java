package cn.linmoyu.gureport.command;

import cn.linmoyu.gureport.Report;
import cn.linmoyu.gureport.manager.RedisManager;
import cn.linmoyu.gureport.utils.LuckPermsUtils;
import cn.linmoyu.gureport.utils.Permissions;
import cn.linmoyu.gureport.utils.RedisKeys;
import com.imaginarycode.minecraft.redisbungee.RedisBungeeAPI;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import redis.clients.jedis.Jedis;

import java.util.*;

import static cn.linmoyu.gureport.manager.ConfigManager.config;

public class ReportListCommand extends Command {
    private static final int PER_PAGE = config.getInt("reports-per-page");

    public ReportListCommand() {
        super("reports", Permissions.REPORT_STAFF_LIST_PERMISSION, "reportlist");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof ProxiedPlayer)) return;

        ProxiedPlayer player = (ProxiedPlayer) sender;

        Report.getInstance().getProxy().getScheduler().runAsync(Report.getInstance(), () -> {
            try (Jedis jedis = RedisManager.getInstance().getJedisPool().getResource()) {
                Set<String> reportIds = jedis.smembers(RedisKeys.ACTIVE_REPORTS_KEY);
                List<String> reportList = new ArrayList<>(reportIds);
                int totalPages = (int) Math.ceil((double) reportList.size() / PER_PAGE);
                if (totalPages == 0) {
                    Report.sendMessageWithPrefix(player, "§c当前暂无可处理的举报.");
                    return;
                }

                int page = 1;
                if (args.length > 0) {
                    try {
                        page = Math.max(1, Math.min(totalPages, Integer.parseInt(args[0])));
                    } catch (NumberFormatException e) {
                        Report.sendMessageWithPrefix(player, "§c无效的页码.");
                        return;
                    }
                }

                ComponentBuilder cb = new ComponentBuilder(Report.getInstance().LINE)
                        .append("§6待处理举报 §7(第" + page + "/" + totalPages + "页)\n");

                int start = (page - 1) * PER_PAGE;
                int end = Math.min(start + PER_PAGE, reportList.size());

                for (int i = start; i < end; i++) {
                    String reportId = reportList.get(i);
                    Map<String, String> report = jedis.hgetAll(RedisKeys.REPORT_KEY_PREFIX + reportId);

                    UUID reportedUUID = UUID.fromString(report.get("reported"));
                    cb.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/reportprocess " + reportId + " handle"))
                            .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§7点此查看§8§o#" + reportId).create()))
                            .append("\n§e" + (i + 1) + ". ")
                            .append(LuckPermsUtils.getPrefix(reportedUUID) + RedisBungeeAPI.getRedisBungeeApi().getNameFromUuid(reportedUUID))
                            .append(" §7- " + report.get("reason").substring(0, Math.min(15, report.get("reason").length())))
                            .append(" §7| §8§o#" + reportId);
                }
                cb.append("\n");
                if (page > 1) {
                    cb.append(" §6[上一页]")
                            .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/reports " + (page - 1)))
                            .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§7切换至上一页(第" + (page - 1) + "页)").create()));
                }
                if (page < totalPages) {
                    cb.append(" §a[下一页]")
                            .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/reports " + (page + 1)))
                            .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§7切换至下一页(第" + (page + 1) + "页)").create()));
                }

                sender.sendMessage(cb.append(Report.getInstance().LINE).create());
            } catch (Exception e) {
                Report.sendMessageWithPrefix(player, "§c获取举报列表失败.");
            }
        });
    }
}