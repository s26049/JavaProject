package mafia.game;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

public final class Game {
    private static final int MAX_PLAYERS = 8;

    private final String id;
    private final String guildId;
    private final String channelId;
    private final Map<String, Player> players = new LinkedHashMap<>();
    private String lobbyMessageId;

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
        if (players.size() >= MAX_PLAYERS || players.containsKey(player.getId())) {
            return false;
        }
        players.put(player.getId(), player);
        return true;
    }

    public synchronized boolean removePlayer(String playerId) {
        return players.remove(playerId) != null;
    }

    public synchronized boolean hasPlayer(String playerId) {
        return players.containsKey(playerId);
    }

    public synchronized MessageEmbed getLobbyEmbed() {
        return new EmbedBuilder()
                .setTitle("🎮 마피아 게임이 생성되었습니다!")
                .setDescription("현재 참가자: " + players.size() + " / " + MAX_PLAYERS)
                .setColor(0x5865F2)
                .build();
    }
}
