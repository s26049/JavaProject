package mafia.game;

public final class Player {
    private final String id;
    private final String nickname;
    private String role;
    private boolean alive = true;

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

    public synchronized String getRole() { return role; }
    public synchronized void setRole(String role) { this.role = role; }
    public synchronized boolean isAlive() { return alive; }
    public synchronized void kill() { alive = false; }
}
