package mafia.role;

import mafia.action.NightAction;
import mafia.action.NightAbility;

public final class Police extends Role implements NightAction {
    public Police() { super("경찰", Team.CITIZEN); }
    @Override public String getPrompt() { return "조사할 사람을 선택해주세요."; }
    @Override public NightAbility getAbility() { return NightAbility.POLICE_SEARCH; }
}
