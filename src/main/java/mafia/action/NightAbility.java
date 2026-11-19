package mafia.action;

public enum NightAbility {
    MAFIA_EXECUTION("mafia"),
    POLICE_SEARCH("police"),
    DOCTOR_HEAL("doctor");

    private final String componentId;

    NightAbility(String componentId) { this.componentId = componentId; }
    public String getComponentId() { return componentId; }

    public static NightAbility fromComponentId(String componentId) {
        for (NightAbility ability : values()) {
            if (ability.componentId.equals(componentId)) return ability;
        }
        return null;
    }
}
