package serverutils.command.team;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import serverutils.ServerUtilities;
import serverutils.ServerUtilitiesConfig;
import serverutils.data.ClaimedChunk;
import serverutils.data.ClaimedChunks;
import serverutils.data.ServerUtilitiesTeamData;
import serverutils.data.WarManager;
import serverutils.lib.command.CmdBase;
import serverutils.lib.command.CommandUtils;
import serverutils.lib.data.ForgePlayer;
import serverutils.lib.data.ForgeTeam;
import serverutils.lib.math.ChunkDimPos;

/**
 * Admin command to manually check capture-front conditions at your current position.
 * Usage: /team checkfront
 */
public class CmdCheckFront extends CmdBase {

    public CmdCheckFront() {
        super("checkfront", Level.ALL);
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        ForgePlayer fp = CommandUtils.getForgePlayer(player);

        if (fp == null) {
            throw ServerUtilities.error(sender, "Player data not available.");
        }

        ChunkDimPos curChunk = new ChunkDimPos(player);
        ClaimedChunk curClaim = ClaimedChunks.instance.getChunk(curChunk);

        String owner = curClaim == null ? "<unclaimed>" : curClaim.getTeam().getId();
        sender.addChatMessage(ServerUtilities.lang(sender, "Front check at chunk " + curChunk + ": owner=" + owner));

        // Check if war is active
        if (curClaim != null && fp.hasTeam()) {
            ForgeTeam claimOwner = curClaim.getTeam();
            boolean warActive = WarManager.get().isWarActive(fp.team, claimOwner);
            sender.addChatMessage(ServerUtilities.lang(sender, "War active with " + claimOwner.getId() + ": " + warActive));
            if (!warActive) {
                sender.addChatMessage(ServerUtilities.lang(sender, "§c[BLOCKED] War must be active to capture!"));
            }
        }

        int radius = ServerUtilitiesConfig.teams.war_capture_radius_chunks;
        int offset = radius + 1;
        sender.addChatMessage(ServerUtilities.lang(sender, "radius=" + radius + " offset=" + offset));

        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        boolean edgeOk = false;
        for (int i = 0; i < dirs.length; i++) {
            int[] d = dirs[i];
            ChunkDimPos checkPos = new ChunkDimPos(curChunk.posX + d[0]*offset, curChunk.posZ + d[1]*offset, curChunk.dim);
            ClaimedChunk c2 = ClaimedChunks.instance.getChunk(checkPos);
            String nOwner = c2 == null ? "<unclaimed>" : (c2.getTeam() == null ? "<null>" : c2.getTeam().getId());
            sender.addChatMessage(ServerUtilities.lang(sender, "dir=" + i + " pos=" + checkPos + " owner=" + nOwner));

            if (c2 == null) {
                edgeOk = true;
            } else {
                ForgeTeam neighborOwner = c2.getTeam();
                if (neighborOwner != null && neighborOwner.equalsTeam(fp.team)) {
                    edgeOk = true;
                }
            }
        }

        sender.addChatMessage(ServerUtilities.lang(sender, "edgeOk=" + edgeOk));

        ServerUtilitiesTeamData teamData = ServerUtilitiesTeamData.get(fp.team);
        sender.addChatMessage(ServerUtilities.lang(sender, "teamCapturing=" + (teamData == null ? "<noTeamData>" : teamData.capturingInProgress)));
    }
}
