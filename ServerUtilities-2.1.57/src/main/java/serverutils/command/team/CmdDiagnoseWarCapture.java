package serverutils.command.team;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import serverutils.ServerUtilities;
import serverutils.ServerUtilitiesConfig;
import serverutils.data.ClaimedChunks;
import serverutils.lib.command.CmdBase;
import serverutils.lib.math.ChunkDimPos;

/**
 * Admin command to diagnose war capture system configuration and chunk claiming status.
 * Usage: /team diagnosewarcapture
 */
public class CmdDiagnoseWarCapture extends CmdBase {

    public CmdDiagnoseWarCapture() {
        super("diagnosewarcapture", Level.ALL);
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        ChunkDimPos curChunk = new ChunkDimPos(player);
        int curDim = curChunk.dim;

        // Check war capture config
        boolean warCaptureEnabled = ServerUtilitiesConfig.teams.war_capture_enabled;
        sender.addChatMessage(ServerUtilities.lang(sender, "§6=== War Capture Diagnostics ==="));
        sender.addChatMessage(ServerUtilities.lang(sender, "war_capture_enabled: " + warCaptureEnabled));

        // Check chunk claiming config
        boolean chunkClaimingEnabled = ServerUtilitiesConfig.world.chunk_claiming;
        sender.addChatMessage(ServerUtilities.lang(sender, "chunk_claiming: " + chunkClaimingEnabled));

        // Check if ClaimedChunks is active
        boolean isActive = ClaimedChunks.isActive();
        sender.addChatMessage(ServerUtilities.lang(sender, "ClaimedChunks.isActive(): " + isActive));

        // Check blocked dimensions
        int[] blockedDims = ServerUtilitiesConfig.world.blocked_claiming_dimensions;
        sender.addChatMessage(ServerUtilities.lang(sender, "blocked_claiming_dimensions count: " + blockedDims.length));
        for (int dim : blockedDims) {
            sender.addChatMessage(ServerUtilities.lang(sender, "  - dimension: " + dim));
        }

        // Check current player dimension
        sender.addChatMessage(ServerUtilities.lang(sender, "Your dimension: " + curDim));

        // Check if current dimension is blocked
        boolean isDimensionBlocked = ServerUtilitiesConfig.world.blockDimension(curDim);
        sender.addChatMessage(ServerUtilities.lang(sender, "Is your dimension blocked: " + isDimensionBlocked));

        // Explain the issue
        sender.addChatMessage(ServerUtilities.lang(sender, "§6=== Analysis ==="));
        if (!warCaptureEnabled) {
            sender.addChatMessage(ServerUtilities.lang(sender, "§c[ISSUE] war_capture_enabled is FALSE - capture disabled"));
        } else if (!chunkClaimingEnabled) {
            sender.addChatMessage(ServerUtilities.lang(sender, "§c[ISSUE] chunk_claiming is FALSE - all chunk lookups return null"));
        } else if (!isActive) {
            sender.addChatMessage(ServerUtilities.lang(sender, "§c[ISSUE] ClaimedChunks.isActive() is FALSE"));
        } else if (isDimensionBlocked) {
            sender.addChatMessage(ServerUtilities.lang(sender, "§c[ISSUE] Your dimension is in blocked_claiming_dimensions - chunk lookups disabled"));
        } else {
            sender.addChatMessage(ServerUtilities.lang(sender, "§a[OK] War capture config looks good!"));
        }
    }
}
