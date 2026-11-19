package mafia.role;

public enum Faction {
    MAFIA("마피아팀"),
    CITIZEN("시민팀");

    private final String displayName;

    Faction(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}
