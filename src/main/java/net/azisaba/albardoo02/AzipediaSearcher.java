package net.azisaba.albardoo02;

import net.azisaba.albardoo02.Command.WikiCommandExecutor;
import net.azisaba.albardoo02.Command.WikiCommandTabCompleter;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public final class AzipediaSearcher extends JavaPlugin {

    private MessageManager messageManager;

    @Override
    public void onEnable() {
        this.getLogger().info("Plugin has been enabled");

        String currentVersion = getConfig().getString("configVersion", "0.0");
        String CONFIG_VERSION = "1.1";
        if (!currentVersion.equals(CONFIG_VERSION)) {
            getLogger().info(ChatColor.YELLOW + "新しいバージョンが見つかったため，ファァイルを更新しています...");
            moveOldFiles();
            saveNewFiles();
        } else {
            getLogger().info("config.ymlは最新バージョンです");
            saveIfNotExists("message_ja.yml");
            saveIfNotExists("message_en.yml");
        }

        this.saveDefaultConfig();
        messageManager = new MessageManager(this);

        this.getCommand("wiki").setExecutor(new WikiCommandExecutor(this, messageManager));
        this.getCommand("wiki").setTabCompleter(new WikiCommandTabCompleter(this));

    }

    private void moveOldFiles() {
        moveFileToOld("message_ja.yml");
        moveFileToOld("message_en.yml");
    }

    private void moveFileToOld(String fileName) {
        File file = new File(getDataFolder(), fileName);
        if (!file.exists()) return;

        File oldFolder = new File(getDataFolder(), "old");
        if (!oldFolder.exists()) {
            oldFolder.mkdir();
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File backupFile = new File(oldFolder, fileName.replace(".yml", "_" + timestamp + ".yml"));

        if (file.renameTo(backupFile)) {
            getLogger().info(fileName + " をoldフォルダに移動しました: " + backupFile.getName());
        } else {
            getLogger().warning(fileName + " の移動に失敗しました");
        }
    }

    private void saveNewFiles() {
        saveResource("message_ja.yml", true);
        saveResource("message_en.yml", true);
    }

    private void saveIfNotExists(String fileName) {
        File file = new File(getDataFolder(), fileName);
        if (!file.exists()) {
            saveResource(fileName, false);
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        this.saveConfig();
        this.getLogger().info("plugin has been disabled");
    }
}
