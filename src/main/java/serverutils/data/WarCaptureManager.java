package serverutils.data;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import serverutils.ServerUtilities;
import serverutils.ServerUtilitiesConfig;
import serverutils.events.chunks.ChunkModifiedEvent;
import serverutils.data.ClaimedChunk;
import serverutils.data.ClaimedChunks;
import serverutils.data.WarManager;
import serverutils.data.ServerUtilitiesTeamData;
import serverutils.lib.data.ForgePlayer;
import serverutils.lib.data.ForgeTeam;
import serverutils.lib.math.ChunkDimPos;

/**
 * Manages automatic capture-from-edge behavior during wars.
 */
public class WarCaptureManager {

    private static final WarCaptureManager INSTANCE = new WarCaptureManager();

    public static WarCaptureManager get() {
        return INSTANCE;
    }

    public static final class CaptureInfo {
        public final UUID playerId;
        public final ChunkDimPos center;
        public final double startX, startY, startZ;
        public final long endTime;
        public final ForgeTeam enemyTeam;
        public final ForgeTeam capturerTeam;

        public CaptureInfo(UUID pid, ChunkDimPos c, double sx, double sy, double sz, long end, ForgeTeam enemy, ForgeTeam capturer) {
            playerId = pid;
            center = c;
            startX = sx;
            startY = sy;
            startZ = sz;
            endTime = end;
            enemyTeam = enemy;
            capturerTeam = capturer;
        }
    }

    private final Map<UUID, CaptureInfo> active = new HashMap<>();

    private WarCaptureManager() {}

    /** Get a map of active captures (player UUID -> CaptureInfo) for display in commands */
    public Map<UUID, CaptureInfo> getActiveCaptures() {
        return new HashMap<>(active);
    }

