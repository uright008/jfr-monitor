package com.github.uright008.jfr;

import com.github.uright008.pc.command.ParallelCommand;
import com.github.uright008.pc.command.ParallelSubCommand;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class JfrCommand implements ParallelSubCommand {

    @Override public String getName() { return "jfr"; }

    @Override
    public void build(LiteralArgumentBuilder<CommandSourceStack> builder) {
        builder
                .executes(this::showStatus)
                .then(Commands.literal("start")
                        .executes(ctx -> start(ctx, 30))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                                .executes(ctx -> start(ctx, IntegerArgumentType.getInteger(ctx, "seconds")))))
                .then(Commands.literal("stop").executes(this::stop));
    }

    @Override
    public String getStatusLine() {
        return "\u00a77  JFR:       " + (JfrMonitor.isRecording() ? "\u00a7aREC" : "\u00a77off");
    }

    private int showStatus(CommandContext<CommandSourceStack> ctx) {
        String msg = "\u00a7e/parallel jfr\n"
                + "\u00a77  Status: " + (JfrMonitor.isRecording() ? "\u00a7aRECORDING" : "\u00a77idle") + "\n"
                + "\u00a77Usage: /parallel jfr [start [seconds] | stop]";
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private int start(CommandContext<CommandSourceStack> ctx, int seconds) {
        JfrMonitor.autoRecord(seconds);
        ctx.getSource().sendSuccess(
                () -> Component.literal("\u00a7aJFR recording started (" + seconds + "s) → run/jfr/"), true);
        return 1;
    }

    private int stop(CommandContext<CommandSourceStack> ctx) {
        JfrMonitor.stop();
        ctx.getSource().sendSuccess(
                () -> Component.literal("\u00a7aJFR recording saved to run/jfr/"), true);
        return 1;
    }
}
