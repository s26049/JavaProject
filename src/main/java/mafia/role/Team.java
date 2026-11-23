package mafia.role;

public enum Team {
    MAFIA("마피아팀"),
    CITIZEN("시민팀");

    private final String displayName;

    Team(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}
