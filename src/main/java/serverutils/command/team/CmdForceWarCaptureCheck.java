package serverutils.command.team;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import serverutils.ServerUtilities;
import serverutils.data.WarCaptureManager;
import serverutils.lib.command.CmdBase;

/**
 * Admin command to manually trigger a war capture check immediately.
 * Usage: /team forcecapturecheck
 */
public class CmdForceWarCaptureCheck extends CmdBase {

    public CmdForceWarCaptureCheck() {
        super("forcecapturecheck", Level.ALL);
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        getCommandSenderAsPlayer(sender);
        
        long now = System.currentTimeMillis();
        sender.addChatMessage(ServerUtilities.lang(sender, "§6[WarCapture] Forcing capture check now..."));
        
        try {
            WarCaptureManager.get().onCheck(now);
            sender.addChatMessage(ServerUtilities.lang(sender, "§a[WarCapture] Capture check executed!"));
        } catch (Exception e) {
            sender.addChatMessage(ServerUtilities.lang(sender, "§c[WarCapture] Error during check: " + e.getMessage()));
            e.printStackTrace();
        }
    }
}
