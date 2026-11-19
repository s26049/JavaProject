package mafia.role;

import mafia.action.NightAction;
import mafia.action.NightAbility;

public final class Doctor extends Role implements NightAction {
    public Doctor() { super("의사", Faction.CITIZEN); }
    @Override public String getPrompt() { return "치료할 사람을 선택해주세요."; }
    @Override public NightAbility getAbility() { return NightAbility.DOCTOR_HEAL; }
}
