package com.keidev.mccord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MCCord extends JavaPlugin {
    private FileConfiguration config;
    private JDA jda;
    private ServerStatus serverStatus;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();

        // Inisialisasi bot secara asynchronous
        initializeBotAsync().exceptionally(throwable -> {
            getLogger().warning(ChatColor.RED + "Bot initialization failed. Please set a valid token in config.yml and use /mccord reload.");
            return null;
        });

        // Register commands
        getCommand("announce").setExecutor(new AnnounceCommand(this));
        getCommand("alert").setExecutor(new AlertCommand(this));
        getCommand("report").setExecutor(new ReportCommand(this));
        MCCordCommand mccordCommand = new MCCordCommand(this);
        getCommand("mccord").setExecutor(mccordCommand);
        getCommand("mccord").setTabCompleter(mccordCommand);

        // Pesan enable yang menarik dengan warna dan hiasan
        getLogger().info("");
        getLogger().info(ChatColor.YELLOW + ChatColor.BOLD.toString() + "=====================================");
        getLogger().info(ChatColor.GOLD + ChatColor.BOLD.toString() + " __  __  ___ ___            _ ");
        getLogger().info(ChatColor.GOLD + ChatColor.BOLD.toString() + "|  \\/  |/ __/ __|___ _ _ __| |");
        getLogger().info(ChatColor.GOLD + ChatColor.BOLD.toString() + "| |\\/| | (_| (__/ _ \\ '_/ _` |");
        getLogger().info(ChatColor.GOLD + ChatColor.BOLD.toString() + "|_|  |_|\\___\\___\\___/_| \\__,_|");
        getLogger().info(ChatColor.YELLOW + ChatColor.BOLD.toString() + "=====================================");
        getLogger().info(ChatColor.GREEN + ChatColor.BOLD.toString() + " MCCord Plugin Enabled!");
        getLogger().info(ChatColor.GRAY + " Version: " + ChatColor.WHITE + getDescription().getVersion());
        getLogger().info(ChatColor.GRAY + " Author: " + ChatColor.WHITE + String.join(", ", getDescription().getAuthors()));
        getLogger().info(ChatColor.GRAY + " Website: " + ChatColor.WHITE + (getDescription().getWebsite() != null ? getDescription().getWebsite() : "Not specified"));
        getLogger().info(ChatColor.GRAY + " Running on: " + ChatColor.WHITE + Bukkit.getServer().getName());
        getLogger().info(ChatColor.YELLOW + ChatColor.BOLD.toString() + "=====================================");
        getLogger().info("");
    }

    @Override
    public void onDisable() {
        if (serverStatus != null) {
            serverStatus.stop();
            serverStatus = null;
        }
        shutdownBot();

        // Pesan disable yang menarik dengan warna dan hiasan
        getLogger().info("");
        getLogger().info(ChatColor.RED + ChatColor.BOLD.toString() + "=====================================");
        getLogger().info(ChatColor.DARK_RED + ChatColor.BOLD.toString() + " MCCord Plugin Disabled!");
        getLogger().info(ChatColor.GRAY + " Goodbye from " + ChatColor.WHITE + String.join(", ", getDescription().getAuthors()) + ChatColor.GRAY + " - See you soon!");
        getLogger().info(ChatColor.RED + ChatColor.BOLD.toString() + "=====================================");
        getLogger().info("");
    }

    private CompletableFuture<JDA> initializeBotAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String botToken = config.getString("discord.bot-token");
                if (botToken == null || botToken.isEmpty() || botToken.equals("YOUR_BOT_TOKEN_HERE")) {
                    getLogger().warning(ChatColor.RED + "Discord bot token is not set or invalid in config.yml!");
                    return null;
                }

                getLogger().info(ChatColor.AQUA + "Connecting to Discord...");
                JDA jdaInstance = JDABuilder.createDefault(botToken)
                        .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                        .build();
                jdaInstance.awaitReady();
                getLogger().info(ChatColor.GREEN + "Discord bot connected successfully!");
                this.jda = jdaInstance;
                if (config.getBoolean("embed.server-status.enable", true)) {
                    Bukkit.getScheduler().runTask(this, () -> {
                        serverStatus = new ServerStatus(this);
                        serverStatus.start();
                    });
                }
                return jdaInstance;
            } catch (Exception e) {
                getLogger().severe(ChatColor.RED + "Failed to connect to Discord: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        });
    }

    private void shutdownBot() {
        if (jda != null) {
            try {
                getLogger().info(ChatColor.AQUA + "Shutting down Discord bot...");
                jda.shutdown();
                jda.awaitShutdown(Duration.ofMillis(5000));
                getLogger().info(ChatColor.GREEN + "Discord bot disconnected successfully!");
            } catch (Exception e) {
                getLogger().warning(ChatColor.RED + "Error during Discord bot shutdown: " + e.getMessage());
                e.printStackTrace();
            }
            jda = null;
        }
    }

    public void reloadPlugin() {
        getLogger().info("");
        getLogger().info(ChatColor.YELLOW + ChatColor.BOLD.toString() + "=====================================");
        getLogger().info(ChatColor.GOLD + ChatColor.BOLD.toString() + " Reloading MCCord Plugin...");
        getLogger().info(ChatColor.YELLOW + ChatColor.BOLD.toString() + "=====================================");

        shutdownBot();
        reloadConfig();
        config = getConfig();

        initializeBotAsync().exceptionally(throwable -> {
            getLogger().warning(ChatColor.RED + "Bot initialization failed during reload. Check config.yml and try again!");
            return null;
        }).thenRun(() -> {
            getLogger().info(ChatColor.YELLOW + ChatColor.BOLD.toString() + "=====================================");
            getLogger().info(ChatColor.GREEN + ChatColor.BOLD.toString() + " MCCord Plugin Reloaded Successfully!");
            getLogger().info(ChatColor.GRAY + " Version: " + ChatColor.WHITE + getDescription().getVersion());
            getLogger().info(ChatColor.YELLOW + ChatColor.BOLD.toString() + "=====================================");
            getLogger().info("");
        });
    }

    public JDA getJDA() {
        return jda;
    }

    public String getChannelId(String command) {
        return config.getString("discord.channels." + command);
    }

    public FileConfiguration getPluginConfig() {
        return config;
    }

    public ServerStatus getServerStatus() {
        return serverStatus;
    }

    private static class MCCordCommand implements CommandExecutor, TabCompleter {
        private final MCCord plugin;

        public MCCordCommand(MCCord plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!sender.hasPermission("mccord.admin")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission!");
                return true;
            }

            if (args.length == 0) {
                sender.sendMessage(ChatColor.RED + "Usage: /mccord <reload|maintenance> [true/false]");
                return true;
            }

            switch (args[0].toLowerCase()) {
                case "reload":
                    plugin.reloadPlugin();
                    sender.sendMessage(ChatColor.GREEN + "MCCord plugin reloaded!");
                    break;
                case "maintenance":
                    if (args.length < 2 || (!args[1].equalsIgnoreCase("true") && !args[1].equalsIgnoreCase("false"))) {
                        sender.sendMessage(ChatColor.RED + "Usage: /mccord maintenance <true|false>");
                        return true;
                    }
                    boolean maintenance = Boolean.parseBoolean(args[1]);
                    ServerStatus status = plugin.getServerStatus();
                    if (status != null) {
                        status.setMaintenance(maintenance);
                        sender.sendMessage(ChatColor.GREEN + "Maintenance mode set to " + maintenance);
                    } else {
                        sender.sendMessage(ChatColor.RED + "Server status is not enabled or initialized!");
                    }
                    break;
                case "announce":
                    if (args.length < 2) {
                        sender.sendMessage(ChatColor.RED + "Usage: /mccord announce <message>");
                        return true;
                    }
                    new AnnounceCommand(plugin).onCommand(sender, command, label, Arrays.copyOfRange(args, 1, args.length));
                    break;
                case "alert":
                    if (args.length < 3) {
                        sender.sendMessage(ChatColor.RED + "Usage: /mccord alert <player> <message>");
                        return true;
                    }
                    new AlertCommand(plugin).onCommand(sender, command, label, Arrays.copyOfRange(args, 1, args.length));
                    break;
                case "report":
                    if (args.length < 2) {
                        sender.sendMessage(ChatColor.RED + "Usage: /mccord report <reason>");
                        return true;
                    }
                    new ReportCommand(plugin).onCommand(sender, command, label, Arrays.copyOfRange(args, 1, args.length));
                    break;
                default:
                    sender.sendMessage(ChatColor.RED + "Usage: /mccord <reload|maintenance> [true/false]");
            }
            return true;
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            List<String> completions = new ArrayList<>();

            if (!sender.hasPermission("mccord.admin")) {
                return completions;
            }

            if (args.length == 1) {
                completions.addAll(Arrays.asList("reload", "maintenance", "announce", "alert", "report"));
            } else if (args.length == 2) {
                switch (args[0].toLowerCase()) {
                    case "maintenance":
                        completions.addAll(Arrays.asList("true", "false"));
                        break;
                    case "alert":
                        completions.addAll(Bukkit.getOnlinePlayers().stream()
                                .map(Player::getName)
                                .toList());
                        break;
                    case "announce":
                    case "report":
                        break;
                }
            }

            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                    .sorted()
                    .toList();
        }
    }
}