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

public class ReportCommand implements CommandExecutor {
    private final MCCord plugin;

    public ReportCommand(MCCord plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command is for players only!");
            return true;
        }

        if (!sender.hasPermission("mccord.report")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission!");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /report <reason>");
            return true;
        }

        Player player = (Player) sender;
        String reason = String.join(" ", args);
        String channelId = plugin.getChannelId("report");

        if (channelId == null || channelId.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Report channel ID is not configured in config.yml!");
            return true;
        }

        TextChannel channel = plugin.getJDA() != null ? plugin.getJDA().getTextChannelById(channelId) : null;
        if (channel == null) {
            sender.sendMessage(ChatColor.RED + "Invalid report channel ID or bot lacks access!");
            return true;
        }

        FileConfiguration config = plugin.getPluginConfig();
        String title = config.getString("embed.report.title", "Player Report");
        String description = config.getString("embed.report.description", "A player has submitted a report")
                .replace("{nl}", "\n");
        String colorHex = config.getString("embed.report.color", "#FFFF00");
        String fieldReportedByName = config.getString("embed.report.field-reported-by.name", "Reported by");
        boolean fieldReportedByInline = config.getBoolean("embed.report.field-reported-by.inline", true);
        String fieldReasonName = config.getString("embed.report.field-reason.name", "Reason");
        boolean fieldReasonInline = config.getBoolean("embed.report.field-reason.inline", false);
        boolean timestamp = config.getBoolean("embed.report.timestamp", true);

        MessageCreateAction messageAction = channel.sendMessageEmbeds(
                new net.dv8tion.jda.api.EmbedBuilder()
                        .setTitle(title)
                        .setDescription(description)
                        .setColor(Integer.parseInt(colorHex.substring(1), 16))
                        .addField(fieldReportedByName, player.getName(), fieldReportedByInline)
                        .addField(fieldReasonName, reason, fieldReasonInline)
                        .setTimestamp(timestamp ? java.time.Instant.now() : null)
                        .build()
        );

        messageAction.queue(
                success -> sender.sendMessage(ChatColor.GREEN + "Your report has been sent to staff!"),
                error -> sender.sendMessage(ChatColor.RED + "Failed to send report to Discord: " + error.getMessage())
        );

        String staffMessage = config.getString("report.staff-message", "&c[Report] {nl}{nl}&e{player} &7reported: &f{reason}")
                .replace("{player}", player.getName())
                .replace("{reason}", reason);
        String[] staffMessageLines = staffMessage.split("\\{nl\\}");

        boolean notifyStaff = config.getBoolean("report.notify-staff.enable", true);
        String staffTitle = config.getString("report.notify-staff.title", "&cNew Report!")
                .replace("{player}", player.getName())
                .replace("{reason}", reason);
        String staffSubtitle = config.getString("report.notify-staff.subtitle", "&e{player} &7reported: &f{reason}")
                .replace("{player}", player.getName())
                .replace("{reason}", reason)
                .replace("{nl}", " ");
        int titleFadeIn = config.getInt("report.notify-staff.title-fade-in", 10);
        int titleStay = config.getInt("report.notify-staff.title-stay", 70);
        int titleFadeOut = config.getInt("report.notify-staff.title-fade-out", 20);
        String soundName = config.getString("report.notify-staff.sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
        float soundVolume = (float) config.getDouble("report.notify-staff.sound-volume", 1.0);
        float soundPitch = (float) config.getDouble("report.notify-staff.sound-pitch", 1.0);

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.hasPermission("mccord.staff") || onlinePlayer.isOp()) {
                for (String line : staffMessageLines) {
                    if (line.trim().isEmpty()) {
                        onlinePlayer.sendMessage(" ");
                    } else {
                        String formattedLine = ChatColor.translateAlternateColorCodes('&', line);
                        onlinePlayer.sendMessage(formattedLine);
                    }
                }

                if (notifyStaff) {
                    onlinePlayer.sendTitle(
                            ChatColor.translateAlternateColorCodes('&', staffTitle),
                            ChatColor.translateAlternateColorCodes('&', staffSubtitle),
                            titleFadeIn,
                            titleStay,
                            titleFadeOut
                    );
                    try {
                        Sound sound = Sound.valueOf(soundName.toUpperCase());
                        onlinePlayer.playSound(onlinePlayer.getLocation(), sound, soundVolume, soundPitch);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid sound name in config: " + soundName);
                    }
                }
            }
        }

        return true;
    }
}