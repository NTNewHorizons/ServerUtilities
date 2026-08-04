package serverutils.mixins.late.hbm;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
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
@Mixin(targets = "com.hbm.explosion.ExplosionNukeRayBatched")
public abstract class MixinExplosionNukeRayBatched {

    @Shadow
    @Final
    private World world;

    @Shadow
    @Final
    private java.util.List<ChunkCoordIntPair> orderedChunks;

    @Shadow
    private java.util.HashMap<ChunkCoordIntPair, ?> perChunk;

    @Inject(method = "processChunk()V", at = @At("HEAD"), cancellable = true, remap = false)
    private void serverutilities$cancelProtectedBlockRemoval(CallbackInfo ci) {
        if (!ClaimedChunks.isActive()) {
            return;
        }

        if (world == null || world.isRemote) {
            return;
        }

        if (orderedChunks == null || orderedChunks.isEmpty()) {
            return;
        }

        ChunkCoordIntPair chunk = orderedChunks.get(0);
        ChunkDimPos targetChunkPos = new ChunkDimPos(chunk, world.provider.dimensionId);

        if (!ServerUtilitiesWorldEventHandler.canExplosionAffect(world, targetChunkPos,
                ServerUtilitiesWorldEventHandler.getCurrentExplosion())) {
            perChunk.remove(chunk);
            orderedChunks.remove(0);
            if (ServerUtilitiesConfig.world.log_hbm_explosion_checks) {
                ServerUtilities.LOGGER.info(
                        "HBM nuke ray chunk blocked at {} by ServerUtilities claimed chunk protection",
                        targetChunkPos);
            }
            ci.cancel();
        }
    }
}
