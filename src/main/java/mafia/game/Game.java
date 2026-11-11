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
        for (int i = 0; i < participants.size(); i++) assignments.put(participants.get(i), roles.get(i));
        return assignments;
    }

    public synchronized MessageEmbed getLobbyEmbed() {
        return new EmbedBuilder()
                .setTitle("🎮 마피아 게임이 생성되었습니다!")
                .setDescription("현재 참가자: " + players.size() + " / " + MAX_PLAYERS)
                .setColor(0x5865F2)
                .build();
    }
}
