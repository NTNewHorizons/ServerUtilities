package serverutils.mixins.late.hbm;

import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import serverutils.ServerUtilities;
import serverutils.ServerUtilitiesConfig;
import serverutils.handlers.ServerUtilitiesWorldEventHandler;
import serverutils.lib.math.ChunkDimPos;

@Pseudo
@Mixin(targets = "com.hbm.explosion.ExplosionNT", remap = false)
public abstract class MixinExplosionNT {

    @Redirect(
            method = "func_77279_a(Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/hbm/world/WorldUtil;canExplosionAffect(Lnet/minecraft/world/World;III)Z",
                    remap = false),
            remap = false)
    private boolean serverutilities$checkBlockDestructibility(World world, int x, int y, int z) {
        if (world == null || world.isRemote) {
            return true;
        }

        if (ServerUtilitiesConfig.world.log_hbm_explosion_checks) {
            ServerUtilities.LOGGER.info(
                    "[ServerUtilities] HBM explosion hook reached at ({}, {}, {}) dim={} currentExplosion={}",
                    x,
                    y,
                    z,
                    world.provider.dimensionId,
                    ServerUtilitiesWorldEventHandler.getCurrentExplosion());
        }

        ChunkDimPos targetChunkPos = new ChunkDimPos(x, y, z, world.provider.dimensionId);
        boolean allowed = ServerUtilitiesWorldEventHandler.canExplosionAffect(
                world,
                targetChunkPos,
                ServerUtilitiesWorldEventHandler.getCurrentExplosion());

        if (ServerUtilitiesConfig.world.log_hbm_explosion_checks) {
            ServerUtilities.LOGGER.info(
                    "[ServerUtilities] HBM explosion decision at ({}, {}, {}) dim={} -> {}",
                    x,
                    y,
                    z,
                    world.provider.dimensionId,
                    allowed);
        }

        if (!allowed && ServerUtilitiesConfig.world.log_hbm_explosion_checks) {
            System.out.println("[ServerUtilities] Blocked HBM Nuke Ray at X: " + x + " Z: " + z);
        }

        return allowed;
    }
}
