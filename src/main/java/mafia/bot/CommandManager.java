package mafia.bot;

import mafia.game.Game;
import mafia.game.GameManager;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
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
            dismiss(event);
            return;
        }

        TextChannel channel = (TextChannel) event.getChannel();
        if (!event.getGuild().getSelfMember().hasPermission(channel, Permission.MANAGE_PERMISSIONS)) {
            dismiss(event);
            return;
        }

        Game game = gameManager.createGameIfNoLobby(event.getGuild().getId(), event.getChannel().getId());
        if (game == null) {
            dismiss(event);
            return;
        }

        event.replyEmbeds(game.getLobbyEmbed())
                .setComponents(ActionRow.of(Button.success(ButtonManager.joinButtonId(game.getId()), "참가하기")))
                .queue(hook -> hook.retrieveOriginal().queue(message -> game.setLobbyMessageId(message.getId())));
    }

    private void dismiss(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue(
                hook -> hook.deleteOriginal().queue(ignored -> { }, ignored -> { }),
                ignored -> { });
    }
}
