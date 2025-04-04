package cn.linmoyu.gureport.listener;

import cn.linmoyu.gureport.Report;
import cn.linmoyu.gureport.utils.Permissions;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

public class BlockMessagePlayerListener implements Listener {

    @EventHandler
    public void onStaffLeave(PlayerDisconnectEvent event) {
        ProxiedPlayer player = event.getPlayer();
        if (!player.hasPermission(Permissions.REPORT_STAFF_BLOCK_PERMISSION)) return;
        Report.getInstance().getBlockReportStaffs().remove(player);
    }
}
