package serverutils.command.team;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import serverutils.ServerUtilities;
import serverutils.data.ClaimedChunks;
import serverutils.data.ServerUtilitiesTeamData;
import serverutils.data.WarManager;
import serverutils.data.WarProposalManager;
import serverutils.lib.command.CmdBase;
import serverutils.lib.command.CommandUtils;
import serverutils.lib.data.ForgePlayer;
import serverutils.lib.data.ForgeTeam;
import serverutils.lib.data.Universe;

public class CmdSurrender extends CmdBase {

    public CmdSurrender() {
        super("surrender", Level.ALL);
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            throw ServerUtilities.error(sender, "Usage: /team surrender <targetTeam> or /team surrender accept/deny");
        }

        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        ForgePlayer p = CommandUtils.getForgePlayer(player);

        if (!p.hasTeam()) {
            throw ServerUtilities.error(sender, "You must be in a team to surrender.");
        }

        if (args[0].equalsIgnoreCase("accept") || args[0].equalsIgnoreCase("deny")) {
            // This is a response to a surrender proposal
            processResponse(sender, p, args[0].equalsIgnoreCase("accept"));
            return;
        }

        // Propose surrender to target team
        if (!p.team.isOwner(p)) {
            throw ServerUtilities.error(sender, "Only the team leader can propose surrender.");
        }

        String targetTeamId = args[0];
        ForgeTeam targetTeam = Universe.get().getTeam(targetTeamId);
        if (!targetTeam.isValid()) {
            throw ServerUtilities.error(sender, "Target team does not exist.");
        }

        if (!WarManager.get().isAtWar(p.team, targetTeam)) {
            throw ServerUtilities.error(sender, "Your team is not at war with that team.");
        }

        // Check if proposal already exists
        if (!WarProposalManager.get().canAddProposal(p.team, targetTeam)) {
            throw ServerUtilities.error(sender, "A proposal is already pending with this team. Wait for it to expire.");
        }

        // Send proposal to target team leader
        WarProposalManager.get().addProposal(WarProposalManager.ProposalType.SURRENDER, p.team, targetTeam, 5 * 60 * 1000L); // 5 minute expiry
        
        sender.addChatMessage(ServerUtilities.lang(sender, "Surrender proposal sent to " + targetTeam.getId() + ". Awaiting leader response."));

        ForgePlayer targetLeader = targetTeam.getOwner();
        if (targetLeader != null && targetLeader.isOnline()) {
            targetLeader.getPlayer().addChatMessage(ServerUtilities.lang(targetLeader.getPlayer(),
                "Team " + p.team.getId() + " has proposed surrender. Use /team surrender accept to accept or /team surrender deny to reject."));
        }
    }

    private void processResponse(ICommandSender sender, ForgePlayer p, boolean accepted) throws CommandException {
        if (!p.hasTeam() || !p.team.isOwner(p)) {
            throw ServerUtilities.error(sender, "Only the team leader can respond to surrender.");
        }

        // Find a pending surrender proposal targeting this team
        WarProposalManager.WarProposal proposal = null;
        ForgeTeam proposerTeam = null;
        
        for (ForgeTeam team : Universe.get().getTeams()) {
            WarProposalManager.WarProposal prop = WarProposalManager.get().getProposal(team, p.team);
            if (prop != null && prop.type == WarProposalManager.ProposalType.SURRENDER) {
                proposal = prop;
                proposerTeam = team;
                break;
            }
        }

        if (proposal == null) {
            throw ServerUtilities.error(sender, "No pending surrender proposal from an enemy team.");
        }

        if (accepted) {
            // Accept surrender: cede all chunks from proposer to target
            if (ClaimedChunks.isActive()) {
                for (serverutils.data.ClaimedChunk chunk : ClaimedChunks.instance.getTeamChunks(proposerTeam, java.util.OptionalInt.empty())) {
                    ClaimedChunks.instance.unclaimChunk(null, chunk.getPos());
                    ServerUtilitiesTeamData teamData = ServerUtilitiesTeamData.get(p.team);
                    serverutils.data.ClaimedChunk newChunk = new serverutils.data.ClaimedChunk(chunk.getPos(), teamData);
                    ClaimedChunks.instance.addChunk(newChunk);
                }
            }

            // End all wars for the surrendering team
            for (String enemyId : WarManager.get().getWarringTeams(proposerTeam.getId())) {
                WarManager.get().endWar(proposerTeam, Universe.get().getTeam(enemyId));
            }

            sender.addChatMessage(ServerUtilities.lang(sender, "You accepted the surrender. All chunks have been ceded."));
            ForgePlayer proposerLeader = proposerTeam.getOwner();
            if (proposerLeader != null && proposerLeader.isOnline()) {
                proposerLeader.getPlayer().addChatMessage(ServerUtilities.lang(proposerLeader.getPlayer(),
                    "Your surrender to " + p.team.getId() + " was accepted. All your chunks have been ceded."));
            }
            
            // Global announcement
            ServerUtilities.announceGlobalSurrender(proposerTeam.getId(), p.team.getId());
        } else {
            sender.addChatMessage(ServerUtilities.lang(sender, "You denied the surrender."));
            ForgePlayer proposerLeader = proposerTeam.getOwner();
            if (proposerLeader != null && proposerLeader.isOnline()) {
                proposerLeader.getPlayer().addChatMessage(ServerUtilities.lang(proposerLeader.getPlayer(),
                    "Your surrender proposal was denied by " + p.team.getId() + "."));
            }
        }

        WarProposalManager.get().removeProposal(proposerTeam, p.team);
    }
}
