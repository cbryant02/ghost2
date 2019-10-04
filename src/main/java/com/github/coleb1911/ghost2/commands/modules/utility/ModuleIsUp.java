package com.github.coleb1911.ghost2.commands.modules.utility;

import com.github.coleb1911.ghost2.commands.meta.CommandContext;
import com.github.coleb1911.ghost2.commands.meta.Module;
import com.github.coleb1911.ghost2.commands.meta.ModuleInfo;
import com.github.coleb1911.ghost2.commands.meta.ReflectiveAccess;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

public final class ModuleIsUp extends Module {
    @ReflectiveAccess
    public ModuleIsUp() {
        super(new ModuleInfo.Builder(ModuleIsUp.class)
                .withName("isup")
                .withDescription("Checks if a website is up"));
    }

    @Override
    public void invoke(@NotNull final CommandContext ctx) {
        if (ctx.getArgs().isEmpty()) {
            ctx.reply("Please specify a host or IP.");
        }

        try {
            final InetAddress address = InetAddress.getByName(ctx.getArgs().get(0));
            if (address.isReachable(10000)) {
                ctx.reply("Host is up!");
            } else {
                ctx.reply("Host is down!");
            }
        } catch (UnknownHostException e) {
            ctx.reply("Could not resolve host");
        } catch (IOException e) {
            ctx.reply("Host is down!");
        }
    }
}