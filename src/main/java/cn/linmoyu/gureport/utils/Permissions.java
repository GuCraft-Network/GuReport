package cn.linmoyu.gureport.utils;

import cn.linmoyu.gureport.Report;
import net.md_5.bungee.api.connection.ProxiedPlayer;

public class Permissions {
    public static String REPORT_BYPASS_COOLDOWN_PERMISSION = "gureport.cooldown.bypass";

    public static String REPORT_STAFF_PROCESS_PERMISSION = "gureport.staff.process";
    public static String REPORT_STAFF_RECEIVE_PERMISSION = "gureport.staff.receive";
    public static String REPORT_STAFF_BLOCK_PERMISSION = "gureport.staff.block";
    public static String REPORT_STAFF_LIST_PERMISSION = "gureport.staff.list";
    public static String REPORT_STAFF_CHATLOG_PERMISSION = "gureport.staff.chatlog";

    //    public static String REPORT_ADMIN_DELETE_PERMISSION = "gureport.admin.delete"; // 别问 问就是史山代码没精力动
    public static String REPORT_ADMIN_RELOAD_PERMISSION = "gureport.admin.reload";
    public static String REPORT_ADMIN_DELETE_ALL_PERMISSION = "gureport.admin.deleteall";

    // 判断是否可以接受举报
    public static boolean canReceiveReport(ProxiedPlayer player) {
        return player.hasPermission(REPORT_STAFF_RECEIVE_PERMISSION)
                && !Report.getInstance().getBlockReportStaffs().contains(player);
    }
}
