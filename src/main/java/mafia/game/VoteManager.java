package mafia.game;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class VoteManager {
    private final Map<String, String> votes = new HashMap<>();
    private final Map<String, Boolean> trialVotes = new HashMap<>();

    public synchronized void beginVote() { votes.clear(); }

    public synchronized boolean castVote(String voterId, String targetId, List<Player> players) {
        if (!isAlive(voterId, players) || !isAlive(targetId, players) || votes.containsKey(voterId)) return false;
        votes.put(voterId, targetId);
        return true;
    }

    public synchronized VoteResult finishVote() {
        Map<String, Integer> counts = new HashMap<>();
        for (String targetId : votes.values()) counts.merge(targetId, 1, Integer::sum);
        int max = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<String> leaders = counts.entrySet().stream().filter(entry -> entry.getValue() == max)
                .map(Map.Entry::getKey).collect(Collectors.toList());
        return new VoteResult(counts, max > 0 && leaders.size() == 1 ? leaders.get(0) : null);
    }

    public synchronized void beginTrial() { trialVotes.clear(); }

    public synchronized boolean castTrialVote(String voterId, String candidateId, boolean approve, List<Player> players) {
        if (voterId.equals(candidateId) || !isAlive(voterId, players) || trialVotes.containsKey(voterId)) return false;
        trialVotes.put(voterId, approve);
        return true;
    }

    public synchronized boolean finishTrial() {
        long approvals = trialVotes.values().stream().filter(Boolean::booleanValue).count();
        long rejections = trialVotes.size() - approvals;
        return approvals >= rejections;
    }

    private boolean isAlive(String playerId, List<Player> players) {
        return players.stream().anyMatch(player -> player.getId().equals(playerId) && player.isAlive());
    }

    public static final class VoteResult {
        private final Map<String, Integer> counts;
        private final String candidateId;

        private VoteResult(Map<String, Integer> counts, String candidateId) {
            this.counts = new HashMap<>(counts);
            this.candidateId = candidateId;
        }

        public Map<String, Integer> getCounts() { return new HashMap<>(counts); }
        public String getCandidateId() { return candidateId; }
    }
}
