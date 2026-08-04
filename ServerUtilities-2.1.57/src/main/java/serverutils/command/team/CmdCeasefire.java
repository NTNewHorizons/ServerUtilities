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

public class CmdCeasefire extends CmdBase {

    public CmdCeasefire() {
        super("ceasefire", Level.ALL);
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            throw ServerUtilities.error(sender, "Usage: /team ceasefire <targetTeam> <minutes> or /team ceasefire accept/deny");
        }

        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        ForgePlayer p = CommandUtils.getForgePlayer(player);

        if (!p.hasTeam()) {
            throw ServerUtilities.error(sender, "You must be in a team to propose a ceasefire.");
        }

        if (args[0].equalsIgnoreCase("accept") || args[0].equalsIgnoreCase("deny")) {
            // This is a response to a ceasefire proposal
            processResponse(sender, p, args[0].equalsIgnoreCase("accept"));
            return;
        }

        // Propose ceasefire to target team
        if (!p.team.isOwner(p)) {
            throw ServerUtilities.error(sender, "Only the team leader can propose a ceasefire.");
        }

        if (args.length < 2) {
            throw ServerUtilities.error(sender, "Usage: /team ceasefire <targetTeam> <minutes>");
        }

        String targetTeamId = args[0];
        ForgeTeam targetTeam = Universe.get().getTeam(targetTeamId);
        if (!targetTeam.isValid()) {
            throw ServerUtilities.error(sender, "Target team does not exist.");
        }

        if (!WarManager.get().isAtWar(p.team, targetTeam)) {
            throw ServerUtilities.error(sender, "Your team is not at war with that team.");
        }

        long ceasefireMinutes;
        try {
            ceasefireMinutes = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            throw ServerUtilities.error(sender, "Duration must be a number in minutes.");
        }

        if (ceasefireMinutes <= 0) {
            throw ServerUtilities.error(sender, "Duration must be positive.");
        }

        // Check if a proposal is already pending
        if (!WarProposalManager.get().canAddProposal(p.team, targetTeam)) {
            throw ServerUtilities.error(sender, "A proposal is already pending with this team. Wait for it to expire.");
        }

        // Send proposal to target team leader
        WarProposalManager.WarProposal proposal = new WarProposalManager.WarProposal(
            WarProposalManager.ProposalType.CEASEFIRE, p.team, targetTeam, 5 * 60 * 1000L); // 5 minute proposal expiry
        proposal.ceasefireDurationMillis = ceasefireMinutes * 60 * 1000L;
        WarProposalManager.get().addProposal(proposal);

        sender.addChatMessage(ServerUtilities.lang(sender, "Ceasefire proposal sent to " + targetTeam.getId() + " for " + ceasefireMinutes + " minutes. Awaiting leader response."));

        ForgePlayer targetLeader = targetTeam.getOwner();
        if (targetLeader != null && targetLeader.isOnline()) {
            targetLeader.getPlayer().addChatMessage(ServerUtilities.lang(targetLeader.getPlayer(),
                "Team " + p.team.getId() + " has proposed a " + ceasefireMinutes + " minute ceasefire. Use /team ceasefire accept to accept or /team ceasefire deny to reject."));
        }
    }

    private void processResponse(ICommandSender sender, ForgePlayer p, boolean accepted) throws CommandException {
        if (!p.hasTeam() || !p.team.isOwner(p)) {
            throw ServerUtilities.error(sender, "Only the team leader can respond to a ceasefire.");
        }

        // Find a pending ceasefire proposal targeting this team
        WarProposalManager.WarProposal proposal = null;
        ForgeTeam proposerTeam = null;
        
        for (ForgeTeam team : Universe.get().getTeams()) {
            WarProposalManager.WarProposal prop = WarProposalManager.get().getProposal(team, p.team);
            if (prop != null && prop.type == WarProposalManager.ProposalType.CEASEFIRE) {
                proposal = prop;
                proposerTeam = team;
                break;
            }
        }

        if (proposal == null) {
            throw ServerUtilities.error(sender, "No pending ceasefire proposal from an enemy team.");
        }

        if (accepted) {
            // Accept ceasefire: pause the war
            WarManager.WarInfo warInfo = WarManager.get().getWarInfo(proposerTeam, p.team);
            if (warInfo != null) {
                long remainingTime = warInfo.warEndTime - System.currentTimeMillis();
                if (remainingTime > 0) {
                    WarProposalManager.get().pauseWar(proposerTeam, p.team, remainingTime);
                    // War is paused; it can be resumed manually via a command or automatically if needed
                }
            }

            sender.addChatMessage(ServerUtilities.lang(sender, "You accepted the ceasefire. The war has been paused for " + (proposal.ceasefireDurationMillis / 60 / 1000) + " minutes."));
            ForgePlayer proposerLeader = proposerTeam.getOwner();
            if (proposerLeader != null && proposerLeader.isOnline()) {
                proposerLeader.getPlayer().addChatMessage(ServerUtilities.lang(proposerLeader.getPlayer(),
                    "Your ceasefire proposal was accepted by " + p.team.getId() + ". War paused for " + (proposal.ceasefireDurationMillis / 60 / 1000) + " minutes."));
            }
            
            // Global announcement
            ServerUtilities.announceGlobalCeasefire(proposerTeam.getId(), p.team.getId(), (int)(proposal.ceasefireDurationMillis / 60 / 1000));
        } else {
            sender.addChatMessage(ServerUtilities.lang(sender, "You denied the ceasefire."));
            ForgePlayer proposerLeader = proposerTeam.getOwner();
            if (proposerLeader != null && proposerLeader.isOnline()) {
                proposerLeader.getPlayer().addChatMessage(ServerUtilities.lang(proposerLeader.getPlayer(),
                    "Your ceasefire proposal was denied by " + p.team.getId() + "."));
            }
        }

        WarProposalManager.get().removeProposal(proposerTeam, p.team);
    }
}
