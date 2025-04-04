package cn.linmoyu.gureport.listener;

import cn.linmoyu.gureport.Report;
import cn.linmoyu.gureport.manager.RedisManager;
import cn.linmoyu.gureport.utils.RedisKeys;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import org.json.JSONObject;
import redis.clients.jedis.Jedis;

import java.util.ArrayList;
import java.util.Arrays;

import static cn.linmoyu.gureport.manager.ConfigManager.config;

public class RecordPlayerChat implements Listener {

    private final ArrayList<String> blockString = new ArrayList<>(Arrays.asList(
            "/l ", "/log ", "/login ", "/reg ", "/register ", "/changepassword ", "/cp "
    ));

    @EventHandler
    public void onPlayerChat(ChatEvent event) {
        if (event.isCancelled() || !(event.getSender() instanceof ProxiedPlayer)) return;
        ProxiedPlayer player = (ProxiedPlayer) event.getSender();
        String message = event.getMessage();

        if (blockString.stream().anyMatch(message::startsWith)) return;

        Report.getInstance().getProxy().getScheduler().runAsync(Report.getInstance(), () -> {
            try (Jedis jedis = RedisManager.getInstance().getJedisPool().getResource()) {
                JSONObject msgData = new JSONObject();
                msgData.put("time", System.currentTimeMillis());
                msgData.put("server", player.getServer().getInfo().getName());
                msgData.put("chat", message);

                String key = RedisKeys.CHAT_RECORD_PREFIX + player.getUniqueId();
                jedis.lpush(key, msgData.toString());
                jedis.ltrim(key, 0, config.getInt("chatlog.max-messages") - 1);
            }
        });
    }

}
