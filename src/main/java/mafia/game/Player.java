package mafia.game;

public final class Player {
    private final String id;
    private final String nickname;

    public Player(String id, String nickname) {
        this.id = id;
        this.nickname = nickname;
    }

    public String getId() {
        return id;
    }

    public String getNickname() {
        return nickname;
    }
}
