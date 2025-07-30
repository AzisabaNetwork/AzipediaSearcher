package net.azisaba.albardoo02.Command;

import net.azisaba.albardoo02.AzipediaSearcher;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class WikiCommandTabCompleter implements TabCompleter {

    private final AzipediaSearcher plugin;

    public WikiCommandTabCompleter(AzipediaSearcher plugin) {
        this.plugin = plugin;
    }

    private static final List<String> SUB_COMMANDS = Arrays.asList("search", "next", "prev", "help", "version", "config");
    private static final List<String> SEARCH_OPTIONS = Arrays.asList("limit:", "category:", "searchrange:", "searchtype:");
    private static final List<String> SEARCH_RANGE_VALUES = Arrays.asList("text", "title");
    private static final List<String> SEARCH_TYPE_VALUES = Arrays.asList("AND", "OR");
    private static final List<String> CONFIG_SUB_COMMANDS = Collections.singletonList("reload");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], SUB_COMMANDS, new ArrayList<>());
        }
        String subCommand = args[0].toLowerCase();
        if (subCommand.equals("search") || subCommand.equals("s")) {
            return getSearchSuggestions(args);
        }
        if (subCommand.equals("config") || subCommand.equals("c")) {
            if (args.length == 2) {
                return StringUtil.copyPartialMatches(args[1], CONFIG_SUB_COMMANDS, new ArrayList<>());
            }
        }
        return Collections.emptyList();
    }

    private List<String> getSearchSuggestions(String[] args) {
        final String currentArg = args[args.length - 1];
        final List<String> suggestions = new ArrayList<>();

        if (currentArg.toLowerCase().startsWith("category:")) {
            List<String> categories = plugin.getConfig().getStringList("category");
            for (String category : categories) {
                suggestions.add("category:" + category);
            }
            return StringUtil.copyPartialMatches(currentArg, suggestions, new ArrayList<>());
        }
        if (currentArg.toLowerCase().startsWith("searchrange:")) {
            for (String value : SEARCH_RANGE_VALUES) suggestions.add("searchrange:" + value);
            return StringUtil.copyPartialMatches(currentArg, suggestions, new ArrayList<>());
        }
        if (currentArg.toLowerCase().startsWith("searchtype:")) {
            for (String value : SEARCH_TYPE_VALUES) suggestions.add("searchtype:" + value);
            return StringUtil.copyPartialMatches(currentArg, suggestions, new ArrayList<>());
        }

        List<String> availableOptions = new ArrayList<>(SEARCH_OPTIONS);
        for (int i = 1; i < args.length - 1; i++) {
            final String usedArg = args[i].toLowerCase();
            availableOptions.removeIf(option -> usedArg.startsWith(option));
        }

        return StringUtil.copyPartialMatches(currentArg, availableOptions, new ArrayList<>());
    }
}