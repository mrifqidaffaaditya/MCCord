package com.keidev.mccord;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class AnnounceCommand implements CommandExecutor {
    private final MCCord plugin;

    public AnnounceCommand(MCCord plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mccord.announce")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission!");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /announce <message>");
            return true;
        }

        String message = String.join(" ", args);
        String senderName = sender instanceof Player ? sender.getName() : "Console";
        String channelId = plugin.getChannelId("announce");

        if (channelId == null || channelId.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Announce channel ID is not configured in config.yml!");
            return true;
        }

        TextChannel channel = plugin.getJDA() != null ? plugin.getJDA().getTextChannelById(channelId) : null;
        if (channel == null) {
            sender.sendMessage(ChatColor.RED + "Invalid announce channel ID or bot lacks access!");
            return true;
        }

        FileConfiguration config = plugin.getPluginConfig();
        String title = config.getString("embed.announce.title", "Server Announcement");
        String description = config.getString("embed.announce.description", "{message}")
                .replace("{message}", message)
                .replace("{nl}", "\n");
        String colorHex = config.getString("embed.announce.color", "#00FF00");
        String fieldName = config.getString("embed.announce.field-announced-by.name", "Announced by");
        boolean fieldInline = config.getBoolean("embed.announce.field-announced-by.inline", true);
        boolean timestamp = config.getBoolean("embed.announce.timestamp", true);

        MessageCreateAction messageAction = channel.sendMessageEmbeds(
                new net.dv8tion.jda.api.EmbedBuilder()
                        .setTitle(title)
                        .setDescription(description)
                        .setColor(Integer.parseInt(colorHex.substring(1), 16))
                        .addField(fieldName, senderName, fieldInline)
                        .setTimestamp(timestamp ? java.time.Instant.now() : null)
                        .build()
        );

        messageAction.queue(
                success -> sender.sendMessage(ChatColor.GREEN + "Announcement sent!"),
                error -> sender.sendMessage(ChatColor.RED + "Failed to send announcement: " + error.getMessage())
        );

        String inGameMessage = config.getString("announce.message", "&6[Announcement] &e{message} &7- {sender}")
                .replace("{message}", message)
                .replace("{sender}", senderName);
        String[] messageLines = inGameMessage.split("\\{nl\\}");
        for (int i = 0; i < messageLines.length; i++) {
            String line = messageLines[i];
            if (line.trim().isEmpty()) {
                Bukkit.broadcastMessage(" ");
            } else {
                String formattedLine = ChatColor.translateAlternateColorCodes('&', line);
                Bukkit.broadcastMessage(formattedLine);
            }
        }

        boolean notifyPlayers = config.getBoolean("announce.notify-players.enable", true);
        String titleText = config.getString("announce.title-mc", "&6Announcement")
                .replace("{message}", message)
                .replace("{sender}", senderName);
        String subtitleText = config.getString("announce.subtitle", "&e{message}{nl}&7By {sender}")
                .replace("{message}", message)
                .replace("{sender}", senderName)
                .replace("{nl}", " ");
        int fadeIn = config.getInt("announce.title-fade-in", 10);
        int stay = config.getInt("announce.title-stay", 70);
        int fadeOut = config.getInt("announce.title-fade-out", 20);
        String soundName = config.getString("announce.notify-players.sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
        float soundVolume = (float) config.getDouble("announce.notify-players.sound-volume", 1.0);
        float soundPitch = (float) config.getDouble("announce.notify-players.sound-pitch", 1.0);

        plugin.getLogger().info("Subtitle text: " + subtitleText);

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (notifyPlayers) {
                onlinePlayer.sendTitle(
                        ChatColor.translateAlternateColorCodes('&', titleText),
                        ChatColor.translateAlternateColorCodes('&', subtitleText),
                        fadeIn, stay, fadeOut
                );
                try {
                    Sound sound = Sound.valueOf(soundName.toUpperCase());
                    onlinePlayer.playSound(onlinePlayer.getLocation(), sound, soundVolume, soundPitch);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid sound name in config for announce: " + soundName);
                }
            }
        }

        return true;
    }
}