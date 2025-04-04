package cn.linmoyu.gureport.utils;

import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.md_5.bungee.api.ChatColor;

import java.util.UUID;

public class LuckPermsUtils {

    public static String getPrefix(UUID uuid) {
        User user = LuckPermsProvider.get().getUserManager().getUser(uuid);
        if (user == null) return "";
        String prefix = user.getCachedData().getMetaData().getPrefix();
        return prefix == null ? "" : ChatColor.translateAlternateColorCodes('&', prefix);
    }

    public static String getPrefixColor(UUID uuid) {
        User user = LuckPermsProvider.get().getUserManager().getUser(uuid);
        if (user == null) return "";
        String prefix = user.getCachedData().getMetaData().getPrefix();
        return prefix == null ? "" : ChatColor.translateAlternateColorCodes('&', prefix).substring(0, 2);
    }

    public static String getSuffix(UUID uuid) {
        User user = LuckPermsProvider.get().getUserManager().getUser(uuid);
        if (user == null) return "";
        String suffix = user.getCachedData().getMetaData().getPrefix();
        return suffix == null ? "" : ChatColor.translateAlternateColorCodes('&', suffix);
    }
}
