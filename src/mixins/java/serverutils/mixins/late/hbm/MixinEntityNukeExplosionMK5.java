package serverutils.mixins.late.hbm;

import java.lang.reflect.Method;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import serverutils.ServerUtilities;
import serverutils.ServerUtilitiesConfig;
import serverutils.data.ClaimedChunks;
import serverutils.handlers.ServerUtilitiesWorldEventHandler;
import serverutils.lib.math.ChunkDimPos;

@Pseudo
@Mixin(targets = "com.hbm.entity.logic.EntityNukeExplosionMK5", remap = false)
public abstract class MixinEntityNukeExplosionMK5 {

    @Inject(method = "func_70071_h_()V", at = @At("HEAD"), cancellable = true, remap = false)
    private void serverutilities$guardMk5TickStart(CallbackInfo ci) {
        Object self = this;
        World world = serverutilities$extractWorld(self);
        if (world == null || world.isRemote || !ClaimedChunks.isActive()) {
            return;
        }

        int x = (int) Math.floor(serverutilities$extractDoubleField(self, "posX"));
        int y = (int) Math.floor(serverutilities$extractDoubleField(self, "posY"));
        int z = (int) Math.floor(serverutilities$extractDoubleField(self, "posZ"));
        ChunkDimPos centerChunk = new ChunkDimPos(x, y, z, world.provider.dimensionId);
        boolean allowed = ServerUtilitiesWorldEventHandler.canExplosionAffect(
                world,
                centerChunk,
                ServerUtilitiesWorldEventHandler.getCurrentExplosion());

        if (allowed) {
            return;
        }

        if (ServerUtilitiesConfig.world.log_hbm_explosion_checks) {
            ServerUtilities.LOGGER.info(
                    "[ServerUtilities] HBM MK5 tick blocked in protected chunk {} (entity halted before processing)",
                    centerChunk);
        }

        serverutilities$invokeSetDead(self);
        ci.cancel();
    }

    @Redirect(
            method = "func_70071_h_()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/hbm/explosion/ExplosionNukeGeneric;dealDamage(Lnet/minecraft/world/World;DDDD)V",
                    remap = false),
            remap = false)
    private void serverutilities$guardMk5BlastDamage(World world, double x, double y, double z, double radius) {
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
        }
    }

    @Redirect(
            method = "func_70071_h_()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;spawnEntityInWorld(Lnet/minecraft/entity/Entity;)Z",
                    remap = false),
            remap = false)
    private boolean serverutilities$guardMk5FalloutSpawn(World world, Entity entity) {
        if (world == null || world.isRemote || !ClaimedChunks.isActive()) {
            return world != null && world.spawnEntityInWorld(entity);
        }

                int cx = entity != null ? (int) Math.floor(entity.posX) : 0;
                int cy = entity != null ? (int) Math.floor(entity.posY) : 0;
                int cz = entity != null ? (int) Math.floor(entity.posZ) : 0;
                ChunkDimPos centerChunk = new ChunkDimPos(cx, cy, cz, world.provider.dimensionId);
        boolean allowed = ServerUtilitiesWorldEventHandler.canExplosionAffect(
                world,
                centerChunk,
                ServerUtilitiesWorldEventHandler.getCurrentExplosion());

        if (allowed) {
            return world.spawnEntityInWorld(entity);
        }

        if (ServerUtilitiesConfig.world.log_hbm_explosion_checks) {
            ServerUtilities.LOGGER.info(
                    "[ServerUtilities] HBM MK5 fallout entity spawn blocked in protected chunk {}",
                    centerChunk);
        }
        return false;
    }

    @Redirect(
            method = "radiate(FD)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/hbm/util/ContaminationUtil;contaminate(Lnet/minecraft/entity/EntityLivingBase;Lcom/hbm/util/ContaminationUtil$HazardType;Lcom/hbm/util/ContaminationUtil$ContaminationType;F)V",
                    remap = false),
            remap = false)
    private void serverutilities$guardMk5Radiation(EntityLivingBase entity, Object hazardType,
            Object contaminationType, float radiation) {
                World world = entity != null ? entity.worldObj : null;
                if (entity == null || world == null || world.isRemote || !ClaimedChunks.isActive()) {
            serverutilities$invokeContaminate(entity, hazardType, contaminationType, radiation);
            return;
        }

        ChunkDimPos targetChunkPos = new ChunkDimPos(entity);
        boolean allowed = ServerUtilitiesWorldEventHandler.canExplosionAffect(
                                world,
                targetChunkPos,
                ServerUtilitiesWorldEventHandler.getCurrentExplosion());

        if (allowed) {
                        serverutilities$invokeContaminate(entity, hazardType, contaminationType, radiation);
            return;
        }

        if (ServerUtilitiesConfig.world.log_hbm_explosion_checks) {
            ServerUtilities.LOGGER.info(
                    "[ServerUtilities] HBM MK5 radiation blocked for entity {} in protected chunk {}",
                    entity.getCommandSenderName(),
                    targetChunkPos);
        }
    }

    private static void serverutilities$invokeDealDamage(World world, double x, double y, double z, double radius) {
        try {
            Class<?> explosionNukeGenericClass = Class.forName("com.hbm.explosion.ExplosionNukeGeneric");
            Method method = explosionNukeGenericClass.getMethod("dealDamage", World.class, double.class, double.class,
                    double.class, double.class);
            method.invoke(null, world, x, y, z, radius);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to invoke HBM ExplosionNukeGeneric.dealDamage", e);
        }
    }

    private static void serverutilities$invokeContaminate(EntityLivingBase entity, Object hazardType,
            Object contaminationType, float radiation) {
        try {
            Class<?> contaminationUtilClass = Class.forName("com.hbm.util.ContaminationUtil");
            for (Method method : contaminationUtilClass.getMethods()) {
                if (!"contaminate".equals(method.getName()) || method.getParameterTypes().length != 4) {
                    continue;
                }
                method.invoke(null, entity, hazardType, contaminationType, radiation);
                return;
            }
            throw new NoSuchMethodException("ContaminationUtil.contaminate with 4 args not found");
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to invoke HBM ContaminationUtil.contaminate", e);
        }
    }

    private static World serverutilities$extractWorld(Object self) {
        try {
            Object world = self.getClass().getField("worldObj").get(self);
            return world instanceof World ? (World) world : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static double serverutilities$extractDoubleField(Object self, String fieldName) {
        try {
            Object value = self.getClass().getField(fieldName).get(self);
            return value instanceof Number ? ((Number) value).doubleValue() : 0D;
        } catch (ReflectiveOperationException e) {
            return 0D;
        }
    }

    private static void serverutilities$invokeSetDead(Object self) {
        try {
            Method method = self.getClass().getMethod("setDead");
            method.invoke(self);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to invoke HBM EntityNukeExplosionMK5.setDead", e);
        }
    }
}
