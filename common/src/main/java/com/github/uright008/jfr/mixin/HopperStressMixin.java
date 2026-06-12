package com.github.uright008.jfr.mixin;

import com.github.uright008.jfr.StressTestConfig;
import com.mojang.brigadier.StringReader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class HopperStressMixin {

    @Unique private static final int GRID = 16;
    @Unique private int stage;

    @Inject(method = "tick", at = @At("HEAD"))
    private void hopperStressTick(CallbackInfo ci) {
        if (!StressTestConfig.isHopperEnabled()) return;
        ServerLevel level = (ServerLevel) (Object) this;
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return;
        var server = level.getServer();
        if (server == null) return;

        if (stage == 0) {
            runCmd(server, "forceload remove all");
            runCmd(server, "forceload add -4 -4 3 3");
            stage = 1;
            return;
        }
        if (stage == 1) {
            buildHoppers(level);
            stage = 2;
            return;
        }
    }

    @Unique
    private void buildHoppers(ServerLevel level) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int baseY = -60;

        for (int z = 0; z < GRID; z++) {
            for (int x = 0; x < GRID; x++) {
                level.setBlock(new BlockPos(x, baseY, z), Blocks.STONE.defaultBlockState(), 3);

                Direction facing;
                if (z % 2 == 0)
                    facing = x < GRID - 1 ? Direction.EAST : (z < GRID - 1 ? Direction.SOUTH : Direction.DOWN);
                else
                    facing = x > 0 ? Direction.WEST : (z < GRID - 1 ? Direction.SOUTH : Direction.DOWN);

                pos.set(x, baseY + 1, z);
                level.setBlock(pos, Blocks.HOPPER.defaultBlockState()
                        .setValue(HopperBlock.FACING, facing)
                        .setValue(HopperBlock.ENABLED, true), 3);

                HopperBlockEntity be = (HopperBlockEntity) level.getBlockEntity(pos);
                if (be != null) be.setItem(0, new ItemStack(Items.STONE, 64));
            }
        }
        pos.set(GRID - 1, baseY, GRID - 1);
        level.setBlock(pos, Blocks.COMPOSTER.defaultBlockState(), 3);
    }

    @Unique
    private static void runCmd(net.minecraft.server.MinecraftServer server, String cmd) {
        var source = server.createCommandSourceStack().withSuppressedOutput();
        var parse = server.getCommands().getDispatcher().parse(new StringReader(cmd), source);
        server.getCommands().performCommand(parse, cmd);
    }
}
