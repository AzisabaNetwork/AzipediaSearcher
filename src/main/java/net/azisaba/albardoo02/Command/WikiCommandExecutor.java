package net.azisaba.albardoo02.Command;

import net.azisaba.albardoo02.AzipediaSearcher;
import net.azisaba.albardoo02.MessageManager;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class WikiCommandExecutor implements CommandExecutor {

    private final AzipediaSearcher plugin;
    private final MessageManager messageManager;

    private final Map<UUID, JSONArray> searchResultsMap = new HashMap<>();
    private final Map<UUID, Integer> pageMap = new HashMap<>();
    private final Map<UUID, String> queryMap = new HashMap<>();
    private final Map<UUID, Long> cooldownMap = new HashMap<>();

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public WikiCommandExecutor(AzipediaSearcher plugin, MessageManager messageManager) {
        this.plugin = plugin;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "コンソールからは使用できません．");
            return true;
        }

        Player player = (Player) sender;
        if (args.length < 1) {
            if (player.hasPermission("azipediasearcher.command.wiki")) {
                messageManager.sendMessage(player, "HelpAdmin");
            } else {
                messageManager.sendMessage(player, "Help");
            }
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "search":
            case "s":
                handleSearchCommand(player, args);
                break;
            case "next":
                handlePagination(player, true);
                break;
            case "prev":
                handlePagination(player, false);
                break;
            case "help":
            case "h":
                if (player.hasPermission("azipediasearcher.command.wiki")) {
                    messageManager.sendMessage(player, "HelpAdmin");
                } else {
                    messageManager.sendMessage(player, "Help");
                }
                break;
            case "version":
            case "v":
                sendVersionMessage(player);
                break;
            case "config":
            case "c":
                handleConfigCommand(player, args);
                break;
            default:
                messageManager.sendMessage(player, "UnknownCommand", "%command%", args[0]);
                break;
        }
        return true;
    }

    private void handleSearchCommand(Player player, String[] args) {

        if (!player.hasPermission("azipediasearcher.command.bypass")) {
            if (isCooldownActive(player)) return;
        }
        List<String> queryWords = new ArrayList<>();
        int limit = plugin.getConfig().getInt("limit", 50);
        String searchRange = "text";
        String categoryFilter = null;
        String searchType = "AND";

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            String lowerArg = arg.toLowerCase();
            if (lowerArg.startsWith("limit:")) {
                try { limit = Integer.parseInt(arg.substring("limit:".length())); } catch (NumberFormatException ignored) {}
            } else if (lowerArg.startsWith("searchrange:")) {
                if (arg.substring("searchrange:".length()).equalsIgnoreCase("title")) { searchRange = "title"; }
            } else if (lowerArg.startsWith("category:")) {
                categoryFilter = arg.substring("category:".length());
            } else if (lowerArg.startsWith("searchtype:")) {
                if (arg.substring("searchtype:".length()).equalsIgnoreCase("OR")) { searchType = "OR"; }
            } else {
                queryWords.add(arg);
            }
        }

        final int finalLimit = limit;
        final String finalCategoryFilter = categoryFilter;

        if (queryWords.isEmpty() && finalCategoryFilter != null && !finalCategoryFilter.isEmpty()) {
            messageManager.sendMessage(player, "Searching");
            performCategoryListAsync(finalCategoryFilter, finalLimit).thenAcceptAsync(items -> {
                        String displayQuery = "Category:" + finalCategoryFilter;
                        if (items == null || items.isEmpty()) {
                            messageManager.sendMessage(player, "SearchNotFound", "%search%", displayQuery);
                            return;
                        }
                        UUID playerId = player.getUniqueId();
                        searchResultsMap.put(playerId, items);
                        pageMap.put(playerId, 1);
                        queryMap.put(playerId, displayQuery);
                        showSearchResults(player, items, 1, displayQuery);
                    }, runnable -> Bukkit.getScheduler().runTask(plugin, runnable))
                    .exceptionally(ex -> {
                        messageManager.sendMessage(player, "SearchError");
                        ex.printStackTrace();
                        return null;
                    });

        } else if (!queryWords.isEmpty()) {
            String searchQuery;
            if (searchType.equalsIgnoreCase("OR")) {
                searchQuery = String.join(" OR ", queryWords);
            } else {
                searchQuery = String.join(" ", queryWords);
            }

            final String finalSearchRange = searchRange;
            final String finalSearchQuery = searchQuery;

            messageManager.sendMessage(player, "Searching");
            performSearchAsync(finalSearchQuery, finalLimit, finalSearchRange, finalCategoryFilter).thenAcceptAsync(items -> {
                        if (items == null || items.isEmpty()) {
                            messageManager.sendMessage(player, "SearchNotFound", "%search%", finalSearchQuery);
                            return;
                        }
                        UUID playerId = player.getUniqueId();
                        searchResultsMap.put(playerId, items);
                        pageMap.put(playerId, 1);
                        queryMap.put(playerId, finalSearchQuery);
                        showSearchResults(player, items, 1, finalSearchQuery);
                    }, runnable -> Bukkit.getScheduler().runTask(plugin, runnable))
                    .exceptionally(ex -> {
                        messageManager.sendMessage(player, "SearchError");
                        ex.printStackTrace();
                        return null;
                    });
        } else {
            messageManager.sendMessage(player, "SearchUsage");
        }
    }

    private CompletableFuture<JSONArray> performSearchAsync(String query, int limit, String searchRange, String categoryFilter) {
        try {
            StringBuilder searchStringBuilder = new StringBuilder(query);
            if (categoryFilter != null && !categoryFilter.isEmpty()) {
                searchStringBuilder.append(" incategory:\"").append(categoryFilter).append("\"");
            }
            String baseUrl = plugin.getConfig().getString("url");
            String encodedSearchQuery = URLEncoder.encode(searchStringBuilder.toString(), StandardCharsets.UTF_8);
            String urlString = String.format("%s?action=query&format=json&list=search&srsearch=%s&srlimit=%d&srwhat=%s&srprop=snippet",
                    baseUrl, encodedSearchQuery, limit, searchRange);
            HttpRequest request = HttpRequest.newBuilder().uri(new URI(urlString)).GET().build();
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .thenApply(body -> {
                        JSONObject json = new JSONObject(body);
                        return json.has("query") && json.getJSONObject("query").has("search") ? json.getJSONObject("query").getJSONArray("search") : new JSONArray();
                    });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private CompletableFuture<JSONArray> performCategoryListAsync(String category, int limit) {
        try {
            String baseUrl = plugin.getConfig().getString("url");
            String encodedCategory = URLEncoder.encode("Category:" + category, StandardCharsets.UTF_8);

            String urlString = String.format(
                    "%s?action=query&format=json&prop=info%%7Cextracts&inprop=url&exintro=true&explaintext=true&generator=categorymembers&gcmtitle=%s&gcmlimit=%d",
                    baseUrl, encodedCategory, limit
            );

            HttpRequest request = HttpRequest.newBuilder().uri(new URI(urlString)).GET().build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .thenApply(body -> {
                        String trimmedBody = body.trim();
                        if (!trimmedBody.startsWith("{")) {
                            plugin.getLogger().severe("APIから不正な応答がありました (Category List)。urlを確認してください。");
                            plugin.getLogger().severe("応答内容: " + (trimmedBody.length() > 300 ? trimmedBody.substring(0, 300) + "..." : trimmedBody));
                            return new JSONArray();
                        }

                        JSONObject json = new JSONObject(trimmedBody);
                        if (!json.has("query") || !json.getJSONObject("query").has("pages")) {
                            return new JSONArray();
                        }

                        JSONObject pages = json.getJSONObject("query").getJSONObject("pages");
                        JSONArray results = new JSONArray();
                        for (String key : pages.keySet()) {
                            results.put(pages.getJSONObject(key));
                        }
                        return results;
                    });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private void showSearchResults(Player player, JSONArray items, int page, String query) {
        int resultsPerPage = 5;
        int totalResults = items.length();
        int totalPages = (int) Math.ceil((double) totalResults / resultsPerPage);
        int start = (page - 1) * resultsPerPage;
        int end = Math.min(start + resultsPerPage, totalResults);

        messageManager.sendMessage(player, "SearchResult", "%search%", query, "%totalResults%", String.valueOf(totalResults));

        for (int i = start; i < end; i++) {
            JSONObject item = items.getJSONObject(i);
            String title = item.getString("title");
            String baseUrl = plugin.getConfig().getString("api-url", "https://wiki.azisaba.net/api.php");
            String link = baseUrl.replace("api.php", "index.php/") + URLEncoder.encode(title.replace(" ", "_"), StandardCharsets.UTF_8);

            TextComponent message = new TextComponent(ChatColor.GRAY + "[" + (i + 1) + "] " + ChatColor.WHITE + title);
            message.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, link));
            ComponentBuilder hoverBuilder = new ComponentBuilder(ChatColor.AQUA + title);

            if (item.has("snippet")) {
                String snippet = item.getString("snippet").replaceAll("<.*?>", "");
                if (!snippet.isEmpty()) {
                    hoverBuilder.append("\n" + ChatColor.GRAY + snippet);
                }
            }
            String openUrlMessage = messageManager.getSingleMessage(player, "OpenUrl");
            hoverBuilder.append("\n" + ChatColor.DARK_GRAY + openUrlMessage);
            message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverBuilder.create()));
            player.spigot().sendMessage(message);
        }

        TextComponent navigation = new TextComponent();
        if (page > 1) {
            TextComponent prevButton = new TextComponent(ChatColor.BLUE + "◀ " + ChatColor.RESET);
            prevButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wiki prev"));
            prevButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(messageManager.getSingleMessage(player, "PrevPage")).create()));
            navigation.addExtra(prevButton);
        }
        navigation.addExtra(new TextComponent(messageManager.getSingleMessage(player, "Page") + " §e" + page + " / " + totalPages + " "));
        if (page < totalPages) {
            TextComponent nextButton = new TextComponent(ChatColor.BLUE + "▶" + ChatColor.RESET);
            nextButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wiki next"));
            nextButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(messageManager.getSingleMessage(player, "NextPage")).create()));
            navigation.addExtra(nextButton);
        }
        player.spigot().sendMessage(navigation);
    }

    private void handlePagination(Player player, boolean isNext) {
        UUID playerId = player.getUniqueId();
        if (!searchResultsMap.containsKey(playerId)) {
            messageManager.sendMessage(player, "NoSearch");
            return;
        }
        int currentPage = pageMap.getOrDefault(playerId, 1);
        JSONArray items = searchResultsMap.get(playerId);
        String query = queryMap.getOrDefault(playerId, "");
        int totalPages = (int) Math.ceil((double) items.length() / 5.0);
        int newPage = isNext ? currentPage + 1 : currentPage - 1;

        if (newPage > 0 && newPage <= totalPages) {
            pageMap.put(playerId, newPage);
            showSearchResults(player, items, newPage, query);
        } else {
            messageManager.sendMessage(player, "PageEnd");
        }
    }

    private boolean isCooldownActive(Player player) {
        UUID playerId = player.getUniqueId();
        int cooldownTime = plugin.getConfig().getInt("cooldown", 5);
        if (cooldownTime <= 0) return false;
        long currentTime = System.currentTimeMillis();
        long lastUsed = cooldownMap.getOrDefault(playerId, 0L);

        if ((currentTime - lastUsed) < cooldownTime * 1000L) {
            long remainingTime = (cooldownTime * 1000L - (currentTime - lastUsed)) / 1000;
            messageManager.sendMessage(player, "CooldownMessage", "%time%", String.valueOf(remainingTime));
            return true;
        }
        cooldownMap.put(playerId, currentTime);
        return false;
    }

    private void sendVersionMessage(CommandSender sender) {
        sender.sendMessage("§fAzipediaSearcher Version§a: " + plugin.getDescription().getVersion());
        sender.sendMessage("§fWebsite: §a" + plugin.getDescription().getWebsite());
    }

    private void handleConfigCommand(Player player, String[] args) {
        if (args.length > 1 && args[1].equalsIgnoreCase("reload")) {
            if (!player.hasPermission("azipediasearcher.command.reload")) {
                messageManager.sendMessage(player, "NoPermission");
                return;
            }
            plugin.reloadConfig();
            messageManager.loadAllMessages();
            messageManager.sendMessage(player, "ReloadConfig");
        } else {
            messageManager.sendMessage(player, "ConfigCommandHelp");
        }
    }
}