package serverutils.mixins.late.hbm;

import java.util.concurrent.ConcurrentMap;

import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import serverutils.ServerUtilities;
import serverutils.ServerUtilitiesConfig;
import serverutils.data.ClaimedChunks;
import serverutils.handlers.ServerUtilitiesWorldEventHandler;
import serverutils.lib.math.ChunkDimPos;

@Pseudo
@Mixin(targets = "com.hbm.explosion.ExplosionNukeRayParallelized", remap = false)
public abstract class MixinExplosionNukeRayParallelized {

    @Shadow
    @Final
    protected World world;

    @Shadow
    @Final
    private ConcurrentMap<ChunkCoordIntPair, ?> destructionMap;

    @Shadow
    @Final
    private ConcurrentMap<ChunkCoordIntPair, ?> damageMap;

    @Inject(method = "runConsolidation()V", at = @At("HEAD"), remap = false)
    private void serverutilities$pruneProtectedChunksBeforeConsolidation(CallbackInfo ci) {
        serverutilities$pruneProtectedChunks();
    }

    @Inject(method = "destructionTick(I)V", at = @At("HEAD"), remap = false)
    private void serverutilities$pruneProtectedChunksBeforeDestruction(int timeBudgetMs, CallbackInfo ci) {
        serverutilities$pruneProtectedChunks();
    }

    private void serverutilities$pruneProtectedChunks() {
        if (!ClaimedChunks.isActive() || world == null || world.isRemote) {
            return;
        }

        destructionMap.keySet().removeIf(this::serverutilities$isProtectedChunk);
        damageMap.keySet().removeIf(this::serverutilities$isProtectedChunk);
    }

    private boolean serverutilities$isProtectedChunk(ChunkCoordIntPair chunkPos) {
        ChunkDimPos targetChunkPos = new ChunkDimPos(chunkPos, world.provider.dimensionId);
        boolean allowed = ServerUtilitiesWorldEventHandler.canExplosionAffect(
                world,
                targetChunkPos,
                ServerUtilitiesWorldEventHandler.getCurrentExplosion());

        if (!allowed && ServerUtilitiesConfig.world.log_hbm_explosion_checks) {
            ServerUtilities.LOGGER.info(
                    "[ServerUtilities] HBM parallelized nuke blocked in protected chunk {}",
                    targetChunkPos);
        }

        return !allowed;
    }
}
