package com.github.coleb1911.ghost2.commands.modules.music;

import com.github.coleb1911.ghost2.commands.meta.CommandContext;
import com.github.coleb1911.ghost2.commands.meta.Module;
import com.github.coleb1911.ghost2.commands.meta.ModuleInfo;
import com.github.coleb1911.ghost2.commands.meta.ReflectiveAccess;
import com.github.coleb1911.ghost2.music.MusicUtils;

import javax.validation.constraints.NotNull;

public final class ModuleRemove extends Module {
    @ReflectiveAccess
    public ModuleRemove() {
        super(new ModuleInfo.Builder(ModuleRemove.class)
                .withName("remove")
                .withDescription("Remove the track at the given index.")
                .showTypingIndicator());
    }

    @Override
    public void invoke(@NotNull CommandContext ctx) {
        if (ctx.getArgs().size() < 1) {
            ctx.reply("Please provide a track number.");
            return;
        }

        int index;
        try {
            index = Integer.parseInt(ctx.getArgs().get(0));
        } catch (NumberFormatException e) {
            ctx.replyBlocking("That's not a number.");
            return;
        }

        MusicUtils.fetchMusicService(ctx)
                .flatMap(svc -> svc.remove(index-1))
                .subscribe(success -> {
                    if (success) {
                        ctx.replyBlocking("Removed track " + index + ".");
                        return;
                    }
                    ctx.replyBlocking("Invalid track number.");
                });
    }
}
