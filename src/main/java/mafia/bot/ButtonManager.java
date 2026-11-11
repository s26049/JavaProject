package mafia.bot;

import mafia.game.Game;
import mafia.game.GameManager;
import mafia.game.Player;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class ButtonManager extends ListenerAdapter {
    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "mafia-countdown");
        thread.setDaemon(true);
        return thread;
    });
    private static final String JOIN_PREFIX = "mafia:join:";
    private static final String CANCEL_PREFIX = "mafia:cancel:";

    private final GameManager gameManager;

    public ButtonManager(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    static String joinButtonId(String gameId) {
        return JOIN_PREFIX + gameId;
    }

    private static String cancelButtonId(String gameId) {
        return CANCEL_PREFIX + gameId;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        boolean joining = componentId.startsWith(JOIN_PREFIX);
        boolean cancelling = componentId.startsWith(CANCEL_PREFIX);
        if (!joining && !cancelling) {
            return;
        }

        String gameId = componentId.substring((joining ? JOIN_PREFIX : CANCEL_PREFIX).length());
        Game game = gameManager.getGame(gameId);
        if (game == null
                || event.getGuild() == null
                || !game.getGuildId().equals(event.getGuild().getId())
                || !game.getChannelId().equals(event.getChannel().getId())) {
            replyEphemeral(event, "게임 참가 실패", "이 게임 대기실은 더 이상 사용할 수 없습니다.", 0xED4245);
            return;
        }

        if (joining) {
            join(event, game);
        } else {
            cancel(event, game);
        }
    }

    private void join(ButtonInteractionEvent event, Game game) {
        if (game.getLobbyMessageId() == null) {
            game.setLobbyMessageId(event.getMessage().getId());
        }

        Player player = new Player(event.getUser().getId(), event.getUser().getName());
        if (!game.addPlayer(player)) {
            String message = game.hasPlayer(player.getId())
                    ? "이미 참가한 대기실입니다."
                    : "대기실 참가에 실패했습니다.";
            replyEphemeral(event, "게임 참가 실패", message, 0xED4245);
            return;
        }

        event.replyEmbeds(new EmbedBuilder()
                        .setTitle("게임 참가 안내")
                        .setDescription("✅ 참가 완료!")
                        .setColor(0x57F287)
                        .build())
                .setEphemeral(true)
                .setComponents(ActionRow.of(Button.danger(cancelButtonId(game.getId()), "취소하기")))
                .queue();
        updateLobby(event, game);
        if (game.isFull()) startCountdown(event, game);
    }

    private void cancel(ButtonInteractionEvent event, Game game) {
        boolean wasFull = game.isFull();
        if (!game.removePlayer(event.getUser().getId())) {
            replyEphemeral(event, "게임 참가 실패", "참가 중인 대기실이 아닙니다.", 0xED4245);
            return;
        }

        replyEphemeral(event, "게임 참가 안내", "✅ 취소 완료!", 0x57F287);
        if (wasFull) sendAnnouncement(event, game, "게임 시작 안내", "참가자가 부족합니다.");
        updateLobby(event, game);
        if (game.isFull()) startCountdown(event, game);
    }

    private void startCountdown(ButtonInteractionEvent event, Game game) {
        int version = game.beginCountdown();
        if (version < 0) return;
        sendAnnouncement(event, game, "게임 시작 안내", "⏳5초 후 게임이 시작됩니다!");
        TIMER.schedule(() -> countdownTick(event, game, version), 1, TimeUnit.SECONDS);
    }

    private void countdownTick(ButtonInteractionEvent event, Game game, int version) {
        if (game.isStarted()) return;
        int seconds = game.nextCountdownSecond(version);
        if (seconds < 0) return;
        if (!game.isFull()) {
            sendAnnouncement(event, game, "게임 시작 안내", "참가자가 부족합니다.");
            return;
        }
        if (seconds > 0) {
            sendAnnouncement(event, game, "게임 시작 안내", "⏳" + seconds + "초 후 게임이 시작됩니다!");
            TIMER.schedule(() -> countdownTick(event, game, version), 1, TimeUnit.SECONDS);
        } else if (seconds == 0) {
            Map<Player, String> roles = game.startAndAssignRoles();
            if (roles == null) return;
            for (Map.Entry<Player, String> assignment : roles.entrySet()) {
                event.getJDA().retrieveUserById(assignment.getKey().getId()).queue(user ->
                        user.openPrivateChannel().queue(channel -> channel.sendMessageEmbeds(new EmbedBuilder()
                                        .setTitle("직업 배정 안내")
                                        .setDescription("당신의 직업은 `" + assignment.getValue() + "` 입니다!")
                                        .setColor(assignment.getValue().equals("마피아") ? 0xED4245 : 0x5865F2)
                                        .build()).queue(), error -> System.err.println("직업 안내 DM 실패: " + error.getMessage())),
                        error -> System.err.println("직업 안내 사용자 조회 실패: " + error.getMessage()));
            }
            sendAnnouncement(event, game, "1번째 밤", "수상한 움직임에 주목하세요.");
        }
    }

    private void sendAnnouncement(ButtonInteractionEvent event, Game game, String title, String message) {
        event.getJDA().getTextChannelById(game.getChannelId()).sendMessageEmbeds(new EmbedBuilder()
                        .setTitle(title).setDescription(message).setColor(0x5865F2).build())
                .queue(null, error -> System.err.println("게임 안내 전송 실패: " + error.getMessage()));
    }

    private void updateLobby(ButtonInteractionEvent event, Game game) {
        event.getJDA().getTextChannelById(game.getChannelId())
                .editMessageById(game.getLobbyMessageId(), new MessageEditBuilder()
                        .setEmbeds(game.getLobbyEmbed())
                        .setComponents(ActionRow.of(Button.success(joinButtonId(game.getId()), "참가하기")))
                        .build())
                .queue(null, error -> System.err.println("대기실 메시지 갱신 실패: " + error.getMessage()));
    }

    private void replyEphemeral(ButtonInteractionEvent event, String title, String message, int color) {
        event.replyEmbeds(new EmbedBuilder()
                        .setTitle(title)
                        .setDescription(message)
                        .setColor(color)
                        .build())
                .setEphemeral(true)
                .queue();
    }
}
