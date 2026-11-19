package mafia.game;

import mafia.role.Role;

public final class Player {
    private final String id;
    private final String nickname;
    private Role role;
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

    public synchronized Role getRole() { return role; }
    public synchronized void setRole(Role role) { this.role = role; }
    public synchronized String getRoleName() { return role == null ? null : role.getName(); }
    public synchronized boolean isAlive() { return alive; }
    public synchronized void kill() { alive = false; }
}
