package mafia.bot;

import mafia.game.Game;
import mafia.game.GameManager;
import mafia.game.GameTimer;
import mafia.game.Player;
import mafia.game.VoteManager;
import mafia.action.NightAction;
import mafia.action.NightAbility;
import mafia.role.Faction;
import mafia.role.Mafia;
import mafia.role.Role;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class ButtonManager extends ListenerAdapter {
    private final GameTimer gameTimer = new GameTimer();
    private static final String JOIN_PREFIX = "mafia:join:";
    private static final String CANCEL_PREFIX = "mafia:cancel:";
    private static final String NIGHT_PREFIX = "mafia:night:";
    private static final String VOTE_PREFIX = "mafia:vote:";
    private static final String TRIAL_PREFIX = "mafia:trial:";
    private final Map<String, Map<String, InteractionHook>> participantHooks = new ConcurrentHashMap<>();

    private final GameManager gameManager;
    private final ChannelSpeechPermissionManager speechPermissions = new ChannelSpeechPermissionManager();

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
        if (componentId.startsWith(NIGHT_PREFIX)) {
            handleNightAction(event, componentId);
            return;
        }
        if (componentId.startsWith(VOTE_PREFIX)) {
            handleVote(event, componentId);
            return;
        }
        if (componentId.startsWith(TRIAL_PREFIX)) {
            handleTrialVote(event, componentId);
            return;
        }
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
                .queue(hook -> participantHooks.computeIfAbsent(game.getId(), ignored -> new ConcurrentHashMap<>())
                        .put(player.getId(), hook));
        updateLobby(event, game);
        if (game.isFull()) startCountdown(event, game);
    }

    private void cancel(ButtonInteractionEvent event, Game game) {
        if (game.isStarted()) {
            replyEphemeral(event, "게임 참가 취소 실패", "게임이 시작되어 참가를 취소할 수 없습니다.", 0xED4245);
            return;
        }
        boolean wasFull = game.isFull();
        if (!game.removePlayer(event.getUser().getId())) {
            replyEphemeral(event, "게임 참가 실패", "참가 중인 대기실이 아닙니다.", 0xED4245);
            return;
        }

        replyEphemeral(event, "게임 참가 안내", "✅ 취소 완료!", 0x57F287);
        Map<String, InteractionHook> hooks = participantHooks.get(game.getId());
        if (hooks != null) hooks.remove(event.getUser().getId());
        if (wasFull) sendAnnouncement(event, game, "게임 시작 안내", "참가자가 부족합니다.");
        updateLobby(event, game);
        if (game.isFull()) startCountdown(event, game);
    }

    private void startCountdown(ButtonInteractionEvent event, Game game) {
        int version = game.beginCountdown();
        if (version < 0) return;
        sendAnnouncement(event, game, "게임 시작 안내", "⏳5초 후 게임이 시작됩니다!");
        gameTimer.schedule(() -> countdownTick(event, game, version), 1, TimeUnit.SECONDS);
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
            gameTimer.schedule(() -> countdownTick(event, game, version), 1, TimeUnit.SECONDS);
        } else if (seconds == 0) {
            Map<Player, Role> roles = game.startAndAssignRoles();
            if (roles == null) return;
            for (Map.Entry<Player, Role> assignment : roles.entrySet()) sendRoleNotice(game, assignment, roles);
            startNight(event, game);
        }
    }

    private void sendRoleNotice(Game game, Map.Entry<Player, Role> assignment, Map<Player, Role> roles) {
        Role role = assignment.getValue();
        String description = "당신의 직업은 `" + role.getName() + "` 입니다!";
        if (role instanceof Mafia) {
            String partners = roles.entrySet().stream().filter(entry -> entry.getValue() instanceof Mafia
                    && !entry.getKey().equals(assignment.getKey())).map(entry -> entry.getKey().getNickname())
                    .findFirst().orElse("알 수 없음");
            description += "\n당신의 동료 마피아는 `" + partners + "`님입니다.";
        }
        int color = role.getFaction() == Faction.MAFIA ? 0xED4245 : 0x5865F2;
        sendPrivateEmbed(game, assignment.getKey().getId(), new EmbedBuilder()
                .setTitle("직업 배정 안내").setDescription(description).setColor(color).build());
    }

    private void startNight(ButtonInteractionEvent event, Game game) {
        int version = game.beginNight();
        if (version < 0) return;
        int night = game.getNightNumber();
        sendAnnouncement(event, game, night + "번째 밤", "수상한 움직임에 주목하세요.");
        for (Player player : game.getPlayers()) {
            if (!player.isAlive()) continue;
            Role role = player.getRole();
            if (role instanceof NightAction) {
                NightAction action = (NightAction) role;
                sendNightPrompt(event, game, player, action.getAbility(), action.getPrompt(), night);
            }
        }
        gameTimer.schedule(() -> finishNight(event, game, version, night), 10, TimeUnit.SECONDS);
    }

    private void sendNightPrompt(ButtonInteractionEvent event, Game game, Player actor, NightAbility ability,
                                 String prompt, int night) {
        List<Button> buttons = new ArrayList<>();
        for (Player target : game.getPlayers()) {
            if (!target.isAlive() || (ability == NightAbility.MAFIA_EXECUTION && target.getRole() instanceof Mafia)) continue;
            buttons.add(Button.primary(NIGHT_PREFIX + ability.getComponentId() + ":" + game.getId() + ":" + target.getId(), target.getNickname()));
        }
        sendPrivateEmbed(game, actor.getId(), new EmbedBuilder()
                        .setTitle(night + "번째 밤").setDescription(prompt).setColor(0x5865F2).build(),
                buttonRows(buttons));
    }

    private void sendPrivateEmbed(Game game, String playerId, net.dv8tion.jda.api.entities.MessageEmbed embed) {
        sendPrivateEmbed(game, playerId, embed, java.util.Collections.emptyList());
    }

    private void sendPrivateEmbed(Game game, String playerId, net.dv8tion.jda.api.entities.MessageEmbed embed,
                                  List<net.dv8tion.jda.api.components.MessageTopLevelComponent> components) {
        Map<String, InteractionHook> hooks = participantHooks.get(game.getId());
        InteractionHook hook = hooks == null ? null : hooks.get(playerId);
        if (hook == null) {
            System.err.println("비공개 안내를 보낼 참가자 상호작용을 찾을 수 없습니다: " + playerId);
            return;
        }
        hook.sendMessageEmbeds(embed).setEphemeral(true).setComponents(components).queue(
                ignored -> { }, error -> System.err.println("채널 비공개 안내 전송 실패: " + error.getMessage()));
    }

    private void handleNightAction(ButtonInteractionEvent event, String componentId) {
        String[] parts = componentId.split(":", 5);
        if (parts.length != 5) return;
        NightAbility ability = NightAbility.fromComponentId(parts[2]);
        Game game = gameManager.getGame(parts[3]);
        String targetId = parts[4];
        if (ability == null || game == null || !game.isNightOpen() || !game.isAlive(event.getUser().getId())) {
            replyEphemeral(event, "능력 사용 실패", "이 밤에는 능력을 사용할 수 없습니다.", 0xED4245);
            return;
        }
        if (ability == NightAbility.POLICE_SEARCH) {
            if (!game.selectNightTarget(event.getUser().getId(), targetId, ability)) {
                replyEphemeral(event, "수색 실패", "이미 수색했거나 해당 대상을 조사할 수 없습니다.", 0xED4245);
                return;
            }
            String result = game.investigate(targetId);
            Player target = game.getPlayers().stream().filter(player -> player.getId().equals(targetId)).findFirst().orElseThrow();
            event.replyEmbeds(new EmbedBuilder().setTitle("수색 결과")
                    .setDescription("`" + target.getNickname() + "`님은 " + result).setColor(0x5865F2).build())
                    .setEphemeral(true).queue(hook -> rememberHook(game, event.getUser().getId(), hook));
            return;
        }
        if (!game.selectNightTarget(event.getUser().getId(), targetId, ability)) {
            replyEphemeral(event, "능력 사용 실패", "해당 대상을 선택할 수 없습니다.", 0xED4245);
            return;
        }
        replyEphemeral(event, game, "능력 사용 안내",
                ability == NightAbility.MAFIA_EXECUTION ? "처형 대상을 선택했습니다. 동료와 선택이 공유됩니다." : "치료 대상을 선택했습니다.", 0x57F287);
    }

    private void finishNight(ButtonInteractionEvent event, Game game, int version, int night) {
        String outcome = game.finishNight(version);
        if (outcome == null) return;
        Runnable announceMorning = () -> {
            String winner = game.checkWinner();
            if (winner != null) {
                announceGameEnd(event, game, winner);
                speechPermissions.restoreDeadPlayers(event.getJDA(), game);
                participantHooks.remove(game.getId());
            } else {
                event.getJDA().getTextChannelById(game.getChannelId())
                        .sendMessageEmbeds(new EmbedBuilder().setTitle(night + "번째 낮")
                                .setDescription(outcome + "\n대화를 통해 추리하세요.")
                                .setColor(0x5865F2).build())
                        .queue(message -> startDayTimer(event, game));
            }
        };
        String killedId = game.getRecentlyKilledId();
        if (killedId == null) announceMorning.run();
        else speechPermissions.silencePlayer(event.getJDA(), game, killedId, announceMorning);
    }

    private void startDayTimer(ButtonInteractionEvent event, Game game) {
        int remainingSeconds = game.getPlayers().size() * 10;
        int minutes = remainingSeconds / 60;
        int seconds = remainingSeconds % 60;
        String time = String.format("%02d:%02d", minutes, seconds);
        event.getJDA().getTextChannelById(game.getChannelId())
                .sendMessageEmbeds(new EmbedBuilder().setTitle("투표까지 남은 시간")
                        .setDescription("⌛" + time).setColor(0x5865F2).build())
                .queue(message -> {
                    if (remainingSeconds > 0) {
                        gameTimer.schedule(() -> updateDayTimer(event, game, message.getId(), remainingSeconds - 1),
                                1, TimeUnit.SECONDS);
                    }
                }, error -> System.err.println("낮 타이머 메시지 전송 실패: " + error.getMessage()));
    }

    private void updateDayTimer(ButtonInteractionEvent event, Game game, String timerMessageId, int remainingSeconds) {
        int minutes = remainingSeconds / 60;
        int seconds = remainingSeconds % 60;
        String time = String.format("%02d:%02d", minutes, seconds);
        event.getJDA().getTextChannelById(game.getChannelId())
                .editMessageEmbedsById(timerMessageId, new EmbedBuilder()
                        .setTitle("투표까지 남은 시간")
                        .setDescription("⌛" + time)
                        .setColor(0x5865F2)
                        .build())
                .queue(null, error -> System.err.println("낮 타이머 메시지 갱신 실패: " + error.getMessage()));
        if (remainingSeconds > 0) {
            gameTimer.schedule(() -> updateDayTimer(event, game, timerMessageId, remainingSeconds - 1),
                    1, TimeUnit.SECONDS);
        } else {
            startVote(event, game);
        }
    }

    private void startVote(ButtonInteractionEvent event, Game game) {
        int version = game.beginVote();
        if (version < 0) return;
        List<Button> buttons = new ArrayList<>();
        for (Player player : game.getAlivePlayers()) {
            buttons.add(Button.primary(VOTE_PREFIX + game.getId() + ":" + player.getId(), player.getNickname()));
        }
        TextChannel channel = event.getJDA().getTextChannelById(game.getChannelId());
        channel.sendMessageEmbeds(countdownEmbed("투표 시간", "의심되는 사람을 선택하세요.", 10))
                .setComponents(buttonRows(buttons))
                .queue(message -> gameTimer.schedule(() -> updateVoteTimer(event, game, message.getId(), version, 9),
                        1, TimeUnit.SECONDS),
                        error -> System.err.println("투표 안내 전송 실패: " + error.getMessage()));
    }

    private void updateVoteTimer(ButtonInteractionEvent event, Game game, String messageId, int version, int remaining) {
        if (game.isFinished()) return;
        TextChannel channel = event.getJDA().getTextChannelById(game.getChannelId());
        channel.editMessageEmbedsById(messageId, countdownEmbed("투표 시간", "의심되는 사람을 투표하세요.", remaining))
                .queue(null, error -> System.err.println("투표 타이머 갱신 실패: " + error.getMessage()));
        if (remaining > 0) {
            gameTimer.schedule(() -> updateVoteTimer(event, game, messageId, version, remaining - 1), 1, TimeUnit.SECONDS);
        } else {
            VoteManager.VoteResult result = game.finishVote(version);
            if (result != null) finishVote(event, game, result);
        }
    }

    private void finishVote(ButtonInteractionEvent event, Game game, VoteManager.VoteResult result) {
        StringBuilder body = new StringBuilder();
        result.getCounts().entrySet().stream()
                .sorted((left, right) -> Integer.compare(right.getValue(), left.getValue()))
                .forEach(entry -> body.append("`").append(game.getPlayerName(entry.getKey()))
                        .append("` — ").append(entry.getValue()).append("표\n"));
        if (body.length() == 0) body.append("\u200B");
        sendAnnouncement(event, game, "투표 결과", body.toString().trim());
        if (result.getCandidateId() == null) {
            sendAnnouncement(event, game, "투표 결과", "처형될 대상을 고르지 못했습니다.");
            continueAfterDay(event, game);
            return;
        }
        startDefense(event, game, result.getCandidateId());
    }

    private void startDefense(ButtonInteractionEvent event, Game game, String candidateId) {
        int version = game.beginTrial(candidateId);
        if (version < 0) return;
        speechPermissions.restrictDefense(event.getJDA(), game, candidateId,
                () -> sendDefenseTimer(event, game, candidateId, version));
    }

    private void sendDefenseTimer(ButtonInteractionEvent event, Game game, String candidateId, int version) {
        String candidate = game.getPlayerName(candidateId);
        TextChannel channel = event.getJDA().getTextChannelById(game.getChannelId());
        channel.sendMessageEmbeds(countdownEmbed(candidate, "최후의 반론", 10))
                .queue(message -> gameTimer.schedule(() -> updateDefenseTimer(event, game, message.getId(),
                                candidateId, version, 9), 1, TimeUnit.SECONDS),
                        error -> System.err.println("최후의 반론 안내 전송 실패: " + error.getMessage()));
    }

    private void updateDefenseTimer(ButtonInteractionEvent event, Game game, String messageId,
                                    String candidateId, int version, int remaining) {
        if (game.isFinished()) return;
        event.getJDA().getTextChannelById(game.getChannelId())
                .editMessageEmbedsById(messageId, countdownEmbed(game.getPlayerName(candidateId), "최후의 반론", remaining))
                .queue(null, error -> System.err.println("최후의 반론 타이머 갱신 실패: " + error.getMessage()));
        if (remaining > 0) {
            gameTimer.schedule(() -> updateDefenseTimer(event, game, messageId, candidateId, version, remaining - 1),
                    1, TimeUnit.SECONDS);
        } else {
            speechPermissions.restoreAfterDefense(event.getJDA(), game,
                    () -> startTrialVote(event, game, candidateId));
        }
    }

    private void startTrialVote(ButtonInteractionEvent event, Game game, String candidateId) {
        int version = game.beginTrialVote();
        if (version < 0) return;
        List<Button> buttons = List.of(
                Button.success(TRIAL_PREFIX + game.getId() + ":approve", "찬성"),
                Button.danger(TRIAL_PREFIX + game.getId() + ":oppose", "반대"));
        TextChannel channel = event.getJDA().getTextChannelById(game.getChannelId());
        channel.sendMessageEmbeds(countdownEmbed("최후의 반론", "`" + game.getPlayerName(candidateId)
                        + "`의 처형 여부를 결정해주세요.", 5))
                .setComponents(ActionRow.of(buttons))
                .queue(message -> gameTimer.schedule(() -> updateTrialTimer(event, game, message.getId(),
                                candidateId, version, 4), 1, TimeUnit.SECONDS),
                        error -> System.err.println("찬반 투표 안내 전송 실패: " + error.getMessage()));
    }

    private void updateTrialTimer(ButtonInteractionEvent event, Game game, String messageId,
                                  String candidateId, int version, int remaining) {
        if (game.isFinished()) return;
        event.getJDA().getTextChannelById(game.getChannelId())
                .editMessageEmbedsById(messageId, countdownEmbed("최후의 반론", "`"
                        + game.getPlayerName(candidateId) + "`의 처형 여부를 결정해주세요.", remaining))
                .queue(null, error -> System.err.println("찬반 투표 타이머 갱신 실패: " + error.getMessage()));
        if (remaining > 0) {
            gameTimer.schedule(() -> updateTrialTimer(event, game, messageId, candidateId, version, remaining - 1),
                    1, TimeUnit.SECONDS);
        } else {
            Boolean executed = game.finishTrial(version);
            if (executed == null) return;
            sendAnnouncement(event, game, "최후의 반론", executed
                    ? "`" + game.getPlayerName(candidateId) + "`님이 투표로 처형당하였습니다."
                    : "`" + game.getPlayerName(candidateId) + "`님의 처형이 부결되었습니다.");
            if (executed) speechPermissions.silencePlayer(event.getJDA(), game, candidateId,
                    () -> continueAfterDay(event, game));
            else continueAfterDay(event, game);
        }
    }

    private void handleVote(ButtonInteractionEvent event, String componentId) {
        String[] parts = componentId.split(":", 4);
        if (parts.length != 4) return;
        Game game = gameManager.getGame(parts[2]);
        boolean accepted = game != null && event.getGuild() != null
                && game.getGuildId().equals(event.getGuild().getId())
                && game.getChannelId().equals(event.getChannel().getId())
                && game.castVote(event.getUser().getId(), parts[3]);
        if (game != null && accepted) {
            replyEphemeral(event, game, "투표 완료", "투표가 접수되었습니다.", 0x57F287);
        } else {
            replyEphemeral(event, "투표 실패", "생존한 참가자는 한 번만 투표할 수 있습니다.", 0xED4245);
        }
    }

    private void handleTrialVote(ButtonInteractionEvent event, String componentId) {
        String[] parts = componentId.split(":", 4);
        if (parts.length != 4) return;
        Game game = gameManager.getGame(parts[2]);
        boolean approve = parts[3].equals("approve");
        boolean accepted = game != null && event.getGuild() != null
                && game.getGuildId().equals(event.getGuild().getId())
                && game.getChannelId().equals(event.getChannel().getId())
                && (approve || parts[3].equals("oppose"))
                && game.castTrialVote(event.getUser().getId(), approve);
        if (game != null && accepted) {
            replyEphemeral(event, game, "찬반 투표 완료", "찬반 투표가 접수되었습니다.", 0x57F287);
        } else {
            replyEphemeral(event, "투표 실패", "후보자 외 생존 참가자는 한 번만 투표할 수 있습니다.", 0xED4245);
        }
    }

    private void continueAfterDay(ButtonInteractionEvent event, Game game) {
        String winner = game.checkWinner();
        if (winner != null) {
            announceGameEnd(event, game, winner);
            speechPermissions.restoreDeadPlayers(event.getJDA(), game);
            participantHooks.remove(game.getId());
        } else {
            startNight(event, game);
        }
    }

    private List<net.dv8tion.jda.api.components.MessageTopLevelComponent> buttonRows(List<Button> buttons) {
        List<net.dv8tion.jda.api.components.MessageTopLevelComponent> rows = new ArrayList<>();
        for (int i = 0; i < buttons.size(); i += 5) {
            rows.add(ActionRow.of(buttons.subList(i, Math.min(i + 5, buttons.size()))));
        }
        return rows;
    }

    private net.dv8tion.jda.api.entities.MessageEmbed countdownEmbed(String title, String prompt, int seconds) {
        int minutes = seconds / 60;
        int remainder = seconds % 60;
        return new EmbedBuilder().setTitle(title)
                .setDescription(prompt + "\n남은 시간: ⏳" + String.format("%02d:%02d", minutes, remainder))
                .setColor(0x5865F2).build();
    }

    private void sendAnnouncement(ButtonInteractionEvent event, Game game, String title, String message) {
        event.getJDA().getTextChannelById(game.getChannelId()).sendMessageEmbeds(new EmbedBuilder()
                        .setTitle(title).setDescription(message).setColor(0x5865F2).build())
                .queue(null, error -> System.err.println("게임 안내 전송 실패: " + error.getMessage()));
    }

    private void announceGameEnd(ButtonInteractionEvent event, Game game, String winner) {
        TextChannel channel = event.getJDA().getTextChannelById(game.getChannelId());
        channel.sendMessageEmbeds(new EmbedBuilder().setTitle("게임 결과")
                        .setDescription(winner).setColor(0x5865F2).build())
                .queue(ignored -> {
                    StringBuilder roles = new StringBuilder();
                    for (Player player : game.getPlayers()) {
                        if (roles.length() > 0) roles.append('\n');
                        roles.append(player.getNickname()).append(" - ").append(player.getRoleName());
                    }
                    channel.sendMessageEmbeds(new EmbedBuilder().setTitle("직업 배정 결과")
                                    .setDescription(roles.toString()).setColor(0x5865F2).build())
                            .queue(null, error -> System.err.println("직업 결과 전송 실패: " + error.getMessage()));
                }, error -> System.err.println("게임 결과 전송 실패: " + error.getMessage()));
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

    private void replyEphemeral(ButtonInteractionEvent event, Game game, String title, String message, int color) {
        event.replyEmbeds(new EmbedBuilder().setTitle(title).setDescription(message).setColor(color).build())
                .setEphemeral(true)
                .queue(hook -> rememberHook(game, event.getUser().getId(), hook));
    }

    private void rememberHook(Game game, String playerId, InteractionHook hook) {
        participantHooks.computeIfAbsent(game.getId(), ignored -> new ConcurrentHashMap<>()).put(playerId, hook);
    }
}
