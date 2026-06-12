package com.github.uright008.jfr.mixin;

import com.github.uright008.jfr.StressTestConfig;
import com.mojang.brigadier.StringReader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class HopperStressMixin {

    @Unique private static final Logger LOG = LoggerFactory.getLogger("stress-test:hopper");
    @Unique private static final int GRID = 16;
    @Unique private static final int BASE_Y = -60;
    @Unique private int stage;
    @Unique private int stressTickCount;

    @Inject(method = "tick", at = @At("HEAD"))
    private void hopperStressTick(CallbackInfo ci) {
        if (!StressTestConfig.isHopperEnabled()) return;
        ServerLevel level = (ServerLevel) (Object) this;
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return;
        var server = level.getServer();
        if (server == null) return;

        if (stage == 0) {
            LOG.info("Stage 0: forceloading...");
            runCmd(server, "forceload remove all");
            runCmd(server, "forceload add -8 -8 7 7");
            stage = 1;
            return;
        }
        if (stage == 1) {
            LOG.info("Stage 1: building {}x{} hopper grid...", GRID, GRID);
            buildHoppers(level);
            stage = 2;
            LOG.info("Stage 2: done, {} hoppers placed", GRID * GRID);
            return;
        }

        // Stage 2+: spawn items above hoppers every tick
        stressTickCount++;
        int timeout = StressTestConfig.timeoutSeconds();
        if (timeout > 0 && stressTickCount > timeout * 20) return;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int z = 0; z < GRID; z++) {
            for (int x = 0; x < GRID; x++) {
                ItemEntity item = new ItemEntity(EntityType.ITEM, level);
                item.setItem(new ItemStack(Items.STONE, 1));
                item.setPos(x + 0.5, BASE_Y + 2.5, z + 0.5);
                item.setDeltaMovement(0, 0, 0);
                item.setPickUpDelay(0);
                level.addFreshEntity(item);
            }
        }
    }

    @Unique
    private void buildHoppers(ServerLevel level) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int z = 0; z < GRID; z++) {
            for (int x = 0; x < GRID; x++) {
                level.setBlock(new BlockPos(x, BASE_Y, z), Blocks.STONE.defaultBlockState(), 3);

                Direction facing = Direction.DOWN;

                pos.set(x, BASE_Y + 1, z);
                level.setBlock(pos, Blocks.HOPPER.defaultBlockState()
                        .setValue(HopperBlock.FACING, facing)
                        .setValue(HopperBlock.ENABLED, true), 3);
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