    /** Called periodically (when server tick logic decides it's time) */
    public void onCheck(long now) {
        if (!ServerUtilitiesConfig.teams.war_capture_enabled || !ClaimedChunks.isActive()) return;

        int radius = ServerUtilitiesConfig.teams.war_capture_radius_chunks;
        int offset = radius + 1; // check one chunk further than capture radius
        int holdSec = ServerUtilitiesConfig.teams.war_capture_hold_seconds;

        // Iterate online players
        java.util.Collection<ForgePlayer> onlinePlayers = serverutils.lib.data.Universe.get().getOnlinePlayers();
        for (ForgePlayer fp : onlinePlayers) {
            if (!fp.isOnline() || !fp.hasTeam()) {
                continue;
            }

            EntityPlayerMP ep = fp.getNullablePlayer();
            if (ep == null) {
                continue;
            }

            // FORCE FRESH DATA FETCH: Re-fetch the player's current chunk coordinates every tick
            // This ensures we don't use stale cached data from previous ticks
            ChunkDimPos curChunk = new ChunkDimPos(ep);
            
            // FORCE FRESH DATA FETCH: Re-query the claim status for this exact chunk
            // This ensures we detect when a player leaves a claimed chunk
            ClaimedChunk curClaim = ClaimedChunks.instance.getChunk(curChunk);

            // Player must be standing inside a claimed chunk owned by the enemy to start a front capture.
            if (curClaim == null) {
                // Not in an enemy chunk: cancel any pending capture
                active.remove(fp.getId());
                continue;
            }

            ForgeTeam claimOwner = curClaim.getTeam();

            // Only consider captures when war is active between player's team and claim owner
            boolean warActive = WarManager.get().isWarActive(fp.team, claimOwner);
            if (!warActive) {
                active.remove(fp.getId());
                continue;
            }

            // Check if at least one direction at offset contains a non-enemy chunk (unclaimed or friendly)
            // FORCE FRESH: Re-compute edge check every tick using the freshly-fetched curChunk
            boolean edgeOk = false;
            int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};

            java.util.List<String> neighborOwners = new java.util.ArrayList<>();
            for (int i = 0; i < dirs.length; i++) {
                int[] d = dirs[i];
                ChunkDimPos checkPos = new ChunkDimPos(curChunk.posX + d[0]*offset, curChunk.posZ + d[1]*offset, curChunk.dim);
                ClaimedChunk c2 = ClaimedChunks.instance.getChunk(checkPos);

                if (ServerUtilitiesConfig.teams.war_capture_debug) {
                    String owner = c2 == null ? "<unclaimed>" : (c2.getTeam() == null ? "<nullTeam>" : c2.getTeam().getId());
                    neighborOwners.add(owner);
                }

                if (c2 == null) {
                    // wilderness at offset -> valid edge
                    edgeOk = true;
                    break;
                }

                ForgeTeam neighborOwner = c2.getTeam();
                // If neighbor is owned by player's team, it's a valid edge (capping into friendly)
                if (neighborOwner != null && neighborOwner.equalsTeam(fp.team)) {
                    edgeOk = true;
                    break;
                }
            }

            // Send a compact in-chat debug summary to the player so they see immediate results
            if (ServerUtilitiesConfig.teams.war_capture_debug) {
                try {
                    StringBuilder nb = new StringBuilder();
                    for (int i = 0; i < neighborOwners.size(); i++) {
                        if (i > 0) nb.append(",");
                        nb.append(neighborOwners.get(i));
                    }
                    String msg = "[WarCapture] " + fp.getName() + " at " + curChunk + " warActive=" + warActive + " edgeOk=" + edgeOk + " neighbors=" + nb.toString();
                    try { ep.addChatMessage(ServerUtilities.lang(ep, msg)); } catch (Exception ignored) {}
                } catch (Exception ignored) {}
            }

            // NOW use the FRESH edgeOk value to determine the main control flow
            if (edgeOk) {
                // Allow multiple active claimants per team
                ServerUtilitiesTeamData capturerTeamData = ServerUtilitiesTeamData.get(fp.team);
                CaptureInfo existing = active.get(fp.getId());
                if (existing == null || !existing.center.equalsChunkDimPos(curChunk)) {
                    long end = now + (long) holdSec * 1000L;
                    String initMsg = "[WarCapture] Starting capture: now=" + now + " holdSec=" + holdSec + " endTime=" + end + " (will complete at " + new java.util.Date(end) + ")";
                    System.out.println(initMsg);
                    CaptureInfo info = new CaptureInfo(fp.getId(), new ChunkDimPos(curChunk.posX, curChunk.posZ, curChunk.dim), ep.posX, ep.posY, ep.posZ, end, claimOwner, fp.team);
                    active.put(fp.getId(), info);
                    capturerTeamData.capturingInProgress = true;
                    // CRITICAL: Mark team dirty so flag change is recognized globally
                    fp.team.markDirty();
                    // notify player
                    try {
                        ep.addChatMessage(ServerUtilities.lang(ep, "§a[WarCapture] You started capturing chunks from " + claimOwner.getId() + ". Stay within " + radius + " chunks to complete in " + holdSec + "s!"));
                    } catch (Exception ignored) {}
                }
            } else {
                // Player is NOT in a valid capture position. Check if they were capturing.
                CaptureInfo capture = active.get(fp.getId());
                ServerUtilitiesTeamData teamData = ServerUtilitiesTeamData.get(fp.team);
                
                System.out.println("[WarCapture] DEBUG: Player " + fp.getName() + " in else block. capture=" + (capture != null) + " flag=" + teamData.capturingInProgress + " chunk=" + curChunk);
                
                if (capture != null) {
                    // This player WAS capturing. Stop it.
                    System.out.println("[WarCapture] Removing capture for " + fp.getName());
                    active.remove(fp.getId());
                    teamData.capturingInProgress = false;
                    // CRITICAL: Mark team dirty to ensure flag change is recognized globally
                    fp.team.markDirty();
                    
                    String msg = "§c[WarCapture] You left the capture area. Capture has been stopped.";
                    System.out.println("[WarCapture] Sending message to " + fp.getName() + ": " + msg);
                    ep.addChatMessage(ServerUtilities.lang(ep, msg));
                    
                    if (ServerUtilitiesConfig.teams.war_capture_debug) {
                        System.out.println("[WarCapture] Capture stopped and flag reset for " + fp.getName() + ". Flag now = " + teamData.capturingInProgress);
                    }
                } else if (teamData.capturingInProgress) {
                    // SAFETY: Flag is true but no capture in map - desynchronized state.
                    System.out.println("[WarCapture] WARNING: Team " + fp.team.getId() + " has capturingInProgress=true but no capture in map!");
                    teamData.capturingInProgress = false;
                    fp.team.markDirty();
                    System.out.println("[WarCapture] Forcing flag reset for " + fp.getName() + ". Flag now = " + teamData.capturingInProgress);
                    ep.addChatMessage(ServerUtilities.lang(ep, "§e[WarCapture] Resetting stuck capture state for your team."));
                }
            }
        }

