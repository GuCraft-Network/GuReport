package cn.linmoyu.gureport.utils;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class LuckPermsUtils {

    static LuckPerms luckPerms = LuckPermsProvider.get();
    static UserManager userManager = luckPerms.getUserManager();

    public static String getPrefix(UUID uuid) {
        User user = getUser(uuid);
        if (user == null) return "";
        String prefix = user.getCachedData().getMetaData().getPrefix();
        if (prefix == null) return "";
        return prefix;
    }

    public static String getSuffix(UUID uuid) {
        User user = getUser(uuid);
        if (user == null) return "";
        String suffix = user.getCachedData().getMetaData().getSuffix();
        if (suffix == null) return "";
        return suffix;
    }

    public static User getUser(UUID uuid) {
        if (userManager == null) {
            return null;
        }

        if (userManager.isLoaded(uuid)) {
            return userManager.getUser(uuid);
        } else {
            CompletableFuture<User> userCompletableFuture = userManager.loadUser(uuid);
            return userCompletableFuture.join();
        }
    }

}