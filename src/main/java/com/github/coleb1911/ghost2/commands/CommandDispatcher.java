package com.github.coleb1911.ghost2.commands;

import com.github.coleb1911.ghost2.Ghost2Application;
import com.github.coleb1911.ghost2.commands.meta.CommandContext;
import com.github.coleb1911.ghost2.commands.meta.CommandType;
import com.github.coleb1911.ghost2.commands.meta.Module;
import com.github.coleb1911.ghost2.commands.meta.ReflectiveAccess;
import com.github.coleb1911.ghost2.commands.modules.operator.ModuleClaimOperator;
import com.github.coleb1911.ghost2.database.entities.GuildMeta;
import com.github.coleb1911.ghost2.database.repos.GuildMetaRepository;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.util.Permission;
import discord4j.core.object.util.PermissionSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Processes {@link MessageCreateEvent}s from the main application class, and calls {@link Module#invoke invoke}
 * when a valid command is invoked in chat.
 */
@Component
@Configurable
public final class CommandDispatcher {
    private final ExecutorService executor;
    @Autowired private GuildMetaRepository guildRepo;
    @Autowired private CommandRegistry registry;

    /**
     * Construct a new CommandDispatcher.
     */
    @ReflectiveAccess
    public CommandDispatcher() {
        // Initialize command registry and thread pool
        executor = Executors.newCachedThreadPool();
    }

    /**
     * Processes {@link MessageCreateEvent}s and {@link Module#invoke invoke}s commands.
     * <p>
     * Should <b>NOT</b> be called by anything other than {@link Ghost2Application}.
     *
     * @param ev Event to process
     */
    public void onMessageEvent(MessageCreateEvent ev) {
        // Build command context
        final CommandContext ctx = new CommandContext(ev);

        // Fetch prefix from database
        // GuildMeta shouldn't be null, otherwise we wouldn't have received the event.
        // We still null-check to be safe and get rid of the warning.
        final GuildMeta meta = guildRepo.findById(ctx.getGuild().getId().asLong()).orElse(null);
        if (null == meta) {
            return;
        }
        String prefix = meta.getPrefix();

        // Check for prefix & isolate the command name if present
        String trigger = ctx.getTrigger();
        String commandName;
        if (trigger.indexOf(prefix) == 0) {
            commandName = trigger.replace(prefix, "");
        // Check for bot mention & isolate the command name if present
        } else if(trigger.equals(ctx.getSelf().getMention())){
            commandName = ctx.getArgs().remove(0);
        } else {
            return;
        }

        // Get & null-check Module instance
        final Module module = registry.getCommandInstance(commandName);
        if (null == module) {
            return;
        }

        // Check user's permissions
        PermissionSet invokerPerms = ctx.getInvoker().getBasePermissions().block();
        if (null == invokerPerms) {
            ctx.reply(Module.REPLY_GENERAL_ERROR);
            return;
        }
        if (!invokerPerms.contains(Permission.ADMINISTRATOR)) {
            for (Permission required : module.getInfo().getUserPermissions()) {
                if (!invokerPerms.contains(required)) {
                    ctx.reply(Module.REPLY_INSUFFICIENT_PERMISSIONS_USER);
                    return;
                }
            }
        }

        // Check user's ID if command is an operator command
        // An exception is made for ModuleClaimOperator
        if (!(module instanceof ModuleClaimOperator) &&
                (module.getInfo().getType() == CommandType.OPERATOR) &&
                (ctx.getInvoker().getId().asLong() != Ghost2Application.getApplicationInstance().getOperatorId())) {

            ctx.reply(Module.REPLY_INSUFFICIENT_PERMISSIONS_USER);
            return;
        }

        // Check bot's permissions
        PermissionSet botPerms = ctx.getSelf().getBasePermissions().block();
        if (null == botPerms) {
            ctx.reply(Module.REPLY_GENERAL_ERROR);
            return;
        }
        if (!botPerms.contains(Permission.ADMINISTRATOR)) {
            for (Permission required : module.getInfo().getBotPermissions()) {
                if (!botPerms.contains(required)) {
                    ctx.reply(Module.REPLY_INSUFFICIENT_PERMISSIONS_BOT);
                    return;
                }
            }
        }

        // Finally kick off command thread if all checks are passed
        executor.execute(() -> module.invoke(ctx));
    }

    public CommandRegistry getRegistry() {
        return registry;
    }
}