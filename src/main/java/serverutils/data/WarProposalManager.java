package serverutils.data;

import java.util.HashMap;
import java.util.Map;

import serverutils.lib.data.ForgeTeam;

/**
 * Manages pending war proposals (surrender, truce, ceasefire).
 */
public class WarProposalManager {
    private static final WarProposalManager INSTANCE = new WarProposalManager();

    public static WarProposalManager get() {
        return INSTANCE;
    }

    public enum ProposalType {
        SURRENDER, TRUCE, CEASEFIRE
    }

    public static final class WarProposal {
        public final ProposalType type;
        public final ForgeTeam proposer;
        public final ForgeTeam target;
        public final long createdTime;
        public final long expiryTime;
        public long ceasefireDurationMillis; // for ceasefire only

        public WarProposal(ProposalType t, ForgeTeam prop, ForgeTeam tgt, long duration) {
            type = t;
            proposer = prop;
            target = tgt;
            createdTime = System.currentTimeMillis();
            expiryTime = createdTime + duration;
            ceasefireDurationMillis = 0;
        }
    }

    // Key: "<proposerTeamId>:<targetTeamId>" to track pending proposals
    private final Map<String, WarProposal> pending = new HashMap<>();
    // Track paused wars: key is war identifier, value is time remaining in millis
    private final Map<String, Long> pausedWars = new HashMap<>();

    private WarProposalManager() {}

    public void addProposal(ProposalType type, ForgeTeam proposer, ForgeTeam target, long durationMillis) {
        String key = proposer.getId() + ":" + target.getId();
        // Check if proposal already exists
        if (pending.containsKey(key)) {
            return; // Duplicate proposal blocked
        }
        WarProposal proposal = new WarProposal(type, proposer, target, durationMillis);
        pending.put(key, proposal);
    }

    public boolean canAddProposal(ForgeTeam proposer, ForgeTeam target) {
        String key = proposer.getId() + ":" + target.getId();
        WarProposal p = pending.get(key);
        if (p != null && System.currentTimeMillis() > p.expiryTime) {
            pending.remove(key);
            return true;
        }
        return p == null;
    }

    public void addProposal(WarProposal proposal) {
        String key = proposal.proposer.getId() + ":" + proposal.target.getId();
        // Check if proposal already exists
        if (pending.containsKey(key)) {
            return; // Duplicate proposal blocked
        }
        pending.put(key, proposal);
    }

    public WarProposal getProposal(ForgeTeam proposer, ForgeTeam target) {
        String key = proposer.getId() + ":" + target.getId();
        WarProposal p = pending.get(key);
        if (p != null && System.currentTimeMillis() > p.expiryTime) {
            pending.remove(key);
            return null;
        }
        return p;
    }

    public void removeProposal(ForgeTeam proposer, ForgeTeam target) {
        String key = proposer.getId() + ":" + target.getId();
        pending.remove(key);
    }

    public void pauseWar(ForgeTeam teamA, ForgeTeam teamB, long remainingMillis) {
        String key = teamA.getId() + ":" + teamB.getId();
        pausedWars.put(key, remainingMillis);
    }

    public Long getPausedWarTime(ForgeTeam teamA, ForgeTeam teamB) {
        String key = teamA.getId() + ":" + teamB.getId();
        return pausedWars.get(key);
    }

    public void removePausedWar(ForgeTeam teamA, ForgeTeam teamB) {
        String key = teamA.getId() + ":" + teamB.getId();
        pausedWars.remove(key);
    }

    public boolean isWarPaused(ForgeTeam teamA, ForgeTeam teamB) {
        return getPausedWarTime(teamA, teamB) != null;
    }
}
