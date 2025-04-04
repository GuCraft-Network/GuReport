package cn.linmoyu.gureport.listener;

import cn.linmoyu.gureport.Report;
import cn.linmoyu.gureport.utils.BuildReportMessage;
import cn.linmoyu.gureport.utils.Permissions;
import com.imaginarycode.minecraft.redisbungee.events.PubSubMessageEvent;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.util.logging.Level;

public class RedisListener implements Listener {

    public static final String SEND_REPORT_PREFIX = "sendReport:";
    public static final String SEND_MESSAGE_PREFIX = "sendMessage:";

    @EventHandler
    public void onRedisBungeeMessage(PubSubMessageEvent event) {
        if (event == null || !event.getChannel().equals(Report.getInstance().BUNGEE_CHANNEL_NAME)) {
            return;
        }

        String message = event.getMessage();
        if (message == null || message.isEmpty()) {
            return;
        }

        // RedisBungeeChannel 只能传递String 只能以特定字符开头来判断要传递什么消息了
        // sendReport: 传递给reportID让方法来构建消息
        // sendMessage: 负责内部传递工作人员消息(例如处理、删除, 在发送消息时构建消息)
        if (message.startsWith(SEND_REPORT_PREFIX)) {
            String reportId = message.substring(SEND_REPORT_PREFIX.length());
            BuildReportMessage.buildReportToProxyStaff(reportId);
        } else if (message.startsWith(SEND_MESSAGE_PREFIX)) {
            String content = message.substring(SEND_MESSAGE_PREFIX.length());

            ProxyServer.getInstance().getPlayers().stream()
                    .filter(Permissions::canReceiveReport)
                    .forEach(player ->
                            Report.sendMessageWithPrefix(player, content)
                    );

            Report.getInstance().getLogger().log(Level.INFO, content);
        }
    }

}