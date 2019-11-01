package com.github.coleb1911.ghost2.commands.modules.music;

import com.github.coleb1911.ghost2.commands.meta.CommandContext;
import com.github.coleb1911.ghost2.commands.meta.Module;
import com.github.coleb1911.ghost2.commands.meta.ModuleInfo;
import com.github.coleb1911.ghost2.music.MusicService;
import com.github.coleb1911.ghost2.music.MusicUtils;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.object.entity.GuildChannel;
import discord4j.core.object.entity.Message;
import discord4j.core.object.reaction.ReactionEmoji;
import discord4j.core.object.util.Permission;
import discord4j.core.object.util.PermissionSet;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.pmw.tinylog.Logger;

import javax.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public final class ModuleQueue extends Module {
    private static final ReactionEmoji REACT_PREV = ReactionEmoji.unicode("\u2B05");
    private static final ReactionEmoji REACT_NEXT = ReactionEmoji.unicode("\u27A1");

    public ModuleQueue() {
        super(new ModuleInfo.Builder(ModuleQueue.class)
                .withName("queue")
                .withDescription("Show the current tracks in the queue."));
    }

    @Override
    public void invoke(@NotNull CommandContext ctx) {
        MusicUtils.fetchMusicService(ctx)
                .flux()
                .flatMap(MusicService::streamTracks)
                .collectList()
                .subscribe(tracks -> {
                    if (tracks.isEmpty()) {
                        ctx.reply("Queue is empty.");
                        return;
                    }

                    new QueueEmbed(tracks, ctx);
                });
    }

    private static class QueueEmbed {
        private final List<AudioTrack> tracks;
        private final CommandContext ctx;
        private Message embedMessage;
        private AtomicInteger page = new AtomicInteger(0);

        private QueueEmbed(final List<AudioTrack> tracks, final CommandContext ctx) {
            this.tracks = tracks;
            this.ctx = ctx;

            if (tracks.isEmpty()) {
                ctx.reply("Queue is empty.");
                return;
            }

            PermissionSet perms = ((GuildChannel) ctx.getChannel()).getEffectivePermissions(ctx.getSelf().getId()).block();
            if (perms != null &&
                    !perms.containsAll(List.of(Permission.MANAGE_MESSAGES, Permission.ADD_REACTIONS))) {
                fallback();
                return;
            }

            update();
            ctx.getClient().getEventDispatcher().on(ReactionAddEvent.class)
                    .filter(e -> e.getMessageId().equals(embedMessage.getId()))
                    .filter(e -> e.getUserId().equals(ctx.getInvoker().getId()))
                    .timeout(Duration.ofSeconds(10L), e -> embedMessage.removeAllReactions().subscribe())
                    .doOnNext(ev -> {
                        ReactionEmoji em = ev.getEmoji();
                        if (em.equals(REACT_NEXT)) {
                            page.getAndIncrement();
                        } else if (em.equals(REACT_PREV)) {
                            page.getAndDecrement();
                        }
                        embedMessage.removeReaction(em, ev.getUserId()).subscribe();
                    })
                    .subscribe(em -> update());
        }

        private void update() {
            if (embedMessage == null) {
                Optional<Message> emOptional = ctx.getChannel().createMessage("One moment...")
                        .doOnError(Logger::error)
                        .blockOptional();
                if (emOptional.isPresent()) {
                    embedMessage = emOptional.get();
                    embedMessage.addReaction(REACT_PREV).subscribe();
                    embedMessage.addReaction(REACT_NEXT).subscribe();
                }
            }

            Objects.requireNonNull(embedMessage).edit(espec -> {
                espec.setContent("");
                espec.setEmbed(spec -> {
                    int start = page.get() * 10;
                    int end = Math.min(start + 10, tracks.size());

                    spec.setTitle("Queue");
                    spec.setFooter((start + 1) + "-" + end + " of " + tracks.size(), null);

                    for (int i = start; i < end; i++) {
                        AudioTrackInfo info = tracks.get(i).getInfo();
                        String duration = DurationFormatUtils.formatDuration(info.length, "HH':'mm':'ss");
                        if (duration.charAt(0) == ':') duration = duration.substring(1);
                        spec.addField(info.author, String.format("%d. [%s](%s) (%s)", (i + 1), info.title, info.uri, duration), false);
                    }
                });
            }).subscribe();
        }

        private void fallback() {
            ctx.replyEmbed(spec -> {
                int end = Math.min(10, tracks.size());
                for (int i = 0; i < end; i++) {
                    AudioTrackInfo info = tracks.get(i).getInfo();
                    String duration = DurationFormatUtils.formatDuration(info.length, "HH':'mm':'ss");
                    if (duration.charAt(0) == ':') duration = duration.substring(1);
                    spec.addField(info.author, String.format("%d. [%s](%s) (%s)", (i + 1), info.title, info.uri, duration), false);
                }
                spec.setDescription("Only displaying some tracks. To scroll through the queue, " +
                        "grant the `Manage Messages` and `Add Reactions` permissions.");
            }).subscribe();
        }
    }
}
