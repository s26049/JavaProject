package mafia.role;

public abstract class Role {
    private final String name;
    private final Team team;

    protected Role(String name, Team team) {
        this.name = name;
        this.team = team;
    }

    public final String getName() { return name; }
    public final Team getTeam() { return team; }
    public boolean canTarget(Role target) { return true; }
}
