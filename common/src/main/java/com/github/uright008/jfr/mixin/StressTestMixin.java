package com.github.uright008.jfr.mixin;

import com.github.uright008.jfr.StressTestConfig;
import com.mojang.brigadier.StringReader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Places one TNT machine per forceloaded chunk to test scalability.
 * Each chunk gets a 3x3x3 TNT cube with stone shell.
 */
@Mixin(ServerLevel.class)
public abstract class StressTestMixin {

    @Unique private int tickCount;
    @Unique private boolean started;
    @Unique private boolean stopped;
    @Unique private static final int CHUNK_RADIUS = 8;
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
            runCmd(server, "forceload remove all");
            runCmd(server, "forceload add -" + CHUNK_RADIUS + " -" + CHUNK_RADIUS
                    + " " + (CHUNK_RADIUS - 1) + " " + (CHUNK_RADIUS - 1));
            return;
        }

        tickCount++;
        if (stopped) return;
        int timeout = StressTestConfig.timeoutSeconds();
        if (timeout > 0 && tickCount > timeout * 20) { stopped = true; return; }

        // Every tick: TNT machine in every chunk
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int cx = -CHUNK_RADIUS; cx < CHUNK_RADIUS; cx++) {
            for (int cz = -CHUNK_RADIUS; cz < CHUNK_RADIUS; cz++) {
                int bx = cx * 16 + 8;
                int bz = cz * 16 + 8;

                // Single TNT block at chunk center, ignite
                pos.set(bx, BASE_Y, bz);
                if (!level.getBlockState(pos).is(Blocks.TNT))
                    level.setBlock(pos, Blocks.TNT.defaultBlockState(), 3);
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
