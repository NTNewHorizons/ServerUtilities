package serverutils.mixins.late.hbm;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Iterator;

import net.minecraft.world.ChunkPosition;
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
import serverutils.handlers.ServerUtilitiesWorldEventHandler;
import serverutils.lib.math.ChunkDimPos;
import serverutils.data.ClaimedChunks;

@Pseudo
@Mixin(targets = "com.hbm.explosion.vanillant.ExplosionVNT")
public abstract class MixinHbmExplosionVNT {

    @Inject(method = "explode()V", at = @At("HEAD"), remap = false)
    private void serverutilities$setVntExplosionContext(CallbackInfo ci) {
        Explosion compat = (Explosion) getCompatExplosion(this);
        ServerUtilitiesWorldEventHandler.setCurrentExplosion(compat);
        if (ServerUtilitiesConfig.world.log_hbm_explosion_checks) {
            ServerUtilities.LOGGER.info(
                    "[ServerUtilities] HBM VNT context set explosion={} thisClass={}",
                    compat,
                    this.getClass().getName());
        }
    }

    @Inject(method = "explode()V", at = @At("RETURN"), remap = false)
    private void serverutilities$clearVntExplosionContext(CallbackInfo ci) {
        if (ServerUtilitiesConfig.world.log_hbm_explosion_checks) {
            ServerUtilities.LOGGER.info(
                    "[ServerUtilities] HBM VNT context cleared previousExplosion={}",
                    ServerUtilitiesWorldEventHandler.getCurrentExplosion());
        }
        ServerUtilitiesWorldEventHandler.clearCurrentExplosion();
    }

    @Redirect(method = "explode()V",
            at = @At(value = "INVOKE",
                    target = "Lcom/hbm/explosion/vanillant/interfaces/IBlockProcessor;process(Lcom/hbm/explosion/vanillant/ExplosionVNT;Lnet/minecraft/world/World;DDDLjava/util/HashSet;)V"),
            remap = false)
    private void serverutilities$filterAffectedBlocksBeforeProcessing(@Coerce Object processor,
            @Coerce Object explosion, World world, double x, double y, double z,
            HashSet<ChunkPosition> affectedBlocks) {

        if (world == null || affectedBlocks == null || affectedBlocks.isEmpty() || !ClaimedChunks.isActive()) {
            invokeProcess(processor, explosion, world, x, y, z, affectedBlocks);
            return;
        }

        Iterator<ChunkPosition> iterator = affectedBlocks.iterator();
        boolean anyRemoved = false;
        Explosion compatExplosion = (Explosion) getCompatExplosion(explosion);
        if (ServerUtilitiesConfig.world.log_hbm_explosion_checks) {
            ServerUtilities.LOGGER.info(
                "[ServerUtilities] HBM VNT block filter reached affectedBlocks={} compatExplosion={} center=({}, {}, {})",
                affectedBlocks.size(),
                compatExplosion,
                x,
                y,
                z);
        }
        while (iterator.hasNext()) {
            ChunkPosition chunkposition = iterator.next();
            ChunkDimPos targetChunkPos = new ChunkDimPos(chunkposition.chunkPosX, chunkposition.chunkPosZ, world.provider.dimensionId);
            if (!ServerUtilitiesWorldEventHandler.canExplosionAffect(world, targetChunkPos, compatExplosion)) {
                iterator.remove();
                anyRemoved = true;
                if (ServerUtilitiesConfig.world.log_hbm_explosion_checks) {
                    ServerUtilities.LOGGER.info(
                            "HBM VNT explosion blocked at chunk {} (block {},{},{}) by ServerUtilities claimed chunk protection",
                            targetChunkPos, chunkposition.chunkPosX, chunkposition.chunkPosY, chunkposition.chunkPosZ);
                }
            }
        }

        if (anyRemoved && compatExplosion != null) {
            removeBlockedFromCompat(compatExplosion, world);
        }

        invokeProcess(processor, explosion, world, x, y, z, affectedBlocks);
    }

    private static void invokeProcess(Object processor, Object explosion, World world, double x, double y, double z, HashSet<ChunkPosition> affectedBlocks) {
        try {
            Method method = findProcessMethod(processor.getClass(), explosion.getClass());
            if (method == null) {
                throw new NoSuchMethodException("Unable to find method process(ExplosionVNT, World, double, double, double, HashSet)");
            }
            method.invoke(processor, explosion, world, x, y, z, affectedBlocks);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static Method findProcessMethod(Class<?> processorClass, Class<?> explosionClass) {
        for (Method method : processorClass.getMethods()) {
            if (!method.getName().equals("process")) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params.length != 6) {
                continue;
            }
            if (params[0].isAssignableFrom(explosionClass)
                    && params[1] == World.class
                    && params[2] == double.class
                    && params[3] == double.class
                    && params[4] == double.class
                    && params[5] == HashSet.class) {
                return method;
            }
        }
        return null;
    }

    private static Object getCompatExplosion(Object explosion) {
        try {
            Field field = explosion.getClass().getField("compat");
            return field.get(explosion);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static void removeBlockedFromCompat(Explosion compat, World world) {
        try {
            Field field = compat.getClass().getField("affectedBlockPositions");
            Object listObj = field.get(compat);
            if (!(listObj instanceof java.util.Collection<?>)) {
                return;
            }
            java.util.Collection<?> list = (java.util.Collection<?>) listObj;
            list.removeIf(affectedPos -> {
                if (!(affectedPos instanceof ChunkPosition)) {
                    return false;
                }
                ChunkPosition pos = (ChunkPosition) affectedPos;
                ChunkDimPos targetChunkPos = new ChunkDimPos(pos.chunkPosX, pos.chunkPosZ, world.provider.dimensionId);
                return !ServerUtilitiesWorldEventHandler.canExplosionAffect(world, targetChunkPos, compat);
            });
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
