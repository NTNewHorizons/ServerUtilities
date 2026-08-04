package serverutils.lib.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import serverutils.lib.data.ForgeTeam;
import serverutils.lib.data.ForgePlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import serverutils.events.team.ForgeTeamCreatedEvent;
import serverutils.events.team.ForgeTeamDeletedEvent;
import serverutils.events.team.ForgeTeamPlayerJoinedEvent;
import serverutils.events.team.ForgeTeamPlayerLeftEvent;
import serverutils.events.team.ForgeTeamLoadedEvent;
import serverutils.lib.data.Universe;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Helper that keeps Minecraft scoreboard teams in sync with ServerUtilities teams.
 */
public class ScoreboardSync {

    private static ScoreboardSync INSTANCE = new ScoreboardSync();

    public static void init() {
        // Ensure instance exists and register for events via Forge event bus if needed.
    }

    public ScoreboardSync() {
    }

    private boolean enabled() {
        try {
            return serverutils.ServerUtilitiesConfig.teams.scoreboard_sync_enabled;
        } catch (Throwable t) {
            // If config isn't accessible for some reason, default to enabled to avoid
            // changing behaviour unexpectedly.
            return true;
        }
    }

    private Scoreboard getScoreboard() {
        if (!enabled()) return null;
        if (!Universe.loaded()) return null;
        MinecraftServer server = Universe.get().server;
        if (server == null || server.worldServers == null || server.worldServers.length == 0) return null;
        return server.worldServers[0].getScoreboard();
    }

    private String teamNameFor(ForgeTeam team) {
        String id = team.getId();
        // Scoreboard team names are limited (40 chars in vanilla); keep simple
        String name = "su_" + id;
        // Older MC versions / scoreboard implementations may have stricter limits
        // (some fields in Minecraft only accept 16 chars). Truncate defensively
        // to avoid creating invalid scoreboard entries that can crash the server.
        final int MAX_SB_TEAM_NAME = 16;
        if (name.length() > MAX_SB_TEAM_NAME) {
            return name.substring(0, MAX_SB_TEAM_NAME);
        }
        return name;
    }

    private ScorePlayerTeam getOrCreateTeam(ForgeTeam team) {
        Scoreboard sb = getScoreboard();
        if (sb == null) return null;
        String name = teamNameFor(team);
        ScorePlayerTeam spt = sb.getTeam(name);
        if (spt == null) {
            try {
                // Avoid creating an excessive number of teams which may stress
                // the scoreboard implementation.
                int teamCount = sb.getTeams().size();
                final int MAX_ALLOWED_SCOREBOARD_TEAMS = 1024;
                if (teamCount >= MAX_ALLOWED_SCOREBOARD_TEAMS) {
                    serverutils.ServerUtilities.LOGGER.warn("Not creating scoreboard team '" + name + "' - too many teams: " + teamCount);
                    return null;
                }

                spt = sb.createTeam(name);
            } catch (Throwable t) {
                // Defensive: log error and don't propagate, to avoid softlocking the server
                serverutils.ServerUtilities.LOGGER.error("Failed to create scoreboard team '" + name + "'", t);
                return null;
            }
        }
        return spt;
    }

    private void removeScoreboardTeam(ForgeTeam team) {
        Scoreboard sb = getScoreboard();
        if (sb == null) return;
        String name = teamNameFor(team);
        ScorePlayerTeam spt = sb.getTeam(name);
        if (spt != null) {
            try {
                sb.removeTeam(spt);
            } catch (Throwable t) {
                serverutils.ServerUtilities.LOGGER.error("Failed to remove scoreboard team '" + name + "'", t);
            }
        }
    }

    private void addPlayerToTeam(ForgeTeam team, ForgePlayer player) {
        Scoreboard sb = getScoreboard();
        if (sb == null) return;
        ScorePlayerTeam spt = getOrCreateTeam(team);
        if (spt == null) return;
        String playerName = player.getName();
        
        // remove from any existing team first
        ScorePlayerTeam currentTeam = sb.getPlayersTeam(playerName);
        if (currentTeam != null && currentTeam != spt) {
            try {
                sb.removePlayerFromTeam(playerName, currentTeam);
            } catch (Throwable ignore) {
                // ignore
            }
        }
        
        // Try direct team member collection manipulation (works in 1.7.10)
        try {
            java.lang.reflect.Field membershipCollection = spt.getClass().getDeclaredField("membershipCollection");
            membershipCollection.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Collection<String> c = (java.util.Collection<String>) membershipCollection.get(spt);
            if (c != null && !c.contains(playerName)) {
                c.add(playerName);
                return;
            }
        } catch (Throwable ignore) {
            // ignore and try next method
        }
        
        // Fallback: try any addPlayer variant that might exist
        try {
            java.lang.reflect.Method addPlayerMethod = spt.getClass().getMethod("addPlayer", String.class);
            addPlayerMethod.invoke(spt, playerName);
            return;
        } catch (Throwable ignore) {
            // ignore and try next method
        }
        
        // Last fallback: try using team's players set directly if exposed
        try {
            java.lang.reflect.Field playersField = spt.getClass().getDeclaredField("players");
            playersField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Set<String> players = (java.util.Set<String>) playersField.get(spt);
            if (players != null && !players.contains(playerName)) {
                players.add(playerName);
                return;
            }
        } catch (Throwable ignore) {
            // ignore
        }
    }

    private void removePlayerFromTeam(ForgeTeam team, ForgePlayer player) {
        Scoreboard sb = getScoreboard();
        if (sb == null) return;
        ScorePlayerTeam spt = sb.getTeam(teamNameFor(team));
        if (spt == null) return;
        String playerName = player.getName();
        try {
            if (sb.getPlayersTeam(playerName) == spt) {
                sb.removePlayerFromTeam(playerName, spt);
            }
        } catch (Throwable ignore) {
            // ignore
        }
    }

    @SubscribeEvent
    public void onTeamCreated(ForgeTeamCreatedEvent event) {
        getOrCreateTeam(event.getTeam());
    }

    @SubscribeEvent
    public void onTeamDeleted(ForgeTeamDeletedEvent event) {
        removeScoreboardTeam(event.getTeam());
    }

    @SubscribeEvent
    public void onPlayerJoined(ForgeTeamPlayerJoinedEvent event) {
        ForgePlayer p = event.getPlayer();
        ForgeTeam team = p.team;
        if (team != null && team.isValid()) {
            addPlayerToTeam(team, p);
        }
    }

    @SubscribeEvent
    public void onPlayerLeft(ForgeTeamPlayerLeftEvent event) {
        ForgePlayer p = event.getPlayer();
        // we don't always know previous team here; try to remove from any su_ team
        Scoreboard sb = getScoreboard();
        if (sb == null) return;
        String playerName = p.getName();
        if (sb.getPlayersTeam(playerName) != null) {
            sb.removePlayerFromTeam(playerName, sb.getPlayersTeam(playerName));
        }
    }

    @SubscribeEvent
    public void onTeamLoaded(ForgeTeamLoadedEvent event) {
        ForgeTeam team = event.getTeam();
        ScorePlayerTeam spt = getOrCreateTeam(team);
        if (spt == null) return;
        for (ForgePlayer p : team.getMembers()) {
            EntityPlayerMP playerEnt = p.getNullablePlayer();
            if (playerEnt != null) {
                addPlayerToTeam(team, p);
            }
        }
    }
}
