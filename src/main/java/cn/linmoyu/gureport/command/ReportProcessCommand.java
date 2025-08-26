package cn.linmoyu.gureport.command;

import cn.linmoyu.gureport.Report;
import cn.linmoyu.gureport.listener.RedisListener;
import cn.linmoyu.gureport.manager.RedisManager;
import cn.linmoyu.gureport.utils.BuildReportMessage;
import cn.linmoyu.gureport.utils.LuckPermsUtils;
import cn.linmoyu.gureport.utils.Permissions;
import cn.linmoyu.gureport.utils.RedisKeys;
import com.imaginarycode.minecraft.redisbungee.RedisBungeeAPI;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;
import redis.clients.jedis.resps.ScanResult;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static cn.linmoyu.gureport.manager.ConfigManager.config;

public class ReportProcessCommand extends Command {

    private static final Map<String, ReportAction> ACTION_MAP = new ConcurrentHashMap<>();

    public ReportProcessCommand() {
        super("reportprocess", Permissions.REPORT_STAFF_PROCESS_PERMISSION, "处理举报");
        initializeActions();
    }

    private void initializeActions() {
        ACTION_MAP.put("handle", this::handle);
        ACTION_MAP.put("accept", this::accept);
        ACTION_MAP.put("invalid", (p, id, args) -> remove(p, id, false));
        ACTION_MAP.put("truecmd", this::executeTrueCommand);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof ProxiedPlayer)) return;

        ProxiedPlayer staff = (ProxiedPlayer) sender;
        if (args.length < 2) {
            Report.sendMessageWithPrefix(staff, "§c用法: /reportprocess <举报ID> <处理方式>");
            return;
        }

        String reportId = args[0].toUpperCase();
        String action = args[1].toLowerCase();

        withRedis(jedis -> {
            Map<String, String> report = getReportData(jedis, reportId);
            if (!isReportExists(report, staff)) return;
            if (ACTION_MAP.containsKey(action)) {
                String[] actionArgs = Arrays.copyOfRange(args, 2, args.length);
                ACTION_MAP.get(action).execute(staff, reportId, actionArgs);
            }
        }, staff);
    }

    private void handle(ProxiedPlayer staff, String reportId, String... args) {
        withRedis(jedis -> {
            Map<String, String> report = getReportData(jedis, reportId);

            RedisBungeeAPI redisBungeeAPI = RedisBungeeAPI.getRedisBungeeApi();
            String reportedCount = jedis.hget(RedisKeys.COUNTS_KEY, report.get("reported"));

            TextComponent textComponent = BuildReportMessage.buildReportComponent(report, reportId, reportedCount, redisBungeeAPI);
            staff.sendMessage(textComponent);
        }, staff);
    }

    private void accept(ProxiedPlayer staff, String reportId, String... args) {
        withRedis(jedis -> {
            Map<String, String> report = getReportData(jedis, reportId);

            ServerInfo server = RedisBungeeAPI.getRedisBungeeApi()
                    .getServerFor(UUID.fromString(report.get("reported")));
            staff.connect(server);

            Report.getInstance().getProxy().getScheduler().schedule(Report.getInstance(), () ->
                    staff.chat(config.getString("staff-teleport-command")
                            .replace("%reporterUUID%", report.get("reported"))
                            .replace("%staffUUID%", staff.getUniqueId().toString())
                    ), 1, TimeUnit.SECONDS);
        }, staff);
    }

    private void remove(ProxiedPlayer staff, String reportId, boolean isDone, String... args) {
        withRedis(jedis -> {
            // 1. 分页获取活跃举报ID（避免大集合内存问题）
            Set<String> activeReports = new HashSet<>();
            String cursor = "0";
            do {
                ScanResult<String> scanResult = jedis.sscan(RedisKeys.ACTIVE_REPORTS_KEY, cursor);
                activeReports.addAll(scanResult.getResult());
                cursor = scanResult.getCursor();
            } while (!"0".equals(cursor));

            // 2. 获取目标被举报者UUID
            String reportedIdPlayer = jedis.hget(RedisKeys.REPORT_KEY_PREFIX + reportId, "reported");
            if (reportedIdPlayer == null) return;

            // 3. 批量获取所有举报的被举报者UUID
            Pipeline pipeline = jedis.pipelined();
            List<Response<String>> reportedResponses = new ArrayList<>(activeReports.size());

            for (String reportIdAll : activeReports) {
                reportedResponses.add(pipeline.hget(RedisKeys.REPORT_KEY_PREFIX + reportIdAll, "reported"));
            }
            pipeline.sync();

            // 4. 收集需要处理的举报ID
            List<String> targetReports = new ArrayList<>();
            int index = 0;
            for (String reportIdAll : activeReports) {
                String uuid = reportedResponses.get(index++).get();
                if (reportedIdPlayer.equals(uuid)) {
                    targetReports.add(reportIdAll);
                }
            }

            // 5. 批量删除操作
            if (!targetReports.isEmpty()) {
                Pipeline deletePipeline = jedis.pipelined();
                for (String id : targetReports) {
                    deletePipeline.del(RedisKeys.REPORT_KEY_PREFIX + id);
                    deletePipeline.srem(RedisKeys.ACTIVE_REPORTS_KEY, id);
                }
                deletePipeline.sync();

                // 统一清除计数（避免循环内多次操作）
                jedis.hdel(RedisKeys.COUNTS_KEY, reportedIdPlayer);
            }

            // 6. 批量广播处理结果
            targetReports.forEach(id ->
                    broadcastProcessingResult(staff, id, isDone)
            );
        }, staff);
//        withRedis(jedis -> {
//            Set<String> reports = jedis.smembers(RedisKeys.ACTIVE_REPORTS_KEY);
//            String reportedIdPlayer = jedis.hget(RedisKeys.REPORT_KEY_PREFIX + reportId, "reported");
//
//            for (String reportIdAll : reports) {
//                String reportedUUID = jedis.hget(RedisKeys.REPORT_KEY_PREFIX + reportIdAll, "reported");
//                if (reportedUUID != null && reportedUUID.equals(reportedIdPlayer)) {
//                    jedis.hdel(RedisKeys.COUNTS_KEY, reportedUUID);
//                    jedis.del(RedisKeys.REPORT_KEY_PREFIX + reportIdAll);
//                    jedis.srem(RedisKeys.ACTIVE_REPORTS_KEY, reportIdAll);
//                }
//                broadcastProcessingResult(staff, reportIdAll, isDone);
//            }
//        }, staff);
//        withRedis(jedis -> {
//            Map<String, String> report = getReportData(jedis, reportId);
//
//            String reportedUUID = report.get("reported");
//            Transaction tx = jedis.multi();
//            tx.del(RedisKeys.REPORT_KEY_PREFIX + reportId);
//            tx.srem(RedisKeys.ACTIVE_REPORTS_KEY, reportId);
//            tx.hincrBy(RedisKeys.COUNTS_KEY, reportedUUID, -1);
//            tx.exec();
//            long count = Long.parseLong(jedis.hget(RedisKeys.COUNTS_KEY, reportedUUID));
//            if (count <= 0) {
//                jedis.hdel(RedisKeys.COUNTS_KEY, reportedUUID);
//            }
//
//            broadcastProcessingResult(staff, reportId, isDone);
//        }, staff);
    }

    private void executeTrueCommand(ProxiedPlayer staff, String reportId, String... args) {
        String command = String.join(" ", args);
        ProxyServer.getInstance().getPluginManager().dispatchCommand(staff, command);
        remove(staff, reportId, true);
    }

    private void withRedis(Consumer<Jedis> action, ProxiedPlayer staff) {
        Report.getInstance().getProxy().getScheduler().runAsync(Report.getInstance(), () -> {
            JedisPool pool = RedisManager.getInstance().getJedisPool();
            if (pool == null) {
                Report.sendMessageWithPrefix(staff, "§cRedis连接池未初始化.");
                return;
            }

            try (Jedis jedis = pool.getResource()) {
                action.accept(jedis);
            } catch (Exception e) {
                Report.sendMessageWithPrefix(staff, "§cRedis查询异常: " + e.getMessage());
            }
        });
    }

    private Map<String, String> getReportData(Jedis jedis, String reportId) {
        return jedis.hgetAll(RedisKeys.REPORT_KEY_PREFIX + reportId);
    }

    private boolean isReportExists(Map<String, String> report, ProxiedPlayer staff) {
        if (report == null || report.isEmpty()) {
            Report.sendMessageWithPrefix(staff, "§c该举报不存在或已过期.");
            return false;
        }
        return true;
    }

    private void broadcastProcessingResult(ProxiedPlayer staff, String reportId, boolean isDone) {
        String message = String.format("%s%s §f%s了举报 §8§o#%s§f.",
                LuckPermsUtils.getPrefix(staff.getUniqueId()),
                staff.getDisplayName(),
                isDone ? "处理" : "移除",
                reportId);

        RedisBungeeAPI.getRedisBungeeApi().sendChannelMessage(
                Report.getInstance().BUNGEE_CHANNEL_NAME,
                RedisListener.SEND_MESSAGE_PREFIX + message
        );


    }

    @FunctionalInterface
    private interface ReportAction {
        void execute(ProxiedPlayer staff, String reportId, String... args);
    }
}