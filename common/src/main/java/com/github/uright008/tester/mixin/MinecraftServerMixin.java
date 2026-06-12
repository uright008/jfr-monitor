package com.github.uright008.tester.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {

    @Inject(method = "reportChunkSaveFailure", at = @At("HEAD"), cancellable = true)
    private void suppressSaveFailure(Throwable throwable, RegionStorageInfo storageInfo, ChunkPos pos, CallbackInfo ci) {
        for (net.minecraft.server.level.ServerLevel level : ((MinecraftServer) (Object) this).getAllLevels()) {
            if (level.noSave) {
                ci.cancel();
                return;
            }
        }
    }
}
