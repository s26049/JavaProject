package mafia;

import mafia.bot.DiscordManager;
import mafia.game.GameManager;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        String token = System.getenv("DISCORD_BOT_TOKEN");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("DISCORD_BOT_TOKEN 환경 변수에 디스코드 봇 토큰을 설정해주세요.");
        }

        new DiscordManager(new GameManager()).start(token);
    }
}
