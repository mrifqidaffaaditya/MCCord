package com.keidev.mccord;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import com.sun.management.OperatingSystemMXBean;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.regex.*;

public class ServerStatus {
    private final MCCord plugin;
    private String statusMessageId;
    private final File messageFile;
    private final File maintenanceFile;
    private volatile BukkitRunnable statusTask;
    private volatile boolean isMaintenance;
    private long lastUpdateTime; // Waktu terakhir pembaruan
    private int updateIntervalSeconds; // Interval pembaruan dalam detik

    public ServerStatus(MCCord plugin) {
        this.plugin = plugin;
        this.messageFile = new File(plugin.getDataFolder(), "statusMessageId.txt");
        this.maintenanceFile = new File(plugin.getDataFolder(), "maintenance.txt");
        this.statusMessageId = loadMessageId();
        this.isMaintenance = loadMaintenanceStatus();
        this.lastUpdateTime = Instant.now().getEpochSecond(); // Inisialisasi dengan waktu saat ini
        this.updateIntervalSeconds = plugin.getPluginConfig().getInt("discord.status-update-interval", 60);
    }

    public void start() {
        String channelId = plugin.getChannelId("status");
        if (channelId == null || channelId.isEmpty()) {
            plugin.getLogger().warning("Status channel ID is not configured in config.yml!");
            return;
        }

        TextChannel channel = plugin.getJDA() != null ? plugin.getJDA().getTextChannelById(channelId) : null;
        if (channel == null) {
            plugin.getLogger().warning("Invalid status channel ID or bot not initialized!");
            return;
        }

        updateIntervalSeconds = plugin.getPluginConfig().getInt("discord.status-update-interval", 60);
        int intervalTicks = Math.max(updateIntervalSeconds * 20, 600); // Minimum 30 detik (600 ticks)

        // Update pertama kali secara langsung
        updateStatus(channel);

        statusTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (plugin.getJDA() == null || plugin.getJDA().getStatus() != JDA.Status.CONNECTED) {
                    return; // Jangan jalankan jika JDA tidak aktif
                }
                lastUpdateTime = Instant.now().getEpochSecond(); // Simpan waktu terakhir pembaruan
                updateStatus(channel);
            }
        };
        statusTask.runTaskTimerAsynchronously(plugin, 0L, intervalTicks);
        plugin.getLogger().info("Server status updates started with interval: " + (intervalTicks / 20) + " seconds.");
    }

    public void stop() {
        if (statusTask != null && !statusTask.isCancelled()) {
            statusTask.cancel();
            statusTask = null;
            String channelId = plugin.getChannelId("status");
            if (channelId == null || channelId.isEmpty()) {
                plugin.getLogger().warning("Status channel ID is not configured in config.yml!");
                return;
            }

            TextChannel channel = plugin.getJDA() != null ? plugin.getJDA().getTextChannelById(channelId) : null;
            if (channel != null && plugin.getJDA() != null && plugin.getJDA().getStatus() == JDA.Status.CONNECTED) {
                EmbedBuilder embed = createOfflineEmbed();
                sendEmbed(channel, embed);
            }
            plugin.getLogger().info("Server status update stopped.");
        }
    }

    private void updateStatus(TextChannel channel) {
        EmbedBuilder embed = isMaintenance ? createMaintenanceEmbed() : createOnlineEmbed();
        sendEmbed(channel, embed);
    }

    private EmbedBuilder createOnlineEmbed() {
        OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

        String playerList = String.join(", ", Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        if (playerList.isEmpty()) playerList = "No players online";
        double[] tps = Bukkit.getServer().getTPS();
        String version = Bukkit.getServer().getVersion();
        Pattern pattern = Pattern.compile("(\\d+\\.\\d+\\.\\d+)");
        Matcher matcher = pattern.matcher(version);
        String versionPoint = matcher.find() ? matcher.group(1) : "Unknown";
        double cpuUsage = osBean.getCpuLoad() * 100;
        long totalMemory = osBean.getTotalMemorySize() / (1024 * 1024);
        long freeMemory = osBean.getFreeMemorySize() / (1024 * 1024);
        long usedMemory = totalMemory - freeMemory;

        EmbedBuilder embed = new EmbedBuilder();
        if (plugin.getPluginConfig().getBoolean("embed.server-status.online.title.enable", true)) {
            String title = plugin.getPluginConfig().getString("embed.server-status.online.title.text");
            String url = plugin.getPluginConfig().getString("embed.server-status.online.title.url", null);
            url = url != null && url.isEmpty() ? null : url;
            if (title != null) embed.setTitle(title, url);
        }

        embed.setColor(Integer.parseInt(plugin.getPluginConfig().getString("embed.server-status.online.color", "#00ff00").substring(1), 16));
        if (plugin.getPluginConfig().getBoolean("embed.server-status.online.description.enable", true)) {
            embed.setDescription(plugin.getPluginConfig().getString("embed.server-status.online.description.text"));
        }
        if (plugin.getPluginConfig().getBoolean("embed.server-status.online.playerlist.enable", false)) {
            embed.addField("Players Online (" + Bukkit.getOnlinePlayers().size() + "/" + Bukkit.getMaxPlayers() + ")", playerList, plugin.getPluginConfig().getBoolean("embed.server-status.online.playerlist.inline"));
        }
        if (plugin.getPluginConfig().getBoolean("embed.server-status.online.java.enable", false)) {
            embed.addField("Java", plugin.getPluginConfig().getString("embed.server-status.online.java.ip", "Unknown"), plugin.getPluginConfig().getBoolean("embed.server-status.online.java.inline"));
        }
        if (plugin.getPluginConfig().getBoolean("embed.server-status.online.bedrock.enable", true)) {
            embed.addField("Bedrock", plugin.getPluginConfig().getString("embed.server-status.online.bedrock.ip", "Unknown"), plugin.getPluginConfig().getBoolean("embed.server-status.online.bedrock.inline"));
        }
        if (plugin.getPluginConfig().getBoolean("embed.server-status.online.tps.enable", true)) {
            embed.addField("TPS (1m)", String.format("%.2f", tps[0]), plugin.getPluginConfig().getBoolean("embed.server-status.online.tps.inline"));
        }
        if (plugin.getPluginConfig().getBoolean("embed.server-status.online.version.enable", false)) {
            embed.addField("Version", versionPoint, plugin.getPluginConfig().getBoolean("embed.server-status.online.version.inline"));
        }
        if (plugin.getPluginConfig().getBoolean("embed.server-status.online.cpu.enable", true)) {
            embed.addField("CPU Usage", String.format("%.2f%%", cpuUsage), plugin.getPluginConfig().getBoolean("embed.server-status.online.cpu.inline"));
        }
        if (plugin.getPluginConfig().getBoolean("embed.server-status.online.ram.enable", true)) {
            embed.addField("RAM Usage", usedMemory + " MB / " + totalMemory + " MB", plugin.getPluginConfig().getBoolean("embed.server-status.online.ram.inline"));
        }

        // Field untuk waktu refresh berikutnya
        long nextUpdateTime = lastUpdateTime + updateIntervalSeconds;
        embed.addField("Next Refresh", "<t:" + nextUpdateTime + ":R>", true);

        if (plugin.getPluginConfig().getBoolean("embed.server-status.online.timestamp", true)) {
            embed.setTimestamp(Instant.now());
        }
        if (plugin.getPluginConfig().getBoolean("embed.server-status.online.author.enable", false)) {
            String authorText = plugin.getPluginConfig().getString("embed.server-status.online.author.text");
            String authorIcon = plugin.getPluginConfig().getString("embed.server-status.online.author.icon", null);
            authorIcon = authorIcon != null && authorIcon.isEmpty() ? null : authorIcon;
            embed.setAuthor(authorText, null, authorIcon);
        }
        if (plugin.getPluginConfig().getBoolean("embed.server-status.online.footer.enable", false)) {
            String footerText = plugin.getPluginConfig().getString("embed.server-status.online.footer.text");
            String footerIcon = plugin.getPluginConfig().getString("embed.server-status.online.footer.icon", null);
            footerIcon = footerIcon != null && footerIcon.isEmpty() ? null : footerIcon;
            embed.setFooter(footerText, footerIcon);
        }
        if (plugin.getPluginConfig().getBoolean("embed.server-status.online.image.enable", false)) {
            embed.setImage(plugin.getPluginConfig().getString("embed.server-status.online.image.url"));
        }
        if (plugin.getPluginConfig().getBoolean("embed.server-status.online.thumbnail.enable", false)) {
            embed.setThumbnail(plugin.getPluginConfig().getString("embed.server-status.online.thumbnail.url"));
        }
        return embed;
    }

    private EmbedBuilder createMaintenanceEmbed() {
        EmbedBuilder embed = new EmbedBuilder();
        if (plugin.getPluginConfig().getBoolean("embed.server-status.maintenance.title.enable", true)) {
            String title = plugin.getPluginConfig().getString("embed.server-status.maintenance.title.text");
            String url = plugin.getPluginConfig().getString("embed.server-status.maintenance.title.url", null);
            url = url != null && url.isEmpty() ? null : url;
            if (title != null) embed.setTitle(title, url);
        }

        embed.setColor(Integer.parseInt(plugin.getPluginConfig().getString("embed.server-status.maintenance.color", "#FFA500").substring(1), 16));
        if (plugin.getPluginConfig().getBoolean("embed.server-status.maintenance.description.enable", true)) {
            embed.setDescription(plugin.getPluginConfig().getString("embed.server-status.maintenance.description.text"));
        }

        // Field untuk waktu refresh berikutnya
        long nextUpdateTime = lastUpdateTime + updateIntervalSeconds;
        embed.addField("Next Refresh", "<t:" + nextUpdateTime + ":R>", true);

        if (plugin.getPluginConfig().getBoolean("embed.server-status.maintenance.timestamp", true)) {
            embed.setTimestamp(Instant.now());
        }
        if (plugin.getPluginConfig().getBoolean("embed.server-status.maintenance.author.enable", false)) {
            String authorText = plugin.getPluginConfig().getString("embed.server-status.maintenance.author.text");
            String authorIcon = plugin.getPluginConfig().getString("embed.server-status.maintenance.author.icon", null);
            authorIcon = authorIcon != null && authorIcon.isEmpty() ? null : authorIcon;
            embed.setAuthor(authorText, null, authorIcon);
        }
        if (plugin.getPluginConfig().getBoolean("embed.server-status.maintenance.footer.enable", false)) {
            String footerText = plugin.getPluginConfig().getString("embed.server-status.maintenance.footer.text");
            String footerIcon = plugin.getPluginConfig().getString("embed.server-status.maintenance.footer.icon", null);
            footerIcon = footerIcon != null && footerIcon.isEmpty() ? null : footerIcon;
            embed.setFooter(footerText, footerIcon);
        }
        if (plugin.getPluginConfig().getBoolean("embed.server-status.maintenance.image.enable", false)) {
            embed.setImage(plugin.getPluginConfig().getString("embed.server-status.maintenance.image.url"));
        }
        if (plugin.getPluginConfig().getBoolean("embed.server-status.maintenance.thumbnail.enable", false)) {
            embed.setThumbnail(plugin.getPluginConfig().getString("embed.server-status.maintenance.thumbnail.url"));
        }
        return embed;
    }

    private EmbedBuilder createOfflineEmbed() {
        EmbedBuilder embed = new EmbedBuilder();
        if (plugin.getPluginConfig().getBoolean("embed.server-status.offline.title.enable", true)) {
            String title = plugin.getPluginConfig().getString("embed.server-status.offline.title.text");
            String url = plugin.getPluginConfig().getString("embed.server-status.offline.title.url", null);
            url = url != null && url.isEmpty() ? null : url;
            if (title != null) embed.setTitle(title, url);
        }

        embed.setColor(Integer.parseInt(plugin.getPluginConfig().getString("embed.server-status.offline.color", "#FF0000").substring(1), 16));
        if (plugin.getPluginConfig().getBoolean("embed.server-status.offline.description.enable", true)) {
            embed.setDescription(plugin.getPluginConfig().getString("embed.server-status.offline.description.text"));
        }
        if (plugin.getPluginConfig().getBoolean("embed.server-status.offline.timestamp", true)) {
            embed.setTimestamp(Instant.now());
        }
        if (plugin.getPluginConfig().getBoolean("embed.server-status.offline.author.enable", false)) {
            String authorText = plugin.getPluginConfig().getString("embed.server-status.offline.author.text");
            String authorIcon = plugin.getPluginConfig().getString("embed.server-status.offline.author.icon", null);
            authorIcon = authorIcon != null && authorIcon.isEmpty() ? null : authorIcon;
            embed.setAuthor(authorText, null, authorIcon);
        }
        if (plugin.getPluginConfig().getBoolean("embed.server-status.offline.footer.enable", false)) {
            String footerText = plugin.getPluginConfig().getString("embed.server-status.offline.footer.text");
            String footerIcon = plugin.getPluginConfig().getString("embed.server-status.offline.footer.icon", null);
            footerIcon = footerIcon != null && footerIcon.isEmpty() ? null : footerIcon;
            embed.setFooter(footerText, footerIcon);
        }
        if (plugin.getPluginConfig().getBoolean("embed.server-status.offline.image.enable", false)) {
            embed.setImage(plugin.getPluginConfig().getString("embed.server-status.offline.image.url"));
        }
        if (plugin.getPluginConfig().getBoolean("embed.server-status.offline.thumbnail.enable", false)) {
            embed.setThumbnail(plugin.getPluginConfig().getString("embed.server-status.offline.thumbnail.url"));
        }
        return embed;
    }

    private void sendEmbed(TextChannel channel, EmbedBuilder embed) {
        if (channel == null || plugin.getJDA() == null || plugin.getJDA().getStatus() != JDA.Status.CONNECTED) {
            plugin.getLogger().warning("Cannot send embed: Channel or JDA is null or not connected!");
            return;
        }

        if (statusMessageId == null) {
            channel.sendMessageEmbeds(embed.build()).queue(
                    message -> {
                        statusMessageId = message.getId();
                        saveMessageId(statusMessageId);
                    },
                    failure -> plugin.getLogger().warning("Failed to send initial status embed: " + failure.getMessage())
            );
        } else {
            channel.editMessageEmbedsById(statusMessageId, embed.build()).queue(
                    success -> {},
                    failure -> channel.sendMessageEmbeds(embed.build()).queue(
                            message -> {
                                statusMessageId = message.getId();
                                saveMessageId(statusMessageId);
                            },
                            error -> plugin.getLogger().warning("Failed to update status embed: " + error.getMessage())
                    )
            );
        }
    }

    private String loadMessageId() {
        if (!messageFile.exists()) return null;
        try {
            return Files.readString(Path.of(messageFile.toURI())).trim();
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load status message ID: " + e.getMessage());
            return null;
        }
    }

    private void saveMessageId(String messageId) {
        try {
            Files.writeString(Path.of(messageFile.toURI()), messageId);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save status message ID: " + e.getMessage());
        }
    }

    private boolean loadMaintenanceStatus() {
        if (!maintenanceFile.exists()) return false;
        try {
            String status = Files.readString(Path.of(maintenanceFile.toURI())).trim();
            return Boolean.parseBoolean(status);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load maintenance status: " + e.getMessage());
            return false;
        }
    }

    private void saveMaintenanceStatus(boolean status) {
        try {
            Files.writeString(Path.of(maintenanceFile.toURI()), String.valueOf(status));
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save maintenance status: " + e.getMessage());
        }
    }

    public void setMaintenance(boolean maintenance) {
        this.isMaintenance = maintenance;
        saveMaintenanceStatus(maintenance);
        String channelId = plugin.getChannelId("status");
        if (channelId != null && !channelId.isEmpty() && plugin.getJDA() != null && plugin.getJDA().getStatus() == JDA.Status.CONNECTED) {
            TextChannel channel = plugin.getJDA().getTextChannelById(channelId);
            if (channel != null) {
                updateStatus(channel);
            }
        }
    }

    public boolean isMaintenance() {
        return isMaintenance;
    }
}