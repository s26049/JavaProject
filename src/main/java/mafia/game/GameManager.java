package mafia.game;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class GameManager {
    private final Map<String, Game> games = new ConcurrentHashMap<>();

    public synchronized Game createGameIfNoLobby(String guildId, String channelId) {
        boolean lobbyExists = games.values().stream()
                .anyMatch(game -> game.getGuildId().equals(guildId));
        if (lobbyExists) {
            return null;
        }

        Game game = new Game(guildId, channelId);
        games.put(game.getId(), game);
        return game;
    }

    public Game getGame(String gameId) {
        return games.get(gameId);
    }
}
