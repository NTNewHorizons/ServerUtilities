package serverutils.mixins.late.hbm;

import java.lang.reflect.Method;

import net.minecraft.entity.EntityLivingBase;
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
@Mixin(targets = "com.hbm.explosion.ExplosionHurtUtil", remap = false)
public abstract class MixinExplosionHurtUtil {

    @Redirect(
            method = "doRadiation(Lnet/minecraft/world/World;DDDFFD)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/hbm/util/ContaminationUtil;contaminate(Lnet/minecraft/entity/EntityLivingBase;Lcom/hbm/util/ContaminationUtil$HazardType;Lcom/hbm/util/ContaminationUtil$ContaminationType;F)V",
                    remap = false),
            remap = false)
    private static void serverutilities$guardRadiation(EntityLivingBase entity, Object hazardType,
            Object contaminationType, float radiation) {
        if (entity == null || !ClaimedChunks.isActive()) {
            serverutilities$invokeContaminate(entity, hazardType, contaminationType, radiation);
            return;
        }

        World world = entity.worldObj;
        if (world == null || world.isRemote) {
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
                    "[ServerUtilities] HBM radiation blocked for entity {} in protected chunk {}",
                    entity.getCommandSenderName(),
                    targetChunkPos);
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
}
