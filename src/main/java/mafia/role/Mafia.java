package mafia.role;

import mafia.action.NightAction;
import mafia.action.NightAbility;

public final class Mafia extends Role implements NightAction {
    public Mafia() { super("마피아", Team.MAFIA); }
    @Override public String getPrompt() { return "살해할 사람을 선택해주세요."; }
    @Override public NightAbility getAbility() { return NightAbility.MAFIA; }
    @Override public boolean canTarget(Role target) { return !(target instanceof Mafia); }
}
