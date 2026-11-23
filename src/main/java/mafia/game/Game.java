package mafia.game;

import mafia.role.Citizen;
import mafia.action.NightAbility;
import mafia.action.NightAction;
import mafia.role.Doctor;
import mafia.role.Team;
import mafia.role.Mafia;
import mafia.role.Police;
import mafia.role.Role;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Game {
    private static final int MAX_PLAYERS = 8;

    private final String id = UUID.randomUUID().toString();
    private final String guildId;
    private final String channelId;
    private final Map<String, Player> players = new LinkedHashMap<>();
    private final VoteManager voteManager = new VoteManager();
    private String lobbyMessageId;
    private GameState state = GameState.WAITING;
    private int countdownVersion;
    private int nightNumber;
    private int nightVersion;
    private int phaseVersion;
    private String mafiaTargetId;
    private String doctorTargetId;
    private boolean policeUsed;
    private String trialCandidateId;
    private String winner;
    private String recentlyKilledId;

    public Game(String guildId, String channelId) {
        this.guildId = guildId;
        this.channelId = channelId;
    }

    public String getId() { return id; }
    public String getGuildId() { return guildId; }
    public String getChannelId() { return channelId; }

    public synchronized void setLobbyMessageId(String lobbyMessageId) { this.lobbyMessageId = lobbyMessageId; }
    public synchronized String getLobbyMessageId() { return lobbyMessageId; }

    public synchronized boolean addPlayer(Player player) {
        if (state != GameState.WAITING || players.size() >= MAX_PLAYERS || players.containsKey(player.getId())) return false;
        players.put(player.getId(), player);
        return true;
    }

    public synchronized boolean removePlayer(String playerId) {
        if (state != GameState.WAITING && state != GameState.COUNTDOWN) return false;
        boolean removed = players.remove(playerId) != null;
        if (removed && players.size() < MAX_PLAYERS) {
            state = GameState.WAITING;
            countdownVersion++;
        }
        return removed;
    }

    public synchronized boolean hasPlayer(String playerId) { return players.containsKey(playerId); }
    public synchronized boolean isFull() { return players.size() == MAX_PLAYERS; }
    public synchronized boolean isStarted() { return state != GameState.WAITING && state != GameState.COUNTDOWN; }
    public synchronized boolean isNightOpen() { return state == GameState.NIGHT; }
    public synchronized boolean isFinished() { return state == GameState.FINISHED; }

    private long countdownStartedAt;

    public synchronized int nextCountdownSecond(int version) {
        if (state != GameState.COUNTDOWN || version != countdownVersion || players.size() != MAX_PLAYERS) return -1;
        return 5 - (int) Math.min(5, Math.max(0, System.currentTimeMillis() - countdownStartedAt) / 1000);
    }

    public synchronized int beginCountdown() {
        if (state != GameState.WAITING || players.size() != MAX_PLAYERS) return -1;
        state = GameState.COUNTDOWN;
        countdownStartedAt = System.currentTimeMillis();
        return ++countdownVersion;
    }

    public synchronized Map<Player, Role> startAndAssignRoles() {
        if (state != GameState.COUNTDOWN || players.size() != MAX_PLAYERS) return null;
        List<Role> roles = new ArrayList<>(List.of(new Mafia(), new Mafia(), new Police(), new Doctor(),
                new Citizen(), new Citizen(), new Citizen(), new Citizen()));
        Collections.shuffle(roles);
        List<Player> participants = new ArrayList<>(players.values());
        Map<Player, Role> assignments = new LinkedHashMap<>();
        for (int i = 0; i < participants.size(); i++) {
            participants.get(i).setRole(roles.get(i));
            assignments.put(participants.get(i), roles.get(i));
        }
        state = GameState.DAY;
        return assignments;
    }

    public synchronized int beginNight() {
        if (state != GameState.DAY) return -1;
        state = GameState.NIGHT;
        nightVersion++;
        phaseVersion++;
        mafiaTargetId = null;
        doctorTargetId = null;
        policeUsed = false;
        recentlyKilledId = null;
        nightNumber++;
        return nightVersion;
    }

    public synchronized int getNightNumber() { return nightNumber; }
    public synchronized String getRecentlyKilledId() { return recentlyKilledId; }
    public synchronized String getMafiaTargetId() { return mafiaTargetId; }
    public synchronized List<Player> getPlayers() { return new ArrayList<>(players.values()); }
    public synchronized List<Player> getAlivePlayers() {
        return players.values().stream().filter(Player::isAlive).collect(java.util.stream.Collectors.toList());
    }
    public synchronized int getAliveCount() { return (int) players.values().stream().filter(Player::isAlive).count(); }

    public synchronized boolean selectNightTarget(String actorId, String targetId, NightAbility ability) {
        Player actor = players.get(actorId);
        Player target = players.get(targetId);
        if (state != GameState.NIGHT || actor == null || target == null || !actor.isAlive() || !target.isAlive()) return false;
        Role role = actor.getRole();
        if (role == null) return false;
        if (!(role instanceof NightAction) || ((NightAction) role).getAbility() != ability) return false;
        switch (ability) {
            case MAFIA:
                if (!(role instanceof Mafia) || actorId.equals(targetId) || !role.canTarget(target.getRole())) return false;
                mafiaTargetId = targetId;
                return true;
            case DOCTOR:
                if (!(role instanceof Doctor)) return false;
                doctorTargetId = targetId;
                return true;
            case POLICE:
                if (!(role instanceof Police) || policeUsed) return false;
                policeUsed = true;
                return true;
            default:
                return false;
        }
    }

    public synchronized boolean isAlive(String playerId) {
        Player player = players.get(playerId);
        return player != null && player.isAlive();
    }
    public synchronized String investigate(String targetId) {
        Player target = players.get(targetId);
        if (target == null || target.getRole() == null) return null;
        return target.getRole().getTeam() == Team.MAFIA ? "마피아 입니다." : "마피아가 아닙니다.";
    }

    public synchronized String finishNight(int version) {
        if (state != GameState.NIGHT || version != nightVersion) return null;
        state = GameState.DAY;
        nightVersion++;
        if (mafiaTargetId == null || !players.containsKey(mafiaTargetId) || !players.get(mafiaTargetId).isAlive()) {
            return "조용하게 밤이 넘어갔습니다.";
        }
        Player victim = players.get(mafiaTargetId);
        if (mafiaTargetId.equals(doctorTargetId)) return "`" + victim.getNickname() + "`님이 치료를 받고 살아났습니다!";
        victim.kill();
        recentlyKilledId = victim.getId();
        return "`" + victim.getNickname() + "`님이 살해당하였습니다.";
    }

    public synchronized int beginVote() {
        if (state != GameState.DAY) return -1;
        voteManager.beginVote();
        state = GameState.VOTING;
        return ++phaseVersion;
    }
    public synchronized boolean castVote(String voterId, String targetId) {
        return state == GameState.VOTING && voteManager.castVote(voterId, targetId, getPlayers());
    }
    public synchronized VoteManager.VoteResult finishVote(int version) {
        if (state != GameState.VOTING || version != phaseVersion) return null;
        VoteManager.VoteResult result = voteManager.finishVote();
        state = result.getCandidateId() == null ? GameState.DAY : GameState.VOTING;
        return result;
    }

    public synchronized int beginTrial(String candidateId) {
        if (state != GameState.VOTING || !isAlive(candidateId)) return -1;
        trialCandidateId = candidateId;
        voteManager.beginTrial();
        state = GameState.DEFENSE;
        return ++phaseVersion;
    }
    public synchronized int beginTrialVote() {
        if (state != GameState.DEFENSE) return -1;
        state = GameState.TRIAL;
        return ++phaseVersion;
    }
    public synchronized boolean castTrialVote(String voterId, boolean approve) {
        return state == GameState.TRIAL && voteManager.castTrialVote(voterId, approve, getPlayers());
    }
    public synchronized Boolean finishTrial(int version) {
        if (state != GameState.TRIAL || version != phaseVersion) return null;
        boolean executed = voteManager.finishTrial(getPlayers());
        state = GameState.DAY;
        if (executed) {
            Player candidate = players.get(trialCandidateId);
            if (candidate != null) {
                candidate.kill();
                recentlyKilledId = trialCandidateId;
            }
        }
        return executed;
    }

    public synchronized String getPlayerName(String playerId) {
        Player player = players.get(playerId);
        return player == null ? "알 수 없는 참가자" : player.getNickname();
    }
    public synchronized String checkWinner() {
        if (state == GameState.FINISHED) return winner;
        long mafia = players.values().stream().filter(Player::isAlive)
                .filter(player -> player.getRole() != null && player.getRole().getTeam() == Team.MAFIA).count();
        long citizens = getAliveCount() - mafia;
        if (mafia == 0) winner = "시민팀 승리!";
        else if (mafia >= citizens) winner = "마피아팀 승리!";
        if (winner != null) state = GameState.FINISHED;
        return winner;
    }

    public synchronized MessageEmbed getLobbyEmbed() {
        return new EmbedBuilder().setTitle("🎮 마피아 게임이 생성되었습니다!")
                .setDescription("현재 참가자: " + players.size() + " / " + MAX_PLAYERS)
                .setColor(0x5865F2).build();
    }
}
