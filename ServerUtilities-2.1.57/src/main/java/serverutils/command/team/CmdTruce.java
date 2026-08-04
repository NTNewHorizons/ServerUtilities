package serverutils.command.team;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import serverutils.ServerUtilities;
import serverutils.data.WarManager;
import serverutils.data.WarProposalManager;
import serverutils.lib.command.CmdBase;
import serverutils.lib.command.CommandUtils;
import serverutils.lib.data.ForgePlayer;
import serverutils.lib.data.ForgeTeam;
import serverutils.lib.data.Universe;

public class CmdTruce extends CmdBase {

    public CmdTruce() {
        super("truce", Level.ALL);
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            throw ServerUtilities.error(sender, "Usage: /team truce <targetTeam> or /team truce accept/deny");
        }

        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        ForgePlayer p = CommandUtils.getForgePlayer(player);

        if (!p.hasTeam()) {
            throw ServerUtilities.error(sender, "You must be in a team to propose a truce.");
        }

        if (args[0].equalsIgnoreCase("accept") || args[0].equalsIgnoreCase("deny")) {
            // This is a response to a truce proposal
            processResponse(sender, p, args[0].equalsIgnoreCase("accept"));
            return;
        }

        // Propose truce to target team
        if (!p.team.isOwner(p)) {
            throw ServerUtilities.error(sender, "Only the team leader can propose a truce.");
        }

        String targetTeamId = args[0];
        ForgeTeam targetTeam = Universe.get().getTeam(targetTeamId);
        if (!targetTeam.isValid()) {
            throw ServerUtilities.error(sender, "Target team does not exist.");
        }

        if (!WarManager.get().isAtWar(p.team, targetTeam)) {
            throw ServerUtilities.error(sender, "Your team is not at war with that team.");
        }

        // Check if a proposal is already pending
        if (!WarProposalManager.get().canAddProposal(p.team, targetTeam)) {
            throw ServerUtilities.error(sender, "A proposal is already pending with this team. Wait for it to expire.");
        }

        // Send proposal to target team leader
        WarProposalManager.get().addProposal(WarProposalManager.ProposalType.TRUCE, p.team, targetTeam, 5 * 60 * 1000L); // 5 minute expiry
        
        sender.addChatMessage(ServerUtilities.lang(sender, "Truce proposal sent to " + targetTeam.getId() + ". Awaiting leader response."));

        ForgePlayer targetLeader = targetTeam.getOwner();
        if (targetLeader != null && targetLeader.isOnline()) {
            targetLeader.getPlayer().addChatMessage(ServerUtilities.lang(targetLeader.getPlayer(),
                "Team " + p.team.getId() + " has proposed a truce. Use /team truce accept to accept or /team truce deny to reject."));
        }
    }

    private void processResponse(ICommandSender sender, ForgePlayer p, boolean accepted) throws CommandException {
        if (!p.hasTeam() || !p.team.isOwner(p)) {
            throw ServerUtilities.error(sender, "Only the team leader can respond to a truce.");
        }

        // Find a pending truce proposal targeting this team
        WarProposalManager.WarProposal proposal = null;
        ForgeTeam proposerTeam = null;
        
        for (ForgeTeam team : Universe.get().getTeams()) {
            WarProposalManager.WarProposal prop = WarProposalManager.get().getProposal(team, p.team);
            if (prop != null && prop.type == WarProposalManager.ProposalType.TRUCE) {
                proposal = prop;
                proposerTeam = team;
                break;
            }
        }

        if (proposal == null) {
            throw ServerUtilities.error(sender, "No pending truce proposal from an enemy team.");
        }

        if (accepted) {
            // Accept truce: end the war without ceding chunks
            WarManager.get().endWar(proposerTeam, p.team);

            sender.addChatMessage(ServerUtilities.lang(sender, "You accepted the truce. The war has ended."));
            ForgePlayer proposerLeader = proposerTeam.getOwner();
            if (proposerLeader != null && proposerLeader.isOnline()) {
                proposerLeader.getPlayer().addChatMessage(ServerUtilities.lang(proposerLeader.getPlayer(),
                    "Your truce proposal was accepted by " + p.team.getId() + ". The war has ended."));
            }
            
            // Global announcement
            ServerUtilities.announceGlobalTruce(proposerTeam.getId(), p.team.getId());
        } else {
            sender.addChatMessage(ServerUtilities.lang(sender, "You denied the truce."));
            ForgePlayer proposerLeader = proposerTeam.getOwner();
            if (proposerLeader != null && proposerLeader.isOnline()) {
                proposerLeader.getPlayer().addChatMessage(ServerUtilities.lang(proposerLeader.getPlayer(),
                    "Your truce proposal was denied by " + p.team.getId() + "."));
            }
        }

        WarProposalManager.get().removeProposal(proposerTeam, p.team);
    }
}
