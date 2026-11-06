package mafia.bot;

import mafia.game.Game;
import mafia.game.GameManager;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;

public final class CommandManager extends ListenerAdapter {
    private final GameManager gameManager;

    public CommandManager(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("게임생성")) {
            return;
        }

        if (event.getGuild() == null || !(event.getChannel() instanceof TextChannel)) {
            event.replyEmbeds(new EmbedBuilder()
                            .setTitle("게임 생성 실패")
                            .setDescription("서버의 텍스트 채널에서만 게임을 생성할 수 있습니다.")
                            .setColor(0xED4245)
                            .build())
                    .setEphemeral(true)
                    .queue();
            return;
        }

        Game game = gameManager.createGameIfNoLobby(event.getGuild().getId(), event.getChannel().getId());
        if (game == null) {
            event.replyEmbeds(new EmbedBuilder()
                            .setTitle("게임 생성 실패")
                            .setDescription("게임이 이미 생성되었습니다.")
                            .setColor(0xED4245)
                            .build())
                    .setEphemeral(true)
                    .queue();
            return;
        }

        event.replyEmbeds(game.getLobbyEmbed())
                .setComponents(ActionRow.of(Button.success(ButtonManager.joinButtonId(game.getId()), "참가하기")))
                .queue(hook -> hook.retrieveOriginal().queue(message -> game.setLobbyMessageId(message.getId())));
    }
}
