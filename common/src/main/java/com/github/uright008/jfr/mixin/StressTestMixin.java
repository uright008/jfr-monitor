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

    @Unique private int tickCount;
    @Unique private boolean started, stopped;
    @Unique private static final int BASE_Y = -30;

    @Inject(method = "tick", at = @At("HEAD"))
    private void stressTestTick(CallbackInfo ci) {
        if (!StressTestConfig.isEnabled()) return;
        ServerLevel level = (ServerLevel) (Object) this;
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return;
        var server = level.getServer();
        if (server == null) return;

        if (!started) {
            started = true;
            int r = StressTestConfig.chunkRadius();
            runCmd(server, "forceload remove all");
            runCmd(server, "forceload add -" + r + " -" + r + " " + (r - 1) + " " + (r - 1));
            return;
        }

        tickCount++;
        if (stopped) return;
        int timeout = StressTestConfig.timeoutSeconds();
        if (timeout > 0 && tickCount > timeout * 20) { stopped = true; return; }

        int r = StressTestConfig.chunkRadius();
        int size = StressTestConfig.tntSize();
        int half = size / 2;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int cx = -r; cx < r; cx++) {
            for (int cz = -r; cz < r; cz++) {
                int bx = cx * 16 + 8;
                int bz = cz * 16 + 8;

                // TNT cube: size³ — no shell, raw performance test
                for (int dx = -half; dx <= half; dx++)
                    for (int dy = -half; dy <= half; dy++)
                        for (int dz = -half; dz <= half; dz++) {
                            pos.set(bx + dx, BASE_Y + dy, bz + dz);
                            if (!level.getBlockState(pos).is(Blocks.TNT))
                                level.setBlock(pos, Blocks.TNT.defaultBlockState(), 3);
                        }

                PrimedTnt tnt = new PrimedTnt(EntityType.TNT, level);
                tnt.setPos(bx + 0.5, BASE_Y, bz + 0.5);
                tnt.setFuse(1);
                level.addFreshEntity(tnt);
            }
        }
    }

    @Unique
    private static void runCmd(net.minecraft.server.MinecraftServer server, String cmd) {
        var source = server.createCommandSourceStack().withSuppressedOutput();
        var parse = server.getCommands().getDispatcher().parse(new StringReader(cmd), source);
        server.getCommands().performCommand(parse, cmd);
    }
}