        // Process active captures that may have completed or need cancellation
        Iterator<Map.Entry<UUID, CaptureInfo>> it = active.entrySet().iterator();
        int capturesProcessed = 0;
        while (it.hasNext()) {
            capturesProcessed++;
            Map.Entry<UUID, CaptureInfo> e = it.next();
            CaptureInfo info = e.getValue();
            if (active.size() > 0 && capturesProcessed == 1) {
                // Log first capture of each check
                long timeRem = info.endTime - System.currentTimeMillis();
                System.out.println("[WarCapture] onCheck() processing " + active.size() + " active capture(s), first has " + Math.max(0, timeRem/1000) + "s remaining");
            }
            ForgePlayer fp = serverutils.lib.data.Universe.get().getPlayer(info.playerId);
            if (fp == null || !fp.isOnline()) {
                it.remove();
                continue;
            }

            EntityPlayerMP ep = fp.getNullablePlayer();
            if (ep == null) { it.remove(); continue; }

            // Verify player still within allowed movement distance from the center chunk and still at war
            double px = ep.posX;
            double pz = ep.posZ;
            double cx = info.center.getBlockX();
            double cz = info.center.getBlockZ();
            int allowedBlocks = ServerUtilitiesConfig.teams.war_capture_radius_chunks * 16; // radius in chunks -> blocks
            double dx = px - cx;
            double dz = pz - cz;
            double distSq = dx * dx + dz * dz;
            if (distSq > (double) allowedBlocks * (double) allowedBlocks) {
                // moved too far -> cancel
                try { 
                    ep.addChatMessage(ServerUtilities.lang(ep, "§c[WarCapture] You moved too far from the capture area! (" + Math.round(Math.sqrt(distSq)) + "/" + allowedBlocks + " blocks)")); 
                } catch (Exception ignored) {}
                cancelCapture(info.playerId);
                it.remove();
                continue;
            }

            ClaimedChunk centerChunk = ClaimedChunks.instance.getChunk(info.center);
            if (centerChunk == null || !WarManager.get().isWarActive(fp.team, centerChunk.getTeam())
                    || !centerChunk.getTeam().equalsTeam(info.enemyTeam)) {
                if (ServerUtilitiesConfig.teams.war_capture_debug) {
                    String reason = centerChunk == null ? "chunk unclaimed" : (!WarManager.get().isWarActive(fp.team, centerChunk.getTeam()) ? "war ended" : "chunk ownership changed");
                    try { ep.addChatMessage(ServerUtilities.lang(ep, "[WarCapture] Capture cancelled - " + reason)); } catch (Exception ignored) {}
                }
                cancelCapture(info.playerId);
                it.remove();
                continue;
            }

            long currentTime = System.currentTimeMillis();
            long timeRemaining = info.endTime - currentTime;
            
            if (currentTime >= info.endTime) {
                // Capture complete! Execute the capture
                String dbgMsg = "[WarCapture] Capture completion triggered: currentTime=" + currentTime + " endTime=" + info.endTime + " diff=" + (currentTime - info.endTime);
                System.out.println(dbgMsg);
                if (ServerUtilitiesConfig.teams.war_capture_debug) {
                    try { ep.addChatMessage(ServerUtilities.lang(ep, "[WarCapture] Executing capture!")); } catch (Exception ignored) {}
                }
                try {
                    int ceded = executeCapture(fp, info.center, info.enemyTeam, radius);
                    try {
                        ep.addChatMessage(ServerUtilities.lang(ep, "§a[WarCapture] Capture complete: ceded " + ceded + " chunks to your team!"));
                    } catch (Exception ignored) {}
                    if (ceded > 0) {
                        // Announce to other team members
                        for (ForgePlayer teamMember : fp.team.getMembers()) {
                            if (teamMember.isOnline() && !teamMember.getId().equals(fp.getId())) {
                                try {
                                    EntityPlayerMP tmp = teamMember.getNullablePlayer();
                                    if (tmp != null) {
                                        tmp.addChatMessage(ServerUtilities.lang(tmp, "§a[WarCapture] " + fp.getName() + " captured " + ceded + " chunks from enemy!"));
                                    }
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                } catch (Exception ex) {
                    try {
                        ep.addChatMessage(ServerUtilities.lang(ep, "§c[WarCapture] Capture failed: " + ex.getMessage()));
                    } catch (Exception ignored) {}
                }
                // clear team flag
                ServerUtilitiesTeamData.get(info.capturerTeam).capturingInProgress = false;
                it.remove();
            } else if (timeRemaining > 0 && timeRemaining <= 5000) {
                // Notify player when capture is about to complete (within 5 seconds)
                try {
                    ep.addChatMessage(ServerUtilities.lang(ep, "§6[WarCapture] Capture completing in " + (timeRemaining / 1000) + " seconds..."));
                } catch (Exception ignored) {}
            }
        }
    }

    private void cancelCapture(UUID playerId) {
        CaptureInfo info = active.remove(playerId);
        // Always attempt to reset the team's capturing flag
        try {
            if (info != null && info.capturerTeam != null) {
                ServerUtilitiesTeamData.get(info.capturerTeam).capturingInProgress = false;
            } else {
                // Fallback: resolve team from playerId if capture info was missing
                serverutils.lib.data.ForgePlayer fp = serverutils.lib.data.Universe.get().getPlayer(playerId);
                if (fp != null && fp.hasTeam()) {
                    ServerUtilitiesTeamData.get(fp.team).capturingInProgress = false;
                }
            }
        } catch (Exception ignored) {}
    }

    private int executeCapture(ForgePlayer capturer, ChunkDimPos center, ForgeTeam enemyTeam, int radius) {
        if (capturer == null || !capturer.hasTeam()) return 0;

        int ceded = 0;
        
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                ChunkDimPos pos = new ChunkDimPos(center.posX + dx, center.posZ + dz, center.dim);
                ClaimedChunk existing = ClaimedChunks.instance.getChunk(pos);
                
                if (existing == null) {
                    // Unclaimed - try to claim it
                    try {
                        ServerUtilitiesTeamData teamData = ServerUtilitiesTeamData.get(capturer.team);
                        ClaimedChunk newChunk = new ClaimedChunk(pos, teamData);
                        ClaimedChunks.instance.addChunk(newChunk);
                        new ChunkModifiedEvent.Claimed(newChunk, capturer).post();
                        ceded++;
                    } catch (Exception e) {
                        // Silent fail - log if needed but don't crash
                    }
                } else if (existing.getTeam() != null && existing.getTeam().equalsTeam(enemyTeam)) {
                    // Enemy chunk - unclaim and reclaim for our team
                    try {
                        ClaimedChunks.instance.unclaimChunk(null, pos);
                        ServerUtilitiesTeamData teamData = ServerUtilitiesTeamData.get(capturer.team);
                        ClaimedChunk newChunk = new ClaimedChunk(pos, teamData);
                        ClaimedChunks.instance.addChunk(newChunk);
                        new ChunkModifiedEvent.Claimed(newChunk, capturer).post();
                        ceded++;
                    } catch (Exception e) {
                        // Silent fail
                    }
                }
                // else - friendly or third party, skip
            }
        }

        return ceded;
    }
}
