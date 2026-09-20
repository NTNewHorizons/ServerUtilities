package serverutils.net;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import serverutils.ServerUtilitiesConfig;
import serverutils.lib.util.MOTDFormatter;

public class TabHeaderFooterSync {

    private static final int SYNC_INTERVAL_TICKS = 40;

    private static int tickCounter = 0;
    private static boolean wasVariable = false;

    public static void onServerTick(MinecraftServer server) {
        String rawHeader = ServerUtilitiesConfig.tab.headerText;
        String rawFooter = ServerUtilitiesConfig.tab.footerText;

        if (!MOTDFormatter.containsVariables(rawHeader) && !MOTDFormatter.containsVariables(rawFooter)) {
            if (wasVariable) {
                // Variables removed, push static text once so clients don't keep stale values.
                wasVariable = false;
                new MessageTabHeaderFooter(rawHeader, rawFooter).sendToAll();
            }
            return;
        }

        if (++tickCounter < SYNC_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;
        wasVariable = true;
        new MessageTabHeaderFooter(
                MOTDFormatter.format(rawHeader, server),
                MOTDFormatter.format(rawFooter, server)).sendToAll();
    }

    public static void sendToPlayer(EntityPlayerMP player) {
        if (player == null || player.mcServer == null) {
            return;
        }

        String rawHeader = ServerUtilitiesConfig.tab.headerText;
        String rawFooter = ServerUtilitiesConfig.tab.footerText;

        if (!MOTDFormatter.containsVariables(rawHeader) && !MOTDFormatter.containsVariables(rawFooter)) {
            return;
        }

        wasVariable = true;
        new MessageTabHeaderFooter(
                MOTDFormatter.format(rawHeader, player.mcServer),
                MOTDFormatter.format(rawFooter, player.mcServer)).sendTo(player);
    }
}
