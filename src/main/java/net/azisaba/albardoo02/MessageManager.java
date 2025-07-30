package net.azisaba.albardoo02;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;

public class MessageManager {

    private final AzipediaSearcher plugin;
    private final Map<String, Map<String, Object>> allMessages = new HashMap<>();
    private String defaultLang;

    public MessageManager(AzipediaSearcher plugin) {
        this.plugin = plugin;
        loadAllMessages();
    }

    public void loadAllMessages() {
        allMessages.clear();
        this.defaultLang = plugin.getConfig().getString("lang", "ja");

        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File[] langFiles = dataFolder.listFiles((dir, name) -> name.matches("message_\\w+\\.yml"));

        if (langFiles == null || langFiles.length == 0) {
            plugin.saveResource("message_en.yml", false);
            plugin.saveResource("message_ja.yml", false);
            langFiles = dataFolder.listFiles((dir, name) -> name.matches("message_\\w+\\.yml"));
        }

        for (File langFile : langFiles) {
            String langCode = langFile.getName().replace("message_", "").replace(".yml", "");
            FileConfiguration langConfig = YamlConfiguration.loadConfiguration(langFile);
            Map<String, Object> messages = new HashMap<>();
            for (String key : langConfig.getKeys(true)) {
                if (!langConfig.isConfigurationSection(key)) {
                    messages.put(key, langConfig.get(key));
                }
            }
            allMessages.put(langCode, messages);
            plugin.getLogger().info(langFile.getName() + " を読み込みました．");
        }
    }

    public void sendMessage(Player player, String key, String... replacements) {
        String playerLang = getPlayerLanguage(player);
        Object messageObject = getMessageObject(playerLang, key);

        List<String> messagesToSend;
        if (messageObject instanceof List) {
            messagesToSend = (List<String>) messageObject;
        } else {
            messagesToSend = Collections.singletonList(String.valueOf(messageObject));
        }

        for (String line : messagesToSend) {
            String processedLine = ChatColor.translateAlternateColorCodes('&', replacePlaceholders(line, replacements));
            player.sendMessage(processedLine);
        }
    }

    private String getPlayerLanguage(Player player) {
        return player.getLocale().split("_")[0].toLowerCase();
    }

    private Object getMessageObject(String lang, String key) {
        return allMessages.getOrDefault(lang, allMessages.get(defaultLang))
                .getOrDefault(key, "&cメッセージが見つかりません: " + key);
    }

    private String replacePlaceholders(String text, String... replacements) {
        if (replacements.length % 2 != 0) {
            throw new IllegalArgumentException("Replacements must be in pairs.");
        }
        for (int i = 0; i < replacements.length; i += 2) {
            text = text.replace(replacements[i], replacements[i + 1]);
        }
        return text;
    }

    public String getSingleMessage(Player player, String key) {
        String playerLang = getPlayerLanguage(player);
        Object messageObject = getMessageObject(playerLang, key);

        if (messageObject instanceof List) {
            List<?> list = (List<?>) messageObject;
            if (!list.isEmpty()) {
                return ChatColor.translateAlternateColorCodes('&', String.valueOf(list.get(0)));
            }
            return "";
        }

        return ChatColor.translateAlternateColorCodes('&', String.valueOf(messageObject));
    }
}