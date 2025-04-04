package cn.linmoyu.gureport.command;

import cn.linmoyu.gureport.Report;
import cn.linmoyu.gureport.utils.Permissions;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

import java.util.HashSet;

public class ReportBlockCommand extends Command {

    public ReportBlockCommand() {
        super("reportblock", Permissions.REPORT_STAFF_BLOCK_PERMISSION, "举报屏蔽", "jubaopingbi", "jbpb", "屏蔽举报", "pingbijubao", "pbjb");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof ProxiedPlayer)) return;

        ProxiedPlayer player = (ProxiedPlayer) sender;
        HashSet<ProxiedPlayer> BlockReportStaffs = Report.getInstance().getBlockReportStaffs();
        if (BlockReportStaffs.contains(player)) {
            Report.getInstance().getBlockReportStaffs().remove(player);
            Report.sendMessageWithPrefix(player, "§a已切换为永久接收举报消息.");
        } else {
            Report.getInstance().getBlockReportStaffs().add(player);
            Report.sendMessageWithPrefix(player, "§c已切换为暂时拒绝举报消息.");
        }
    }
}