package mafia.game;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

public final class Game {
    private static final int MAX_PLAYERS = 8;

    private final String id;
    private final String guildId;
    private final String channelId;
    private final Map<String, Player> players = new LinkedHashMap<>();
    private String lobbyMessageId;
    private int countdownSeconds;
    private int countdownVersion;
    private boolean started;
    private boolean nightOpen;
    private int nightNumber;
    private int nightVersion;
    private String mafiaTargetId;
    private String doctorTargetId;
    private boolean policeUsed;

    public Game(String guildId, String channelId) {
        this.id = UUID.randomUUID().toString();
        this.guildId = guildId;
        this.channelId = channelId;
    }

    public String getId() {
        return id;
    }

    public String getGuildId() {
        return guildId;
    }

    public String getChannelId() {
        return channelId;
    }

    public synchronized void setLobbyMessageId(String lobbyMessageId) {
        this.lobbyMessageId = lobbyMessageId;
    }

    public synchronized String getLobbyMessageId() {
        return lobbyMessageId;
    }

    public synchronized boolean addPlayer(Player player) {
        if (started || players.size() >= MAX_PLAYERS || players.containsKey(player.getId())) {
            return false;
        }
        players.put(player.getId(), player);
        return true;
    }

    public synchronized boolean removePlayer(String playerId) {
        boolean removed = players.remove(playerId) != null;
        if (removed && !started && players.size() < MAX_PLAYERS) { countdownSeconds = 0; countdownVersion++; }
        return removed;
    }

    public synchronized boolean hasPlayer(String playerId) {
        return players.containsKey(playerId);
    }

    public synchronized boolean isFull() { return players.size() == MAX_PLAYERS; }
    public synchronized boolean isStarted() { return started; }
    public synchronized int nextCountdownSecond(int version) {
        if (version != countdownVersion) return -1;
        if (started || players.size() != MAX_PLAYERS) { countdownSeconds = 0; return -1; }
        countdownSeconds--;
        return countdownSeconds;
    }
    public synchronized int beginCountdown() {
        if (started || players.size() != MAX_PLAYERS) return -1;
        countdownSeconds = 5;
        return ++countdownVersion;
    }

    public synchronized Map<Player, String> startAndAssignRoles() {
        if (started || players.size() != MAX_PLAYERS) return null;
        started = true;
        List<String> roles = new ArrayList<>(List.of("마피아", "마피아", "경찰", "의사", "시민", "시민", "시민", "시민"));
        Collections.shuffle(roles);
        List<Player> participants = new ArrayList<>(players.values());
        Map<Player, String> assignments = new LinkedHashMap<>();
        for (int i = 0; i < participants.size(); i++) {
            participants.get(i).setRole(roles.get(i));
            assignments.put(participants.get(i), roles.get(i));
        }
        return assignments;
    }

    public synchronized int beginNight() {
        if (!started || nightOpen) return -1;
        nightOpen = true;
        nightVersion++;
        mafiaTargetId = null;
        doctorTargetId = null;
        policeUsed = false;
        nightNumber++;
        return nightVersion;
    }

    public synchronized int getNightVersion() { return nightVersion; }
    public synchronized int getNightNumber() { return nightNumber; }
    public synchronized boolean isNightOpen() { return nightOpen; }
    public synchronized List<Player> getPlayers() { return new ArrayList<>(players.values()); }

    public synchronized boolean selectNightTarget(String actorId, String targetId, String ability) {
        Player actor = players.get(actorId);
        Player target = players.get(targetId);
        if (!nightOpen || actor == null || target == null || !actor.isAlive() || !target.isAlive()) return false;
        switch (ability) {
            case "mafia":
                if (!"마피아".equals(actor.getRole()) || actorId.equals(targetId)) return false;
                mafiaTargetId = targetId;
                return true;
            case "doctor":
                if (!"의사".equals(actor.getRole())) return false;
                doctorTargetId = targetId;
                return true;
            case "police":
                if (!"경찰".equals(actor.getRole()) || policeUsed) return false;
                policeUsed = true;
                return true;
            default:
                return false;
        }
    }

    public synchronized String getRole(String playerId) {
        Player player = players.get(playerId);
        return player == null ? null : player.getRole();
    }

    public synchronized boolean isAlive(String playerId) {
        Player player = players.get(playerId);
        return player != null && player.isAlive();
    }

    public synchronized String investigate(String targetId) {
        Player target = players.get(targetId);
        if (target == null) return null;
        return "마피아".equals(target.getRole()) ? "마피아 입니다." : "마피아가 아닙니다.";
    }

    public synchronized String finishNight(int version) {
        if (!nightOpen || version != nightVersion) return null;
        nightOpen = false;
        nightVersion++;
        if (mafiaTargetId == null || !players.containsKey(mafiaTargetId) || !players.get(mafiaTargetId).isAlive()) {
            return "조용하게 밤이 넘어갔습니다.";
        }
        Player victim = players.get(mafiaTargetId);
        if (mafiaTargetId.equals(doctorTargetId)) return "`" + victim.getNickname() + "`님이 치료를 받고 살아났습니다!";
        victim.kill();
        return "`" + victim.getNickname() + "`님이 살해당하였습니다.";
    }

    public synchronized MessageEmbed getLobbyEmbed() {
        return new EmbedBuilder()
                .setTitle("🎮 마피아 게임이 생성되었습니다!")
                .setDescription("현재 참가자: " + players.size() + " / " + MAX_PLAYERS)
                .setColor(0x5865F2)
                .build();
    }
}
