package com.github.uright008.tester.mixin;

import com.github.uright008.tester.StressTestConfig;
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

    @Unique private int stage, tickCount, entityCount;
    @Unique private boolean tntBuilt, entityBuilt, stopped;
    @Unique private int benchStep, benchPhase, benchPhaseTick;
    @Unique private long benchMSPTAccum;
    @Unique private static final int BENCH_SPAWN = 0, BENCH_SETTLE = 1, BENCH_MEASURE = 2, BENCH_CLEAN = 3;

    @Inject(method = "tick", at = @At("HEAD"))
    private void stressTestTick(CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return;
        var server = level.getServer();
        if (server == null) return;

        boolean tnt = StressTestConfig.isTnt();
        boolean hopper = StressTestConfig.isHopper();
        boolean entity = StressTestConfig.isEntity();
        boolean bench = StressTestConfig.isBenchmark();
        if (!tnt && !hopper && !entity && !bench) return;

        if (stage == 0) {
            this.noSave = true;
            int r = StressTestConfig.chunkRadius();
            ServerHelper.forceload(server, r);
            stage = 1;
            return;
        }

        // Build platform once
        if ((entity || bench) && !entityBuilt) {
            buildEntityPlatform(level);
            entityBuilt = true;
        }

        if (bench) {
            runBenchmark(level);
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
        int max = StressTestConfig.maxEntities();
        if (entityCount >= max) return;

        int rate = StressTestConfig.entitySpawnRate();
        int toSpawn = Math.min(rate, max - entityCount);
        int rad = Math.max(1, StressTestConfig.chunkRadius());
        java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();

        for (int i = 0; i < toSpawn; i++) {
            int cx = rng.nextInt(-rad, rad);
            int cz = rng.nextInt(-rad, rad);
            double x = cx * 16 + rng.nextDouble() * 16;
            double z = cz * 16 + rng.nextDouble() * 16;

            ZombifiedPiglin piglin = new ZombifiedPiglin(EntityType.ZOMBIFIED_PIGLIN, level);
            piglin.setPos(x, -29.0, z);
            piglin.setPersistenceRequired();
            level.addFreshEntity(piglin);
            entityCount++;
        }
    }

    @Unique
    private void buildEntityPlatform(ServerLevel level) {
        int r = Math.max(1, StressTestConfig.chunkRadius());
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int platformY = -30;
        for (int cx = -r; cx < r; cx++) {
            for (int cz = -r; cz < r; cz++) {
                for (int lx = 0; lx < 16; lx++)
                    for (int lz = 0; lz < 16; lz++) {
                        p.set(cx * 16 + lx, platformY, cz * 16 + lz);
                        level.setBlock(p, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                        for (int dy = 1; dy <= 3; dy++) {
                            p.set(cx * 16 + lx, platformY + dy, cz * 16 + lz);
                            if (!level.getBlockState(p).isAir())
                                level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
            }
        }
        LOG.info("Entity platform: {}x{} chunks grass at y={}", r*2, r*2, platformY);
    }

    @Unique
    private void runTnt(ServerLevel level) {
        int r = StressTestConfig.chunkRadius();
        int size = StressTestConfig.tntSize();
        for (int cx = -r; cx < r; cx++) {
            for (int cz = -r; cz < r; cz++) {
                PrimedTnt tnt = new PrimedTnt(EntityType.TNT, level);
                tnt.setPos(cx * 16 + 8.5, BASE_Y + size + 0.5, cz * 16 + 8.5);
                tnt.setFuse(1);
                level.addFreshEntity(tnt);
            }
        }
    }

    @Unique
    private void buildTntSandwich(ServerLevel level) {
        int r = StressTestConfig.chunkRadius();
        int size = StressTestConfig.tntSize();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int cx = -r; cx < r; cx++) {
            for (int cz = -r; cz < r; cz++) {
                int bx = cx * 16, bz = cz * 16;
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        int wx = bx + lx, wz = bz + lz;
                        for (int dy = 0; dy < size; dy++) {
                            pos.set(wx, BASE_Y + dy, wz);
                            level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
                        }
                        for (int dy = size; dy < size * 2; dy++) {
                            pos.set(wx, BASE_Y + dy, wz);
                            level.setBlock(pos, Blocks.TNT.defaultBlockState(), 3);
                        }
                        for (int dy = size * 2; dy < size * 3; dy++) {
                            pos.set(wx, BASE_Y + dy, wz);
                            level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
                        }
                    }
                }
            }
        }
        LOG.info("TNT sandwich: {} chunks, {} layers ({} stone+{} TNT+{} stone)",
                (r * 2) * (r * 2), size * 3, size, size, size);
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

    @Unique
    private void runBenchmark(ServerLevel level) {
        int[] steps = StressTestConfig.benchmarkSteps();
        if (benchStep >= steps.length) return;

        int target = steps[benchStep];
        int settleTicks = 20 * 5;  // 5s settle
        int measureTicks = 20 * StressTestConfig.benchmarkSeconds();

        switch (benchPhase) {
            case BENCH_SPAWN -> {
                if (entityCount < target) {
                    int rate = StressTestConfig.entitySpawnRate();
                    int toSpawn = Math.min(rate, target - entityCount);
                    int rad = Math.max(1, StressTestConfig.chunkRadius());
                    var rng = java.util.concurrent.ThreadLocalRandom.current();
                    for (int i = 0; i < toSpawn; i++) {
                        int cx = rng.nextInt(-rad, rad);
                        int cz = rng.nextInt(-rad, rad);
                        double x = cx * 16 + rng.nextDouble() * 16;
                        double z = cz * 16 + rng.nextDouble() * 16;
                        ZombifiedPiglin p = new ZombifiedPiglin(EntityType.ZOMBIFIED_PIGLIN, level);
                        p.setPos(x, -29, z);
                        p.setPersistenceRequired();
                        level.addFreshEntity(p);
                        entityCount++;
                    }
                } else {
                    benchPhase = BENCH_SETTLE;
                    benchPhaseTick = 0;
                    benchMSPTAccum = 0;
                }
            }
            case BENCH_SETTLE -> {
                if (++benchPhaseTick >= settleTicks) {
                    benchPhase = BENCH_MEASURE;
                    benchPhaseTick = 0;
                    benchMSPTAccum = 0;
                }
            }
            case BENCH_MEASURE -> {
                benchMSPTAccum += level.getServer().getAverageTickTimeNanos();
                benchPhaseTick++;
                if (benchPhaseTick >= measureTicks) {
                    long avgNs = benchMSPTAccum / benchPhaseTick;
                    LOG.info("BENCH[{}] entities={} mspt={}ms tps={}",
                            benchStep, entityCount,
                            avgNs / 1_000_000.0, 1000.0 / (avgNs / 1_000_000.0));
                    benchPhase = BENCH_CLEAN;
                }
            }
            case BENCH_CLEAN -> {
                // Collect first, then kill (avoid concurrent modification)
                var toKill = new java.util.ArrayList<ZombifiedPiglin>();
                for (var e : level.getEntitiesOfClass(ZombifiedPiglin.class,
                        new net.minecraft.world.phys.AABB(-100, -64, -100, 100, 320, 100),
                        p -> true)) {
                    toKill.add(e);
                }
                // Keep 1 entity to prevent server auto-pause
                int keep = Math.min(1, toKill.size());
                for (int i = 0; i < toKill.size() - keep; i++) {
                    toKill.get(i).remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                }
                entityCount = keep;
                benchStep++;
                benchPhase = BENCH_SPAWN;
                if (benchStep >= steps.length) {
                    LOG.info("BENCH complete: {} steps", steps.length);
                }
            }
        }
    }
}
