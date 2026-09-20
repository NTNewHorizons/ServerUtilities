package serverutils.lib.util;

import java.text.DecimalFormat;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;

import serverutils.ServerUtilitiesConfig;

public class MOTDFormatter {

    private static final DecimalFormat TPS_FORMAT = new DecimalFormat("0.0");
    private static final DecimalFormat MEMORY_FORMAT = new DecimalFormat("0");

    private static long serverStartTime = 0L;
    private static long clientStartTime = 0L;

    public static IChatComponent buildMOTD(MinecraftServer server) {
        if (serverStartTime == 0L) {
            serverStartTime = System.currentTimeMillis();
        }

        if (!ServerUtilitiesConfig.motd.enabled) {
            return new ChatComponentText(server.getMOTD());
        }

        String line1 = format(ServerUtilitiesConfig.motd.line1, server);
        String line2 = format(ServerUtilitiesConfig.motd.line2, server);

        String combinedText = line1 + "\n" + line2;

        return new ChatComponentText(combinedText);
    }

    // Returns true if the text contains any variable placeholders.
    public static boolean containsVariables(String text) {
        return text != null && (text.contains("{players}") || text.contains("{maxPlayers}")
                || text.contains("{tps}")
                || text.contains("{memory}")
                || text.contains("{uptime}"));
    }

    // Client-side fallback for TAB when no server data was received yet. Leaves {tps} and {maxPlayers} untouched.
    public static String formatClient(String text, int playerCount) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        if (clientStartTime == 0L) {
            clientStartTime = System.currentTimeMillis();
        }

        String result = text.replace("{players}", String.valueOf(playerCount));

        if (result.contains("{memory}")) {
            result = result.replace("{memory}", formatMemory());
        }

        if (result.contains("{uptime}")) {
            result = result.replace("{uptime}", formatUptime(System.currentTimeMillis() - clientStartTime));
        }

        return result;
    }

    // Replaces {players}, {maxPlayers}, {tps}, {memory}, {uptime} with live server data.
    public static String format(String text, MinecraftServer server) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        String result = text;

        result = result.replace("{players}", String.valueOf(server.getCurrentPlayerCount()));

        result = result.replace("{maxPlayers}", String.valueOf(server.getMaxPlayers()));

        result = result.replace("{tps}", calculateTPS(server));

        if (result.contains("{memory}")) {
            result = result.replace("{memory}", formatMemory());
        }

        if (result.contains("{uptime}")) {
            long uptimeMillis = System.currentTimeMillis() - serverStartTime;
            result = result.replace("{uptime}", formatUptime(uptimeMillis));
        }

        return result;
    }

    private static String formatMemory() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / 1024 / 1024; // Convert to MB
        long totalMemory = runtime.totalMemory() / 1024 / 1024;
        long freeMemory = runtime.freeMemory() / 1024 / 1024;
        long usedMemory = totalMemory - freeMemory;

        return MEMORY_FORMAT.format(usedMemory) + "/" + MEMORY_FORMAT.format(maxMemory) + "MB";
    }

    private static String calculateTPS(MinecraftServer server) {
        try {
            // Calculate average tick time from tickTimeArray (public field)
            long[] tickTimes = server.tickTimeArray;

            // Calculate average tick time in nanoseconds
            double avgTickTimeNanos = MathHelper.average(tickTimes);

            // Convert to milliseconds
            double avgTickTimeMs = avgTickTimeNanos * 1.0E-6D;

            // Calculate TPS (1000ms per second / ms per tick)
            // Cap at 20.0 TPS maximum
            double tps = Math.min(20.0, 1000.0 / avgTickTimeMs);

            return TPS_FORMAT.format(tps);
        } catch (Exception e) {
            // Fallback if calculation fails
            return "N/A";
        }
    }

    private static String formatUptime(long uptimeMillis) {
        long seconds = uptimeMillis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return days + "d " + (hours % 24) + "h";
        } else if (hours > 0) {
            return hours + "h " + (minutes % 60) + "m";
        } else if (minutes > 0) {
            return minutes + "m";
        } else {
            return seconds + "s";
        }
    }
}
