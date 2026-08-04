package serverutils.command.team;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import serverutils.ServerUtilities;
import serverutils.data.WarManager;
import serverutils.lib.command.CmdBase;
import java.util.Map;

public class CmdListWars extends CmdBase {
    public CmdListWars() {
        super("listwars", Level.ALL);
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        Map<String, Map<String, WarManager.WarInfo>> wars = WarManager.get().getAllWars();
        if (wars.isEmpty()) {
            sender.addChatMessage(ServerUtilities.lang(sender, "There are no active wars."));
            return;
        }
        sender.addChatMessage(ServerUtilities.lang(sender, "Active wars:"));
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Map<String, WarManager.WarInfo>> entry : wars.entrySet()) {
            String teamA = entry.getKey();
            for (Map.Entry<String, WarManager.WarInfo> e : entry.getValue().entrySet()) {
                String teamB = e.getKey();
                WarManager.WarInfo info = e.getValue();
                if (teamA.compareTo(teamB) < 0 && info.warEndTime > now) {
                    long secondsLeft = (info.warEndTime - now) / 1000L;
                    if (now < info.graceEndTime) {
                        long graceLeft = (info.graceEndTime - now) / 1000L;
                        sender.addChatMessage(ServerUtilities.lang(sender, " - " + teamA + " vs " + teamB + " (grace: " + graceLeft + "s, total: " + secondsLeft + "s left)"));
                    } else {
                        sender.addChatMessage(ServerUtilities.lang(sender, " - " + teamA + " vs " + teamB + " (active, " + secondsLeft + "s left)"));
                    }
                }
            }
        }
    }
}
