package mafia.bot;

import mafia.game.Game;
import mafia.game.Player;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.restaction.PermissionOverrideAction;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

final class ChannelSpeechPermissionManager {
    private static final List<Permission> SEND_PERMISSIONS = List.of(
            Permission.MESSAGE_SEND, Permission.MESSAGE_SEND_IN_THREADS);

    private final Map<String, Map<String, Map<Permission, Integer>>> defenseSnapshots = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Map<Permission, Integer>>> deadPlayerSnapshots = new ConcurrentHashMap<>();

    void restrictDefense(JDA jda, Game game, String candidateId, Runnable whenComplete) {
        TextChannel channel = jda.getTextChannelById(game.getChannelId());
        Map<String, Map<Permission, Integer>> snapshots = new ConcurrentHashMap<>();
        defenseSnapshots.put(game.getId(), snapshots);

        List<Player> players = game.getPlayers();
        AtomicInteger pending = new AtomicInteger(players.size());
        if (players.isEmpty()) {
            whenComplete.run();
            return;
        }

        for (Player player : players) {
            jda.getGuildById(game.getGuildId()).retrieveMemberById(player.getId()).queue(member -> {
                snapshots.put(player.getId(), snapshot(channel.getPermissionOverride(member)));
                PermissionOverrideAction action = channel.upsertPermissionOverride(member);
                if (player.getId().equals(candidateId)) action.grant(SEND_PERMISSIONS);
                else action.deny(SEND_PERMISSIONS);
                action.queue(ignored -> complete(pending, whenComplete), error -> {
                    logFailure("최후의 반론 발언권 설정 실패", error);
                    complete(pending, whenComplete);
                });
            }, error -> {
                logFailure("최후의 반론 참가자 조회 실패", error);
                complete(pending, whenComplete);
            });
        }
    }

    void restoreAfterDefense(JDA jda, Game game, Runnable whenComplete) {
        restore(jda, game, defenseSnapshots.remove(game.getId()), whenComplete, "발언권 복구 실패");
    }

    void silencePlayer(JDA jda, Game game, String playerId, Runnable whenComplete) {
        TextChannel channel = jda.getTextChannelById(game.getChannelId());
        jda.getGuildById(game.getGuildId()).retrieveMemberById(playerId).queue(member -> {
            deadPlayerSnapshots.computeIfAbsent(game.getId(), ignored -> new ConcurrentHashMap<>())
                    .put(playerId, snapshot(channel.getPermissionOverride(member)));
            channel.upsertPermissionOverride(member).deny(SEND_PERMISSIONS).queue(
                    ignored -> whenComplete.run(), error -> {
                        logFailure("사망 플레이어 발언 제한 실패", error);
                        whenComplete.run();
                    });
        }, error -> {
            logFailure("사망 플레이어 조회 실패", error);
            whenComplete.run();
        });
    }

    void restoreDeadPlayers(JDA jda, Game game) {
        restore(jda, game, deadPlayerSnapshots.remove(game.getId()), () -> { }, "게임 종료 후 발언권 복구 실패");
    }

    private void restore(JDA jda, Game game, Map<String, Map<Permission, Integer>> snapshots,
                         Runnable whenComplete, String failureMessage) {
        if (snapshots == null || snapshots.isEmpty()) {
            whenComplete.run();
            return;
        }

        TextChannel channel = jda.getTextChannelById(game.getChannelId());
        AtomicInteger pending = new AtomicInteger(snapshots.size());
        snapshots.forEach((playerId, snapshot) -> jda.getGuildById(game.getGuildId())
                .retrieveMemberById(playerId).queue(member -> {
                    PermissionOverrideAction action = channel.upsertPermissionOverride(member).clear(SEND_PERMISSIONS);
                    EnumSet<Permission> allowed = permissionsWithState(snapshot, 1);
                    EnumSet<Permission> denied = permissionsWithState(snapshot, -1);
                    if (!allowed.isEmpty()) action.grant(allowed);
                    if (!denied.isEmpty()) action.deny(denied);
                    action.queue(ignored -> complete(pending, whenComplete), error -> {
                        logFailure(failureMessage, error);
                        complete(pending, whenComplete);
                    });
                }, error -> {
                    logFailure("발언권 복구 대상 조회 실패", error);
                    complete(pending, whenComplete);
                }));
    }

    private Map<Permission, Integer> snapshot(PermissionOverride override) {
        Map<Permission, Integer> result = new EnumMap<>(Permission.class);
        for (Permission permission : SEND_PERMISSIONS) {
            int state = override == null ? 0 : override.getAllowed().contains(permission) ? 1
                    : override.getDenied().contains(permission) ? -1 : 0;
            result.put(permission, state);
        }
        return result;
    }

    private EnumSet<Permission> permissionsWithState(Map<Permission, Integer> snapshot, int expectedState) {
        EnumSet<Permission> result = EnumSet.noneOf(Permission.class);
        for (Permission permission : SEND_PERMISSIONS) {
            if (snapshot.getOrDefault(permission, 0) == expectedState) result.add(permission);
        }
        return result;
    }

    private void complete(AtomicInteger pending, Runnable callback) {
        if (pending.decrementAndGet() == 0) callback.run();
    }

    private void logFailure(String action, Throwable error) {
        System.err.println(action + ": " + error.getMessage());
    }
}
