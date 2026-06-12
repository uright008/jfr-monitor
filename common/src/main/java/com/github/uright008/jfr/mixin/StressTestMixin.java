package com.github.uright008.jfr.mixin;

import com.github.uright008.jfr.StressTestConfig;
import com.github.uright008.pc.ServerHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Unified stress test mixin — supports TNT and hopper modes
 * configurable via {@link StressTestConfig}.
 */
@Mixin(ServerLevel.class)
public abstract class StressTestMixin {

    @Shadow public boolean noSave;

    @Unique private static final Logger LOG = LoggerFactory.getLogger("stress-test");
    @Unique private static final int BASE_Y = -30;
    @Unique private static final int HOPPER_Y = -60;

    @Unique private int stage, tickCount;
    @Unique private boolean stopped;

    @Inject(method = "tick", at = @At("HEAD"))
    private void stressTestTick(CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return;
        var server = level.getServer();
        if (server == null) return;

        boolean tnt = StressTestConfig.isEnabled();
        boolean hopper = StressTestConfig.isHopperEnabled();
        boolean entity = StressTestConfig.isEntitySpawnEnabled();
        if (!tnt && !hopper && !entity) return;

        if (stage == 0) {
            this.noSave = true;
            int r = StressTestConfig.chunkRadius();
            ServerHelper.forceload(server, r);
            stage = 1;
            return;
        }

        tickCount++;
        int timeout = StressTestConfig.timeoutSeconds();
        if (stopped || (timeout > 0 && tickCount > timeout * 20)) {
            if (!stopped) stopped = true;
            return;
        }

        if (tnt) runTnt(level);
        if (hopper) runHopper(level);
        if (entity) runEntitySpawn(level);
    }

    @Unique
    private void runEntitySpawn(ServerLevel level) {
        int rate = StressTestConfig.entitySpawnRate();
        final double spawnX = 0.5, spawnY = -29.0, spawnZ = 0.5;

        // Build 3x3 chunk grass platform (one block below spawn)
        if (stage == 1) {
            int r = 1; // 3x3 chunks = radius 1
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            int platformY = (int) spawnY - 1;
            for (int cx = -r; cx <= r; cx++) {
                for (int cz = -r; cz <= r; cz++) {
                    for (int lx = 0; lx < 16; lx++)
                        for (int lz = 0; lz < 16; lz++) {
                            p.set(cx * 16 + lx, platformY, cz * 16 + lz);
                            level.setBlock(p, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                            // air above
                            for (int dy = 1; dy <= 3; dy++) {
                                p.set(cx * 16 + lx, platformY + dy, cz * 16 + lz);
                                if (!level.getBlockState(p).isAir())
                                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                            }
                        }
                }
            }
            LOG.info("Entity spawn platform: 3x3 chunks grass at y={}", platformY);
            stage = 2;
        }

        // Spawn entities at single point for collision testing
        for (int i = 0; i < rate; i++) {
            ZombifiedPiglin piglin = new ZombifiedPiglin(EntityType.ZOMBIFIED_PIGLIN, level);
            piglin.setPos(spawnX, spawnY, spawnZ);
            piglin.setPersistenceRequired();
            level.addFreshEntity(piglin);
        }
    }

    @Unique
    private void runTnt(ServerLevel level) {
        int r = StressTestConfig.chunkRadius();
        int size = StressTestConfig.tntSize();
        int half = size / 2;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int cx = -r; cx < r; cx++) {
            for (int cz = -r; cz < r; cz++) {
                int bx = cx * 16 + 8, bz = cz * 16 + 8;

                // 4 layers stone above TNT
                for (int dx = -half; dx <= half; dx++)
                    for (int dy = size; dy < size + 4; dy++)
                        for (int dz = -half; dz <= half; dz++) {
                            pos.set(bx + dx, BASE_Y + dy, bz + dz);
                            if (!level.getBlockState(pos).is(Blocks.STONE))
                                level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
                        }

                // Middle: size layers of TNT
                for (int dx = -half; dx <= half; dx++)
                    for (int dy = 0; dy < size; dy++)
                        for (int dz = -half; dz <= half; dz++) {
                            pos.set(bx + dx, BASE_Y + dy, bz + dz);
                            if (!level.getBlockState(pos).is(Blocks.TNT))
                                level.setBlock(pos, Blocks.TNT.defaultBlockState(), 3);
                        }

                // 4 layers stone below TNT
                for (int dx = -half; dx <= half; dx++)
                    for (int dy = -4; dy < 0; dy++)
                        for (int dz = -half; dz <= half; dz++) {
                            pos.set(bx + dx, BASE_Y + dy, bz + dz);
                            if (!level.getBlockState(pos).is(Blocks.STONE))
                                level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
                        }

                PrimedTnt tnt = new PrimedTnt(EntityType.TNT, level);
                tnt.setPos(bx + 0.5, BASE_Y + 1, bz + 0.5);
                tnt.setFuse(1);
                level.addFreshEntity(tnt);
            }
        }
    }

    @Unique
    private void runHopper(ServerLevel level) {
        int r = StressTestConfig.chunkRadius();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        // Build hoppers on first hopper tick
        if (stage == 1) {
            for (int cx = -r; cx < r; cx++) {
                for (int cz = -r; cz < r; cz++) {
                    int bx = cx * 16 + 8, bz = cz * 16 + 8;
                    level.setBlock(new BlockPos(bx, HOPPER_Y, bz), Blocks.STONE.defaultBlockState(), 3);
                    pos.set(bx, HOPPER_Y + 1, bz);
                    level.setBlock(pos, Blocks.HOPPER.defaultBlockState()
                            .setValue(HopperBlock.FACING, Direction.DOWN)
                            .setValue(HopperBlock.ENABLED, true), 3);
                }
            }
            LOG.info("Built {} hoppers", (r * 2) * (r * 2));
            stage = 2;
        }

        // Spawn items above each hopper every tick
        for (int cx = -r; cx < r; cx++) {
            for (int cz = -r; cz < r; cz++) {
                int bx = cx * 16 + 8, bz = cz * 16 + 8;
                ItemEntity item = new ItemEntity(EntityType.ITEM, level);
                item.setItem(new ItemStack(Items.STONE, 1));
                item.setPos(bx + 0.5, HOPPER_Y + 2.5, bz + 0.5);
                item.setDeltaMovement(0, 0, 0);
                item.setPickUpDelay(0);
                level.addFreshEntity(item);
            }
        }
    }
}
