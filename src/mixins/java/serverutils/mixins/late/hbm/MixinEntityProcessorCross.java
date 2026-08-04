package serverutils.mixins.late.hbm;

import java.lang.reflect.Field;

import net.minecraft.entity.Entity;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import serverutils.ServerUtilities;
import serverutils.ServerUtilitiesConfig;
import serverutils.data.ClaimedChunks;
import serverutils.handlers.ServerUtilitiesWorldEventHandler;
import serverutils.lib.math.ChunkDimPos;

@Pseudo
@Mixin(targets = {
        "com.hbm.explosion.vanillant.standard.EntityProcessorCross",
        "com.hbm.explosion.vanillant.standard.EntityProcessorCrossSmooth" }, remap = false)
public abstract class MixinEntityProcessorCross {

    @Inject(method = "attackEntity(Lnet/minecraft/entity/Entity;Lcom/hbm/explosion/vanillant/ExplosionVNT;F)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void serverutilities$guardVntEntityDamage(Entity entity, @Coerce Object explosionVnt, float amount,
            CallbackInfo ci) {
        if (entity == null || !ClaimedChunks.isActive()) {
            return;
        }

        World world = entity.worldObj;
        if (world == null || world.isRemote) {
            return;
        }

        ChunkDimPos targetChunkPos = new ChunkDimPos(entity);
        Explosion compatExplosion = serverutilities$getCompatExplosion(explosionVnt);
        if (ServerUtilitiesConfig.world.log_hbm_explosion_checks) {
            ServerUtilities.LOGGER.info(
                    "[ServerUtilities] HBM VNT entity hook reached entity={} chunk={} amount={} compatExplosion={} explosionVntClass={}",
                    entity.getCommandSenderName(),
                    targetChunkPos,
                    amount,
                    compatExplosion,
                    explosionVnt == null ? null : explosionVnt.getClass().getName());
        }
        boolean allowed = ServerUtilitiesWorldEventHandler.canExplosionAffect(world, targetChunkPos, compatExplosion);

        if (allowed) {
            if (ServerUtilitiesConfig.world.log_hbm_explosion_checks) {
                ServerUtilities.LOGGER.info(
                        "[ServerUtilities] HBM VNT entity damage allowed for entity {} in chunk {}",
                        entity.getCommandSenderName(),
                        targetChunkPos);
            }
            return;
        }

        if (ServerUtilitiesConfig.world.log_hbm_explosion_checks) {
            ServerUtilities.LOGGER.info(
                    "[ServerUtilities] HBM VNT entity damage blocked for entity {} in protected chunk {}",
                    entity.getCommandSenderName(),
                    targetChunkPos);
        }

        ci.cancel();
    }

    private static Explosion serverutilities$getCompatExplosion(Object explosionVnt) {
        if (explosionVnt == null) {
            return null;
        }

        try {
            Field field = explosionVnt.getClass().getField("compat");
            Object value = field.get(explosionVnt);
            return value instanceof Explosion ? (Explosion) value : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
