package serverutils.command.team;

import java.util.Map;
import java.util.UUID;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import serverutils.ServerUtilities;
import serverutils.data.WarCaptureManager;
import serverutils.lib.command.CmdBase;

public class CmdListFronts extends CmdBase {

    public CmdListFronts() {
        super("listfronts", Level.ALL);
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        Map<UUID, WarCaptureManager.CaptureInfo> captures = WarCaptureManager.get().getActiveCaptures();
        
        if (captures.isEmpty()) {
            sender.addChatMessage(ServerUtilities.lang(sender, "No active capture fronts."));
            return;
        }

        sender.addChatMessage(ServerUtilities.lang(sender, "Active Capture Fronts:"));
        for (WarCaptureManager.CaptureInfo info : captures.values()) {
            long timeRemaining = info.endTime - System.currentTimeMillis();
            long secondsRemaining = Math.max(0, timeRemaining / 1000L);
            
            String capturerName = serverutils.lib.data.Universe.get().getPlayer(info.playerId) != null ?
                    serverutils.lib.data.Universe.get().getPlayer(info.playerId).getName() : "Unknown";
            
            String coords = String.format("%.1f, %.1f, %.1f", info.startX, info.startY, info.startZ);
            String msg = String.format("  %s from %s @ (%s) - %d seconds remaining",
                    capturerName, info.enemyTeam.getId(), coords, secondsRemaining);
            sender.addChatMessage(new ChatComponentText(msg));
        }
    }
}
