package serverutils.command.team;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import serverutils.ServerUtilities;
import serverutils.ServerUtilitiesConfig;
import serverutils.lib.command.CmdBase;
import serverutils.lib.command.CommandUtils;
import serverutils.lib.data.ForgePlayer;
import serverutils.lib.data.ForgeTeam;
import serverutils.lib.data.Universe;
import serverutils.data.ServerUtilitiesTeamData;

public class CmdDeclareWar extends CmdBase {

    public CmdDeclareWar() {
        super("declarewar", Level.ALL);
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        // Usage: /team declarewar <targetTeam>
        if (args.length < 1) {
            throw ServerUtilities.error(sender, "Usage: /team declarewar <targetTeam>");
        }

        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        ForgePlayer p = CommandUtils.getForgePlayer(player);
        if (!p.hasTeam()) {
            throw ServerUtilities.error(sender, "You must be in a team to declare war.");
        }

        String targetTeamId = args[0];
        ForgeTeam targetTeam = Universe.get().getTeam(targetTeamId);
        
        // If not found by ID, try to find by title
        if (!targetTeam.isValid()) {
            for (ForgeTeam team : Universe.get().getTeams()) {
                if (team.getTitle().getUnformattedText().equalsIgnoreCase(targetTeamId)) {
                    targetTeam = team;
                    break;
                }
            }
        }
        
        if (!targetTeam.isValid()) {
            throw ServerUtilities.error(sender, "Target team does not exist.");
        }

        if (p.team.equalsTeam(targetTeam)) {
            throw ServerUtilities.error(sender, "You cannot declare war on your own team.");
        }

        // Calculate active team members for target team
        // "Active" means either online OR logged in within the inactivity threshold
        int inactivityDays = ServerUtilitiesConfig.teams.war_player_inactivity_days;
        long inactivityTicks = (long) inactivityDays * 24L * 60L * 60L * 20L; // Convert days to ticks
        long currentTicks = targetTeam.universe.ticks.ticks();

        int totalMembers = targetTeam.getMembers().size();
        int activeMembers = 0;
        int onlineMembers = 0;

        for (ForgePlayer member : targetTeam.getMembers()) {
            if (member.isOnline()) {
                onlineMembers++;
                activeMembers++;
            } else {
                // Check if member logged in recently enough
                long timeSinceSeen = currentTicks - member.getLastTimeSeen();
                if (timeSinceSeen <= inactivityTicks) {
                    activeMembers++;
                }
            }
        }

        // Validate team activity based on config
        int requiredActivePercentage = ServerUtilitiesConfig.teams.war_team_active_percentage;
        boolean allowAllOffline = ServerUtilitiesConfig.teams.war_allow_all_offline;

        if (onlineMembers == 0 && !allowAllOffline) {
            // No one is online and offline wars are not allowed
            throw ServerUtilities.error(sender, "You cannot declare war on a team with no online members.");
        }

        if (requiredActivePercentage > 0) {
            // Calculate required active members (round up)
            int requiredActive = (int) Math.ceil(totalMembers * requiredActivePercentage / 100.0);
            if (activeMembers < requiredActive) {
                throw ServerUtilities.error(sender, "Target team does not meet the activity requirement. "
                    + activeMembers + "/" + requiredActive + " members are active enough to declare war.");
            }
        }

        // Check if already at war
        if (serverutils.data.WarManager.get().isAtWar(p.team, targetTeam)) {
            throw ServerUtilities.error(sender, "You are already at war with this team.");
        }

        // Cooldown check
        long now = System.currentTimeMillis();
        ServerUtilitiesTeamData teamData = ServerUtilitiesTeamData.get(p.team);
        if (teamData.warCooldownUntil != null && now < teamData.warCooldownUntil) {
            long secondsLeft = (teamData.warCooldownUntil - now) / 1000L;
            throw ServerUtilities.error(sender, "You must wait " + secondsLeft + " seconds before declaring war again.");
        }

        // Only allow team leaders to declare war
        if (!p.team.isOwner(p)) {
            throw ServerUtilities.error(sender, "Only the team leader can declare war on another team.");
        }

    // Register war in WarManager using configured grace period.
    long graceMillis = (long) ServerUtilitiesConfig.teams.war_grace_period_seconds * 1000L;
    serverutils.data.WarManager.get().declareWar(p.team, targetTeam,
        ServerUtilitiesConfig.teams.war_duration_minutes * 60L * 1000L, graceMillis);
        // Set cooldown
        teamData.warCooldownUntil = now + (ServerUtilitiesConfig.teams.war_cooldown_seconds * 1000L);

    // Friendly message: prefer minutes when possible
    long graceSeconds = ServerUtilitiesConfig.teams.war_grace_period_seconds;
    String whenMsg;
    if (graceSeconds % 60 == 0) {
        whenMsg = (graceSeconds / 60) + " minutes";
    } else {
        whenMsg = graceSeconds + " seconds";
    }

    sender.addChatMessage(ServerUtilities.lang(sender,
        "War declared between your team and " + targetTeam.getId() + "! The war will become active in " + whenMsg + "."));

        // Announce to all players on the server (no grace period mention)
        for (Object obj : player.mcServer.getConfigurationManager().playerEntityList) {
            if (obj instanceof EntityPlayerMP) {
                ((EntityPlayerMP) obj).addChatMessage(ServerUtilities.lang((EntityPlayerMP) obj,
                    "\u00a7cWar declared between team " + p.team.getTitle().getUnformattedText() + " and team " + targetTeam.getTitle().getUnformattedText() + "!"));
            }
        }
    }
}
