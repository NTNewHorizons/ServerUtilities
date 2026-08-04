package serverutils.mixins.late.hbm;

import java.lang.reflect.Method;

import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import serverutils.ServerUtilities;
import serverutils.ServerUtilitiesConfig;
import serverutils.data.ClaimedChunks;
import serverutils.handlers.ServerUtilitiesWorldEventHandler;
import serverutils.lib.math.ChunkDimPos;

@Pseudo
@Mixin(targets = "com.hbm.explosion.ExplosionNukeSmall", remap = false)
public abstract class MixinExplosionNukeSmall {

    @Redirect(
        method = "explode(Lnet/minecraft/world/World;DDDLcom/hbm/explosion/ExplosionNukeSmall$MukeParams;)V",
        at = @At(
            value = "INVOKE",
            target = "Lcom/hbm/explosion/ExplosionNukeGeneric;dealDamage(Lnet/minecraft/world/World;DDDD)V",
            remap = false),
        remap = false)
    private static void serverutilities$guardMiniNukeDirectDamage(World world, double x, double y, double z,
        double radius) {
    if (world == null || world.isRemote || !ClaimedChunks.isActive()) {
        serverutilities$invokeDealDamage(world, x, y, z, radius);
        return;
    }

    ChunkDimPos centerChunk = new ChunkDimPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z),
        world.provider.dimensionId);
    boolean allowed = ServerUtilitiesWorldEventHandler.canExplosionAffect(
        world,
        centerChunk,
        ServerUtilitiesWorldEventHandler.getCurrentExplosion());

    if (allowed) {
        serverutilities$invokeDealDamage(world, x, y, z, radius);
        return;
    }

    if (ServerUtilitiesConfig.world.log_hbm_explosion_checks) {
        ServerUtilities.LOGGER.info(
            "[ServerUtilities] HBM mini-nuke direct damage blocked in protected chunk {}",
            centerChunk);
    }
    }

    @Redirect(
        method = "explode(Lnet/minecraft/world/World;DDDLcom/hbm/explosion/ExplosionNukeSmall$MukeParams;)V",
        at = @At(
            value = "INVOKE",
            target = "Lcom/hbm/world/WorldUtil;loadAndSpawnEntityInWorld(Lnet/minecraft/entity/Entity;)V",
            remap = false),
        remap = false)
    private static void serverutilities$guardMiniNukeMk5Spawn(Object entity) {
    World world = serverutilities$extractEntityWorld(entity);
    if (world == null || world.isRemote || !ClaimedChunks.isActive()) {
        serverutilities$invokeLoadAndSpawnEntityInWorld(entity);
        return;
    }

    ChunkDimPos centerChunk = new ChunkDimPos(serverutilities$extractEntityPosX(entity),
        serverutilities$extractEntityPosY(entity), serverutilities$extractEntityPosZ(entity),
        world.provider.dimensionId);
    boolean allowed = ServerUtilitiesWorldEventHandler.canExplosionAffect(
        world,
        centerChunk,
        ServerUtilitiesWorldEventHandler.getCurrentExplosion());

    if (allowed) {
        serverutilities$invokeLoadAndSpawnEntityInWorld(entity);
        return;
    }

    if (ServerUtilitiesConfig.world.log_hbm_explosion_checks) {
        ServerUtilities.LOGGER.info(
            "[ServerUtilities] HBM mini-nuke MK5 spawn blocked in protected chunk {}",
            centerChunk);
    }
    }

    @Redirect(
            method = "explode(Lnet/minecraft/world/World;DDDLcom/hbm/explosion/ExplosionNukeSmall$MukeParams;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/hbm/handler/radiation/ChunkRadiationHandler;incrementRad(Lnet/minecraft/world/World;IIIF)V",
                    remap = false),
            remap = false)
    private static void serverutilities$guardMiniNukeRadiation(Object handler, World world, int x, int y,
            int z, float radiation) {
        if (handler == null || world == null || world.isRemote || !ClaimedChunks.isActive()) {
            if (handler != null) {
                serverutilities$invokeIncrementRad(handler, world, x, y, z, radiation);
            }
            return;
        }

        ChunkDimPos targetChunkPos = new ChunkDimPos(x, y, z, world.provider.dimensionId);
        boolean allowed = ServerUtilitiesWorldEventHandler.canExplosionAffect(
                world,
                targetChunkPos,
                ServerUtilitiesWorldEventHandler.getCurrentExplosion());

        if (allowed) {
            serverutilities$invokeIncrementRad(handler, world, x, y, z, radiation);
            return;
        }

        if (ServerUtilitiesConfig.world.log_hbm_explosion_checks) {
            ServerUtilities.LOGGER.info(
                    "[ServerUtilities] HBM mini-nuke radiation blocked in protected chunk {} at ({}, {}, {})",
                    targetChunkPos,
                    x,
                    y,
                    z);
        }
    }

    private static void serverutilities$invokeDealDamage(World world, double x, double y, double z, double radius) {
        try {
            Class<?> explosionNukeGenericClass = Class.forName("com.hbm.explosion.ExplosionNukeGeneric");
            Method method = explosionNukeGenericClass.getMethod("dealDamage", World.class, double.class,
                    double.class, double.class, double.class);
            method.invoke(null, world, x, y, z, radius);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to invoke HBM ExplosionNukeGeneric.dealDamage", e);
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

    private static void serverutilities$invokeLoadAndSpawnEntityInWorld(Object entity) {
        try {
            Class<?> worldUtilClass = Class.forName("com.hbm.world.WorldUtil");
            Class<?> entityClass = Class.forName("net.minecraft.entity.Entity");
            Method method = worldUtilClass.getMethod("loadAndSpawnEntityInWorld", entityClass);
            method.invoke(null, entity);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to invoke HBM WorldUtil.loadAndSpawnEntityInWorld", e);
        }
    }

    private static World serverutilities$extractEntityWorld(Object entity) {
        try {
            Object world = entity.getClass().getField("worldObj").get(entity);
            return world instanceof World ? (World) world : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static int serverutilities$extractEntityPosX(Object entity) {
        return serverutilities$extractEntityCoord(entity, "posX");
    }

    private static int serverutilities$extractEntityPosY(Object entity) {
        return serverutilities$extractEntityCoord(entity, "posY");
    }

    private static int serverutilities$extractEntityPosZ(Object entity) {
        return serverutilities$extractEntityCoord(entity, "posZ");
    }

    private static int serverutilities$extractEntityCoord(Object entity, String fieldName) {
        try {
            Object value = entity.getClass().getField(fieldName).get(entity);
            return value instanceof Number ? (int) Math.floor(((Number) value).doubleValue()) : 0;
        } catch (ReflectiveOperationException e) {
            return 0;
        }
    }
}
