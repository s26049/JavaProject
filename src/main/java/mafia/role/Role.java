package mafia.role;

public abstract class Role {
    private final String name;
    private final Faction faction;

    protected Role(String name, Faction faction) {
        this.name = name;
        this.faction = faction;
    }

    public final String getName() { return name; }
    public final Faction getFaction() { return faction; }
    public boolean canTarget(Role target) { return true; }
}
