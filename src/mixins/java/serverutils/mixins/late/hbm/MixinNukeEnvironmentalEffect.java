package serverutils.mixins.late.hbm;

import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import serverutils.ServerUtilities;
import serverutils.ServerUtilitiesConfig;
import serverutils.data.ClaimedChunks;
import serverutils.handlers.ServerUtilitiesWorldEventHandler;
import serverutils.lib.math.ChunkDimPos;

@Pseudo
@Mixin(targets = "com.hbm.explosion.NukeEnvironmentalEffect", remap = false)
public abstract class MixinNukeEnvironmentalEffect {

    @Inject(method = "applyStandardEffect(Lnet/minecraft/world/World;III)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void serverutilities$guardEnvironmentalEffect(World world, int x, int y, int z, CallbackInfo ci) {
        if (!ClaimedChunks.isActive() || world == null || world.isRemote) {
            return;
        }

        ChunkDimPos targetChunkPos = new ChunkDimPos(x, y, z, world.provider.dimensionId);
        boolean allowed = ServerUtilitiesWorldEventHandler.canExplosionAffect(
                world,
                targetChunkPos,
                ServerUtilitiesWorldEventHandler.getCurrentExplosion());

        if (!allowed) {
            if (ServerUtilitiesConfig.world.log_hbm_explosion_checks) {
                ServerUtilities.LOGGER.info(
                        "[ServerUtilities] HBM environmental effect blocked in protected chunk {} at ({}, {}, {})",
                        targetChunkPos,
                        x,
                        y,
                        z);
            }
            ci.cancel();
        }
    }
}
