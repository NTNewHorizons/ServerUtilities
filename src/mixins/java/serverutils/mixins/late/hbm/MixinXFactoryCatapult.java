package serverutils.mixins.late.hbm;

import java.lang.reflect.Method;

import net.minecraft.world.Explosion;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import serverutils.ServerUtilities;
import serverutils.ServerUtilitiesConfig;
import serverutils.data.ClaimedChunks;
import serverutils.handlers.ServerUtilitiesWorldEventHandler;
import serverutils.lib.math.ChunkDimPos;

@Pseudo
@Mixin(targets = "com.hbm.items.weapon.sedna.factory.XFactoryCatapult", remap = false)
public abstract class MixinXFactoryCatapult {

    @Inject(method = "incrementRad(Lnet/minecraft/world/World;DDDF)V", at = @At("HEAD"), remap = false)
    private static void serverutilities$logCatapultRadiationEntry(World world, double posX, double posY, double posZ,
            float mult, CallbackInfo ci) {
        if (!ServerUtilitiesConfig.world.log_hbm_explosion_checks || world == null) {
            return;
        }

        ChunkDimPos centerChunk = new ChunkDimPos((int) Math.floor(posX), (int) Math.floor(posY),
                (int) Math.floor(posZ), world.provider.dimensionId);
        ServerUtilities.LOGGER.info(
                "[ServerUtilities] HBM catapult incrementRad entered centerChunk={} pos=({}, {}, {}) mult={} claimsActive={} currentExplosion={}",
                centerChunk,
                posX,
                posY,
                posZ,
                mult,
                ClaimedChunks.isActive(),
                ServerUtilitiesWorldEventHandler.getCurrentExplosion());
    }

    @Redirect(
            method = "incrementRad(Lnet/minecraft/world/World;DDDF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/hbm/handler/radiation/ChunkRadiationHandler;incrementRad(Lnet/minecraft/world/World;IIIF)V",
                    remap = false),
            remap = false)
    private static void serverutilities$guardCatapultRadiation(@Coerce Object handler, World world, int x, int y,
            int z, float radiation) {
        if (handler == null || world == null || world.isRemote || !ClaimedChunks.isActive()) {
            if (ServerUtilitiesConfig.world.log_hbm_explosion_checks) {
                ServerUtilities.LOGGER.info(
                        "[ServerUtilities] HBM catapult radiation passthrough at ({}, {}, {}) reason=handler/world/remote/claimsActive handler={} world={} isRemote={} claimsActive={}",
                        x,
                        y,
                        z,
                        handler != null,
                        world != null,
                        world != null && world.isRemote,
                        ClaimedChunks.isActive());
            }
            if (handler != null) {
                serverutilities$invokeIncrementRad(handler, world, x, y, z, radiation);
            }
            return;
        }

        ChunkDimPos targetChunkPos = new ChunkDimPos(x, y, z, world.provider.dimensionId);
        Explosion explosion = ServerUtilitiesWorldEventHandler.getCurrentExplosion();
        boolean allowed = ServerUtilitiesWorldEventHandler.canExplosionAffect(world, targetChunkPos, explosion);

        if (allowed) {
            serverutilities$invokeIncrementRad(handler, world, x, y, z, radiation);
            return;
        }

        if (ServerUtilitiesConfig.world.log_hbm_explosion_checks) {
            ServerUtilities.LOGGER.info(
                    "[ServerUtilities] HBM catapult radiation blocked in protected chunk {} at ({}, {}, {})",
                    targetChunkPos,
                    x,
                    y,
                    z);
        }
    }

    private static void serverutilities$invokeIncrementRad(Object handler, World world, int x, int y, int z,
            float radiation) {
        try {
            Method incrementRad = handler.getClass().getMethod("incrementRad", World.class, int.class, int.class,
                    int.class, float.class);
            incrementRad.invoke(handler, world, x, y, z, radiation);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to invoke HBM ChunkRadiationHandler.incrementRad", e);
        }
    }
}
