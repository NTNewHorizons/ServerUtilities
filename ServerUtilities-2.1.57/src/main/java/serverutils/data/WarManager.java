package serverutils.data;

import serverutils.lib.data.ForgeTeam;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * Tracks active wars between teams and their expiration times (in millis).
 */
public class WarManager {
    private static final WarManager INSTANCE = new WarManager();
    // Map: teamA -> (teamB -> WarInfo)
    private final Map<String, Map<String, WarInfo>> wars = new HashMap<>();
    private static final String WARS_FILE = "wars.json";
    private static final Gson GSON = new Gson();
    // Persistent sets to track which wars have been announced (prevents message spam)
    private final Set<String> announcedActive = new HashSet<>();
    private final Set<String> announcedEnd = new HashSet<>();

    // Make WarInfo public for use in commands
    public static class WarInfo {
        public long graceEndTime;
        public long warEndTime;
    }

    public static WarManager get() {
        return INSTANCE;
    }

    /**
     * Declare war between two teams for a duration (in millis), with a grace period (in millis).
     */
    public void declareWar(ForgeTeam teamA, ForgeTeam teamB, long durationMillis, long graceMillis) {
        long now = System.currentTimeMillis();
        WarInfo info = new WarInfo();
        info.graceEndTime = now + graceMillis;
        // warEndTime should be after the grace period + the war duration so the grace period
        // elapses first and then the war duration counts down.
        info.warEndTime = now + graceMillis + durationMillis;
        addWar(teamA.getId(), teamB.getId(), info);
        addWar(teamB.getId(), teamA.getId(), info);
    }

    private void addWar(String a, String b, WarInfo info) {
        wars.computeIfAbsent(a, k -> new HashMap<>()).put(b, info);
    }

    /**
     * Returns true if teamA is at war with teamB and war is still active (including grace period).
     */
    public boolean isAtWar(ForgeTeam teamA, ForgeTeam teamB) {
        Map<String, WarInfo> map = wars.get(teamA.getId());
        if (map == null) return false;
        WarInfo info = map.get(teamB.getId());
        if (info == null) return false;
        if (System.currentTimeMillis() > info.warEndTime) {
            map.remove(teamB.getId());
            return false;
        }
        return true;
    }

    /**
     * Returns true if the war is active (grace period has ended and war is still ongoing).
     */
    public boolean isWarActive(ForgeTeam teamA, ForgeTeam teamB) {
        Map<String, WarInfo> map = wars.get(teamA.getId());
        if (map == null) return false;
        WarInfo info = map.get(teamB.getId());
        if (info == null) return false;
        long now = System.currentTimeMillis();
        if (now > info.warEndTime) {
            map.remove(teamB.getId());
            return false;
        }
        return now >= info.graceEndTime;
    }

    /**
     * Get all teams currently at war with the given team.
     */
    public Set<String> getWarringTeams(String teamId) {
        Set<String> result = new HashSet<>();
        Map<String, WarInfo> map = wars.get(teamId);
        if (map != null) {
            long now = System.currentTimeMillis();
            for (Map.Entry<String, WarInfo> e : map.entrySet()) {
                if (e.getValue().warEndTime > now) {
                    result.add(e.getKey());
                }
            }
        }
        return result;
    }

    /**
     * End a war between two teams immediately.
     */
    public void endWar(ForgeTeam teamA, ForgeTeam teamB) {
        Map<String, WarInfo> mapA = wars.get(teamA.getId());
        if (mapA != null) {
            mapA.remove(teamB.getId());
        }
        Map<String, WarInfo> mapB = wars.get(teamB.getId());
        if (mapB != null) {
            mapB.remove(teamA.getId());
        }
    }

    /**
     * Get war info for a specific war if it exists.
     */
    public WarInfo getWarInfo(ForgeTeam teamA, ForgeTeam teamB) {
        Map<String, WarInfo> map = wars.get(teamA.getId());
        if (map == null) return null;
        return map.get(teamB.getId());
    }

    /**
     * Resume a paused war (called when ceasefire expires).
     */
    public void resumeWar(ForgeTeam teamA, ForgeTeam teamB, long remainingMillis) {
        long now = System.currentTimeMillis();
        WarInfo info = new WarInfo();
        info.graceEndTime = now; // grace already passed
        info.warEndTime = now + remainingMillis;
        addWar(teamA.getId(), teamB.getId(), info);
        addWar(teamB.getId(), teamA.getId(), info);
    }

    // Returns a copy of all wars for listing
    public Map<String, Map<String, WarInfo>> getAllWars() {
        Map<String, Map<String, WarInfo>> copy = new HashMap<>();
        for (Map.Entry<String, Map<String, WarInfo>> entry : wars.entrySet()) {
            copy.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        return copy;
    }

    // Call this periodically (e.g. from a tick handler) to announce wars becoming active and end expired wars
    public void cleanupExpiredWars() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Map<String, WarInfo>> entry : new HashMap<>(wars).entrySet()) {
            String teamA = entry.getKey();
            for (Map.Entry<String, WarInfo> e : new HashMap<>(entry.getValue()).entrySet()) {
                String teamB = e.getKey();
                WarInfo info = e.getValue();
                // Announce war active (only once per war)
                if (now >= info.graceEndTime && now < info.warEndTime && !announcedActive.contains(teamA + ":" + teamB) && !announcedActive.contains(teamB + ":" + teamA)) {
                    serverutils.ServerUtilities.announceGlobalWarActive(teamA, teamB);
                    announcedActive.add(teamA + ":" + teamB);
                }
                // Announce war end (only once per war)
                if (info.warEndTime <= now && !announcedEnd.contains(teamA + ":" + teamB) && !announcedEnd.contains(teamB + ":" + teamA)) {
                    entry.getValue().remove(teamB);
                    Map<String, WarInfo> other = wars.get(teamB);
                    if (other != null) other.remove(teamA);
                    serverutils.ServerUtilities.announceGlobalWarEnd(teamA, teamB);
                    announcedEnd.add(teamA + ":" + teamB);
                    // Clean up announcement tracking when war ends
                    announcedActive.remove(teamA + ":" + teamB);
                    announcedActive.remove(teamB + ":" + teamA);
                }
            }
        }
    }

    public void saveWars() {
        try (FileWriter writer = new FileWriter(new File(WARS_FILE))) {
            GSON.toJson(wars, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadWars() {
        File file = new File(WARS_FILE);
        if (!file.exists()) return;
        try (FileReader reader = new FileReader(file)) {
            java.lang.reflect.Type type = new TypeToken<Map<String, Map<String, WarInfo>>>(){}.getType();
            Map<String, Map<String, WarInfo>> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                wars.clear();
                wars.putAll(loaded);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
