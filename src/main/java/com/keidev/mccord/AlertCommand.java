package com.keidev.mccord;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class AlertCommand implements CommandExecutor {
    private final MCCord plugin;

    public AlertCommand(MCCord plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mccord.alert")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /alert <player> <reason>");
            return true;
        }

        String targetPlayerName = args[0];
        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        String senderName = sender instanceof Player ? sender.getName() : "Console";
        String channelId = plugin.getChannelId("alert");

        // Kirim ke Discord
        if (channelId == null || channelId.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Alert channel ID is not configured in config.yml!");
            return true;
        }

        TextChannel channel = plugin.getJDA() != null ? plugin.getJDA().getTextChannelById(channelId) : null;
        if (channel == null) {
            sender.sendMessage(ChatColor.RED + "Invalid alert channel ID or bot lacks access!");
            return true;
        }

        FileConfiguration config = plugin.getPluginConfig();
        String title = config.getString("embed.alert.title", "Alert");
        String description = config.getString("embed.alert.description", "An alert has been issued for a player")
                .replace("{nl}", "\n");
        String colorHex = config.getString("embed.alert.color", "#FF0000");
        String fieldTargetName = config.getString("embed.alert.field-target-player.name", "Target Player");
        boolean fieldTargetInline = config.getBoolean("embed.alert.field-target-player.inline", true);
        String fieldReasonName = config.getString("embed.alert.field-reason.name", "Reason");
        boolean fieldReasonInline = config.getBoolean("embed.alert.field-reason.inline", false);
        String fieldAlertedByName = config.getString("embed.alert.field-alerted-by.name", "Alerted by");
        boolean fieldAlertedByInline = config.getBoolean("embed.alert.field-alerted-by.inline", true);
        boolean timestamp = config.getBoolean("embed.alert.timestamp", true);

        MessageCreateAction messageAction = channel.sendMessageEmbeds(
                new net.dv8tion.jda.api.EmbedBuilder()
                        .setTitle(title)
                        .setDescription(description)
                        .setColor(Integer.parseInt(colorHex.substring(1), 16))
                        .addField(fieldTargetName, targetPlayerName, fieldTargetInline)
                        .addField(fieldReasonName, reason, fieldReasonInline)
                        .addField(fieldAlertedByName, senderName, fieldAlertedByInline)
                        .setTimestamp(timestamp ? java.time.Instant.now() : null)
                        .build()
        );

        messageAction.queue(
                success -> sender.sendMessage(ChatColor.GREEN + "Alert for " + targetPlayerName + " sent!"),
                error -> sender.sendMessage(ChatColor.RED + "Failed to send alert: " + error.getMessage())
        );

        // Kirim pesan in-game dengan pemisahan baris
        Player targetPlayer = Bukkit.getPlayer(targetPlayerName);
        String alertMessage = config.getString("alert.message", "&c[Alert] {nl}{nl}&e{reason} &7- Issued by: {sender}")
                .replace("{reason}", reason)
                .replace("{sender}", senderName);
        String[] messageLines = alertMessage.split("\\{nl\\}");
        boolean broadcastToAll = config.getBoolean("alert.broadcast-to-all", false);

        for (int i = 0; i < messageLines.length; i++) {
            String line = messageLines[i];
            if (line.trim().isEmpty()) {
                if (broadcastToAll) {
                    Bukkit.broadcastMessage(" ");
                } else if (targetPlayer != null && targetPlayer.isOnline()) {
                    targetPlayer.sendMessage(" ");
                }
            } else {
                String formattedLine = ChatColor.translateAlternateColorCodes('&', line);
                if (broadcastToAll) {
                    Bukkit.broadcastMessage(formattedLine);
                } else if (targetPlayer != null && targetPlayer.isOnline()) {
                    targetPlayer.sendMessage(formattedLine);
                }
            }
        }

        // Notifikasi title, subtitle, dan sound untuk target player dan/atau semua pemain
        boolean notifyTarget = config.getBoolean("alert.notify-target.enable", true);
        String titleText = config.getString("alert.title-mc", "&cAlert")
                .replace("{reason}", reason)
                .replace("{sender}", senderName);
        String subtitleText = config.getString("alert.subtitle", "&e{reason}{nl}&7By {sender}")
                .replace("{reason}", reason)
                .replace("{sender}", senderName)
                .replace("{nl}", " ");
        int fadeIn = config.getInt("alert.title-fade-in", 10);
        int stay = config.getInt("alert.title-stay", 70);
        int fadeOut = config.getInt("alert.title-fade-out", 20);
        String soundName = config.getString("alert.notify-target.sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
        float soundVolume = (float) config.getDouble("alert.notify-target.sound-volume", 1.0);
        float soundPitch = (float) config.getDouble("alert.notify-target.sound-pitch", 1.0);

        if (targetPlayer != null && targetPlayer.isOnline()) {
            if (notifyTarget) {
                targetPlayer.sendTitle(
                        ChatColor.translateAlternateColorCodes('&', titleText),
                        ChatColor.translateAlternateColorCodes('&', subtitleText),
                        fadeIn, stay, fadeOut
                );
                try {
                    Sound sound = Sound.valueOf(soundName.toUpperCase().replace(" ", "_"));
                    targetPlayer.playSound(targetPlayer.getLocation(), sound, soundVolume, soundPitch);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid sound name in config for alert: " + soundName + ". Using default sound.");
                    targetPlayer.playSound(targetPlayer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, soundVolume, soundPitch);
                }
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Player " + targetPlayerName + " is not online!");
        }

        if (broadcastToAll && notifyTarget) {
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (onlinePlayer != targetPlayer) {
                    onlinePlayer.sendTitle(
                            ChatColor.translateAlternateColorCodes('&', titleText),
                            ChatColor.translateAlternateColorCodes('&', subtitleText),
                            fadeIn, stay, fadeOut
                    );
                    try {
                        Sound sound = Sound.valueOf(soundName.toUpperCase().replace(" ", "_"));
                        onlinePlayer.playSound(onlinePlayer.getLocation(), sound, soundVolume, soundPitch);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid sound name in config for alert: " + soundName + ". Using default sound.");
                        onlinePlayer.playSound(onlinePlayer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, soundVolume, soundPitch);
                    }
                }
            }
        }

        return true;
    }
}