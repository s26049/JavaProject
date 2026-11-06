package mafia.bot;

import mafia.game.GameManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

public final class DiscordManager {
    private final GameManager gameManager;

    public DiscordManager(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    public JDA start(String token) {
        return JDABuilder.createDefault(token)
                .addEventListeners(
                        new CommandManager(gameManager),
                        new ButtonManager(gameManager),
                        new ReadyListener())
                .build();
    }

    private static final class ReadyListener extends ListenerAdapter {
        @Override
        public void onReady(ReadyEvent event) {
            for (Guild guild : event.getJDA().getGuilds()) {
                guild.upsertCommand(Commands.slash("게임생성", "마피아 게임 대기실을 생성합니다."))
                        .queue(
                                command -> System.out.println("슬래시 명령어 등록 완료: " + guild.getName()),
                                error -> System.err.println("슬래시 명령어 등록 실패 (" + guild.getName() + "): " + error.getMessage()));
            }
        }
    }
}
