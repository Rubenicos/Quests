package com.leonardobishop.quests.module;

import com.leonardobishop.quests.Quests;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QLocale {

    private static final Quests pl = Quests.get();

    private static FileConfiguration defaultLang;
    private static FileConfiguration lang;

    public static boolean papi = false;

    private static boolean noTitle = false;

    private static boolean oldVersion = false;
    private static boolean sendPackets = false;

    private static boolean rgb = false;
    private static Pattern pattern;

    public static void load(String language) {
        defaultLang = QFiles.getFromPlugin("lang/en_US.yml");
        reload(language, true);
        String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
        if (version.startsWith("v1_7") || version.equals("v1_8_R1")) {
            noTitle = true;
            pl.getQuestsLogger().info("Titles are not supported for this version.");
        } else if (version.startsWith("v1_8") || version.startsWith("v1_9") || version.startsWith("v1_10") || version.startsWith("v1_11")) {
            oldVersion = true;
            if (pl.getSettings().getBoolean("Locale.SendPackets")) {
                sendPackets = true;
                setupReflection(version);
            }
        }
        if (pl.getSettings().getBoolean("Locale.AllowRGB") && Integer.parseInt(version.split("_")[1]) >= 16) {
            rgb = true;
            pattern = Pattern.compile("(?<!\\\\)(&#[a-fA-F0-9]{6})");
        }
        if (pl.getConfig().contains("messages")) runConverter();
    }

    public static void reload(String language, boolean update) {
        lang = QFiles.getFromFile("lang/" + language + ".yml", "lang/en_US.yml", update);
    }

    public static void sendTo(CommandSender sender, String path, String... args) {
        if (sender instanceof Player) {
            getList(path).forEach(string -> sendMsgTo((Player) sender, string, args));
        } else {
            getList(path).forEach(string -> sendMsgTo(sender, string, args));
        }
    }

    public static void sendTo(Player player, String path, String... args) {
        getList(path).forEach(string -> sendMsgTo(player, string, args));
    }

    public static void sendMsgTo(CommandSender sender, String msg, String... args) {
        sender.sendMessage(color(setArgs(msg, args)));
    }

    public static void sendMsgTo(Player player, String msg, String... args) {
        player.sendMessage(setPlaceholders(player, setArgs(msg, args), true));
    }

    public static void sendTitleByName(Player player, String name, String... args) {
        sendTitle(player, getString("Title." + name + ".title", args), getString("Title." + name + ".subtitle", args), getInt("Title." + name + ".fadeIn"), getInt("Title." + name + ".stay"), getInt("Title." + name + ".fadeOut"));
    }

    public static void sendTitle(Player player, String title, String subtitle, String... args) {
        sendTitle(player, setArgs(title, args), setArgs(subtitle, args), 10, 100, 10);
    }

    public static void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut, String... args) {
        sendTitle(player, setArgs(title, args), setArgs(subtitle, args), fadeIn, stay, fadeOut);
    }

    @SuppressWarnings("deprecation")
    public static void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        if (noTitle) return;
        if (oldVersion) {
            if (sendPackets) {
                sendTitlePacket(player, setPlaceholders(player, title, true), setPlaceholders(player, subtitle, true), fadeIn, stay, fadeOut);
            } else {
                player.sendTitle(setPlaceholders(player, title, true), setPlaceholders(player, subtitle, true));
            }
        } else {
            player.sendTitle(setPlaceholders(player, title, true), setPlaceholders(player, subtitle, true), fadeIn, stay, fadeOut);
        }
    }

    public static String getString(String path, String... args) {
        return setArgs(lang.getString(path, defaultLang.getString(path)), args);
    }

    public static List<String> getStringList(String path, String... args) {
        final List<String> list = lang.getStringList(path);
        return (list.isEmpty() ? defaultLang.getStringList(path) : list);
    }

    public static List<String> getList(String path) {
        final Object object = lang.get(path, defaultLang.get(path));
        if (object instanceof String) {
            return Collections.singletonList((String) object);
        } else if (object instanceof List) {
            return (List<String>) object;
        }
        return Collections.emptyList();
    }

    public static int getInt(String path) {
        return lang.getInt(path, defaultLang.getInt(path));
    }

    public static String setPlaceholders(Player player, String text, boolean color) {
        if (papi && text.contains("%")) text = PlaceholderAPI.setPlaceholders(player, text);
        return (color ? color(text) : text);
    }

    public static List<String> color(List<String> list) {
        List<String> text = new ArrayList<>();
        list.forEach(s -> text.add(color(s)));
        return text;
    }

    public static String color(String s) {
        if (rgb && s.contains("&#")) {
            Matcher matcher = pattern.matcher(s);
            while (matcher.find()) {
                String color = matcher.group();
                s = s.replace(color, "" + net.md_5.bungee.api.ChatColor.of(color.replace("&", "")));
            }
        }
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public static String setArgs(String s, String... args) {
        return MessageFormat.format(s, (Object[]) args);
    }

    private static Map<String, Class<?>> classCache;
    private static Map<String, Method> methodCache;
    private static Map<String, Field> fieldCache;
    private static Constructor<?> packetPlayOutTitle;

    private static void setupReflection(String version) {
        try {
            classCache = new HashMap<>();
            classCache.put("IChatBaseComponent", Class.forName("net.minecraft.server." + version + "." + "IChatBaseComponent"));
            classCache.put("ChatSerializer", classCache.get("IChatBaseComponent").getDeclaredClasses()[0]);
            classCache.put("PacketPlayOutTitle", Class.forName("net.minecraft.server." + version + "." + "PacketPlayOutTitle"));
            classCache.put("CraftPlayer", Class.forName("org.bukkit.craftbukkit." + version + ".entity." + "CraftPlayer"));

            methodCache = new HashMap<>();
            methodCache.put("a", classCache.get("ChatSerializer").getMethod("a", String.class));
            methodCache.put("getHandle", classCache.get("CraftPlayer").getMethod("getHandle"));
            methodCache.put("sendPacket", Class.forName("net.minecraft.server." + version + "." + "PlayerConnection").getMethod("sendPacket", Class.forName("net.minecraft.server." + version + "." + "Packet")));

            fieldCache = new HashMap<>();
            fieldCache.put("playerConnection", Class.forName("net.minecraft.server." + version + "." + "EntityPlayer").getField("playerConnection"));
            fieldCache.put("TIMES", classCache.get("PacketPlayOutTitle").getDeclaredClasses()[0].getField("TIMES"));
            fieldCache.put("TITLE", classCache.get("PacketPlayOutTitle").getDeclaredClasses()[0].getField("TITLE"));
            fieldCache.put("SUBTITLE", classCache.get("PacketPlayOutTitle").getDeclaredClasses()[0].getField("SUBTITLE"));

            packetPlayOutTitle = classCache.get("PacketPlayOutTitle").getConstructor(classCache.get("PacketPlayOutTitle").getDeclaredClasses()[0], classCache.get("IChatBaseComponent"), int.class, int.class, int.class);
        } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException e) {
            e.printStackTrace();
            sendPackets = false;
        }
    }

    private static void sendTitlePacket(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        try {
            Object chatTitle = methodCache.get("a").invoke(classCache.get("ChatSerializer"), "{\"text\" : \"" + title + "\"}");
            Object chatSubtitle = methodCache.get("a").invoke(classCache.get("ChatSerializer"), "{\"text\" : \"" + subtitle + "\"}");

            Object packet1 = packetPlayOutTitle.newInstance(fieldCache.get("TIMES").get(null), chatTitle, fadeIn, stay, fadeOut);
            Object packet2 = packetPlayOutTitle.newInstance(fieldCache.get("TITLE").get(null), chatTitle, fadeIn, stay, fadeOut);
            Object packet3 = packetPlayOutTitle.newInstance(fieldCache.get("SUBTITLE").get(null), chatSubtitle, fadeIn, stay, fadeOut);

            Object playerConnection = fieldCache.get("playerConnection").get(methodCache.get("getHandle").invoke(classCache.get("CraftPlayer").cast(player)));
            methodCache.get("sendPacket").invoke(playerConnection, packet1);
            methodCache.get("sendPacket").invoke(playerConnection, packet2);
            methodCache.get("sendPacket").invoke(playerConnection, packet3);
        } catch (IllegalAccessException | InvocationTargetException | InstantiationException e) {
            e.printStackTrace();
        }
    }

    private static void runConverter() {
        pl.getConfig().set("Locale.Language", "Old-Messages");
        for (OldPaths path : OldPaths.values()) {
            String s = pl.getConfig().getString(path.getOldPath());
            if (s != null) {
                int num = 0;
                for (String arg : path.getArgs()) {
                    s = s.replace(arg, "{" + num + "}");
                    num++;
                }
                lang.set(path.getNewPath(), s);
            }
        }
        pl.getConfig().set("titles", null);
        pl.getConfig().set("messages", null);
        pl.saveConfig();
        try {
            lang.save(Quests.get().getDataFolder() + File.separator + "lang" + File.separator + "Old-Messages.yml");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private enum OldPaths {
        TIME_FORMAT("messages.time-format", "Message.Time-Format", "{hours}", "{minutes}", "{seconds}"),
        QUEST_START("messages.quest-start", "Quest.Start.Success", "{quest}"),
        QUEST_COMPLETE("messages.quest-complete", "Quest.Complete.Success", "{quest}"),
        QUEST_CANCEL("messages.quest-cancel", "Quest.Cancel.Success", "{quest}"),
        QUEST_TRACK("messages.quest-track", "Quest.Track.Tracking", "{quest}"),
        QUEST_TRACK_STOP("messages.quest-track-stop", "Quest.Track.Cancel", "{quest}"),
        QUEST_START_LIMIT("messages.quest-start-limit", "Quest.Start.Fail.Limit", "{limit}"),
        QUEST_START_DISABLED("messages.quest-start-disabled", "Quest.Start.Fail.Disabled"),
        QUEST_START_LOCKED("messages.quest-start-locked", "Quest.Start.Fail.Locked"),
        QUEST_START_COOLDOWN("messages.quest-start-cooldown", "Quest.Start.Fail.Cooldown", "{time}"),
        QUEST_START_STARTED("messages.quest-start-started", "Quest.Start.Fail.Started"),
        QUEST_START_PERMISSION("messages.quest-start-permission", "Quest.Start.Fail.Permission"),
        QUEST_CATEGORY_QUEST_PERMISSION("messages.quest-category-quest-permission", "Quest.Category.Quest-Permission"),
        QUEST_CATEGORY_PERMISSION("messages.quest-category-permission", "Quest.Category.Permission"),
        QUEST_CANCEL_NOTSTARTED("messages.quest-cancel-notstarted", "Quest.Cancel.Not-Started"),
        QUEST_UPDATER("messages.quest-updater", "Quest.Updater", "{newver}", "{oldver}", "{link}"),
        COMMAND_SUB_DOESNTEXIST("messages.command-sub-doesntexist", "Command.Not-Exist", "{sub}"),
        COMMAND_QUEST_START_DOESNTEXIST("messages.command-quest-start-doesntexist", "Command.Quest.Start.Not-Exist", "{quest}"),
        COMMAND_QUEST_GENERAL_DOESNTEXIST("messages.command-quest-general-doesntexist", "Command.Quest.General.Not-Exist", "{quest}"),
        COMMAND_QUEST_OPENCATEGORY_ADMIN_SUCCESS("messages.command-quest-opencategory-admin-success", "Command.Admin.Opengui.Category.Success", "{category}", "{player}"),
        COMMAND_QUEST_OPENQUESTS_ADMIN_SUCCESS("messages.command-quest-openquests-admin-success", "Command.Admin.Opengui.Quests.Success", "{player}"),
        COMMAND_QUEST_ADMIN_PLAYERNOTFOUND("messages.command-quest-admin-playernotfound", "Command.Admin.Not-Found", "{player}"),
        COMMAND_CATEGORY_OPEN_DOESNTEXIST("messages.command-category-open-doesntexist", "Command.Category.Open.Not-Exist", "{category}"),
        COMMAND_CATEGORY_OPEN_DISABLED("messages.command-category-open-disabled", "Command.Category.Open.Disabled"),
        COMMAND_TASKVIEW_ADMIN_FAIL("messages.command-taskview-admin-fail", "Command.Admin.Types.Not-Exist", "{task}"),
        TITLE_QUEST_START_TITLE("titles.quest-start.title", "Title.Quest-Started.title", "{quest}"),
        TITLE_QUEST_START_SUBTITLE("titles.quest-start.subtitle", "Title.Quest-Started.subtitle", "{quest}"),
        TITLE_QUEST_COMPLETE_TITLE("titles.quest-complete.title", "Title.Quest-Complete.title", "{quest}"),
        TITLE_QUEST_COMPLETE_SUBTITLE("titles.quest-complete.subtitle", "Title.Quest-Complete.subtitle", "{quest}"),
        BETA_REMINDER("messages.beta-reminder", "Message.Beta-Reminder"),
        COMMAND_QUEST_ADMIN_LOADDATA("messages.command-quest-admin-loaddata", "Command.Admin.LoadData", "{player}"),
        COMMAND_QUEST_ADMIN_NODATA("messages.command-quest-admin-nodata", "Command.Admin.NoData", "{player}"),
        COMMAND_QUEST_ADMIN_CLEAN_SUCCESS("messages.command-quest-admin-clean-success", "Command.Admin.ModData.Clean.Success"),
        COMMAND_QUEST_ADMIN_CLEAN_FAIL("messages.command-quest-admin-clean-fail", "Command.Admin.ModData.Clean.Fail"),
        COMMAND_QUEST_ADMIN_FULLRESET("messages.command-quest-admin-fullreset", "Command.Admin.ModData.FullReset.Success", "{player}"),
        COMMAND_QUEST_ADMIN_START_FAILLOCKED("messages.command-quest-admin-start-faillocked", "Command.Admin.ModData.Start.Fail.Locked", "{quest}", "{player}"),
        COMMAND_QUEST_ADMIN_START_FAILCOOLDOWN("messages.command-quest-admin-start-failcooldown", "Command.Admin.ModData.Start.Fail.Cooldown", "{quest}", "{player}"),
        COMMAND_QUEST_ADMIN_START_FAILCOMPLETE("messages.command-quest-admin-start-failcomplete", "Command.Admin.ModData.Start.Fail.Complete", "{quest}", "{player}"),
        COMMAND_QUEST_ADMIN_START_FAILLIMIT("messages.command-quest-admin-start-faillimit", "Command.Admin.ModData.Start.Fail.Limit", "{quest}", "{player}"),
        COMMAND_QUEST_ADMIN_START_FAILSTARTED("messages.command-quest-admin-start-failstarted", "Command.Admin.ModData.Start.Fail.Started", "{quest}", "{player}"),
        COMMAND_QUEST_ADMIN_START_FAILPERMISSION("messages.command-quest-admin-start-failpermission", "Command.Admin.ModData.Start.Fail.Permission", "{quest}", "{player}"),
        COMMAND_QUEST_ADMIN_START_FAILCATEGORYPERMISSION("messages.command-quest-admin-start-failcategorypermission", "Command.Admin.ModData.Start.Fail.Category-Perm", "{quest}", "{player}"),
        COMMAND_QUEST_ADMIN_START_FAILOTHER("messages.command-quest-admin-start-failother", "Command.Admin.ModData.Start.Fail.Other", "{quest}", "{player}"),
        COMMAND_QUEST_ADMIN_START_SUCCESS("messages.command-quest-admin-start-success", "Command.Admin.ModData.Start.Success", "{quest}", "{player}"),
        COMMAND_QUEST_ADMIN_CATEGORY_PERMISSION("messages.command-quest-admin-category-permission", "Command.Admin.Opengui.Quests.Permission", "{category}", "{player}"),
        COMMAND_QUEST_ADMIN_COMPLETE_SUCCESS("messages.command-quest-admin-complete-success", "Command.Admin.Complete.Success", "{quest}", "{player}"),
        COMMAND_QUEST_ADMIN_RESET_SUCCESS("messages.command-quest-admin-reset-success", "Command.Admin.Reset.Success", "{quest}", "{player}");

        private final String oldPath;
        private final String newPath;
        private final List<String> args = new ArrayList<>();

        OldPaths(String oldPath, String newPath, String... args) {
            this.oldPath = oldPath;
            this.newPath = newPath;
            this.args.addAll(Arrays.asList(args));
        }

        public String getOldPath() {
            return oldPath;
        }

        public String getNewPath() {
            return newPath;
        }

        public List<String> getArgs() {
            return args;
        }
    }
}
