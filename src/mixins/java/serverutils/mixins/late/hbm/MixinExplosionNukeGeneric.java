package serverutils.mixins.late.hbm;

import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import serverutils.ServerUtilities;
import serverutils.ServerUtilitiesConfig;
import serverutils.data.ClaimedChunks;
import serverutils.handlers.ServerUtilitiesWorldEventHandler;
import serverutils.lib.math.ChunkDimPos;

@Pseudo
@Mixin(targets = "com.hbm.explosion.ExplosionNukeGeneric", remap = false)
public abstract class MixinExplosionNukeGeneric {

    @Inject(method = "destruction(Lnet/minecraft/world/World;III)I", at = @At("HEAD"), cancellable = true, remap = false)
    private static void serverutilities$guardDestruction(World world, int x, int y, int z,
            CallbackInfoReturnable<Integer> cir) {
        if (!serverutilities$isProtected(world, x, y, z, "destruction")) {
            return;
        }
        cir.setReturnValue(0);
    }

    @Inject(method = "vaporDest(Lnet/minecraft/world/World;III)I", at = @At("HEAD"), cancellable = true, remap = false)
    private static void serverutilities$guardVaporDest(World world, int x, int y, int z,
            CallbackInfoReturnable<Integer> cir) {
        if (!serverutilities$isProtected(world, x, y, z, "vapor")) {
            return;
        }
        cir.setReturnValue(0);
    }

    private static boolean serverutilities$isProtected(World world, int x, int y, int z, String path) {
        if (!ClaimedChunks.isActive() || world == null || world.isRemote) {
            return false;
        }

        ChunkDimPos targetChunkPos = new ChunkDimPos(x, y, z, world.provider.dimensionId);
        boolean allowed = ServerUtilitiesWorldEventHandler.canExplosionAffect(
                world,
                targetChunkPos,
                ServerUtilitiesWorldEventHandler.getCurrentExplosion());

        if (!allowed && ServerUtilitiesConfig.world.log_hbm_explosion_checks) {
            ServerUtilities.LOGGER.info(
                    "[ServerUtilities] HBM {} blocked in protected chunk {} at ({}, {}, {})",
                    path,
                    targetChunkPos,
                    x,
                    y,
                    z);
        }

        return !allowed;
    }
}
