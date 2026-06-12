package com.github.uright008.jfr.mixin;

import com.github.uright008.jfr.StressTestConfig;
import com.mojang.brigadier.StringReader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class StressTestMixin {

    @Unique
    private static final BlockPos TNT_FROM   = new BlockPos(6, -30, 6);
    @Unique
    private static final BlockPos TNT_TO     = new BlockPos(10, -26, 10);
    @Unique
    private static final BlockPos SHELL_FROM = new BlockPos(4, -32, 4);
    @Unique
    private static final BlockPos SHELL_TO   = new BlockPos(12, -24, 12);
    @Unique
    private static final BlockPos IGNITE_POS = new BlockPos(8, -26, 8);

    @Unique
    private int stressTickCount;
    @Unique
    private boolean stressStarted;
    @Unique
    private boolean stressStopped;

    @Inject(method = "tick", at = @At("HEAD"))
    private void stressTestTick(CallbackInfo ci) {
        if (!StressTestConfig.isEnabled()) return;
        ServerLevel level = (ServerLevel) (Object) this;
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return;

        var server = level.getServer();
        if (server == null) return;

        if (!stressStarted) {
            stressStarted = true;
            // Bot provides chunk loading; forceload provides entity processing
            runCmd(server, "forceload remove all");
            runCmd(server, "forceload add -4 -4 3 3");
        }

        stressTickCount++;
        if (stressStopped) return;
        int timeout = StressTestConfig.timeoutSeconds();
        if (timeout > 0 && stressTickCount > timeout * 20) {
            stressStopped = true;
            return;
        }

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = SHELL_FROM.getX(); x <= SHELL_TO.getX(); x++)
            for (int y = SHELL_FROM.getY(); y <= SHELL_TO.getY(); y++)
                for (int z = SHELL_FROM.getZ(); z <= SHELL_TO.getZ(); z++) {
                    pos.set(x, y, z);
                    if (level.getBlockState(pos).is(Blocks.TNT))
                        level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
                }

        for (int x = TNT_FROM.getX(); x <= TNT_TO.getX(); x++)
            for (int y = TNT_FROM.getY(); y <= TNT_TO.getY(); y++)
                for (int z = TNT_FROM.getZ(); z <= TNT_TO.getZ(); z++) {
                    pos.set(x, y, z);
                    if (!level.getBlockState(pos).is(Blocks.TNT))
                        level.setBlock(pos, Blocks.TNT.defaultBlockState(), 3);
                }

        PrimedTnt tnt = new PrimedTnt(EntityType.TNT, level);
        tnt.setPos(IGNITE_POS.getX() + 0.5, IGNITE_POS.getY(), IGNITE_POS.getZ() + 0.5);
        tnt.setFuse(1);
        level.addFreshEntity(tnt);
    }

    @Unique
    private static void runCmd(net.minecraft.server.MinecraftServer server, String cmd) {
        var source = server.createCommandSourceStack().withSuppressedOutput();
        var parse = server.getCommands().getDispatcher().parse(new StringReader(cmd), source);
        server.getCommands().performCommand(parse, cmd);
    }
}
