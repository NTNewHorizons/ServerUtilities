package serverutils.mixins.late.hbm;

import net.minecraft.world.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import serverutils.ServerUtilities;
import serverutils.ServerUtilitiesConfig;
import serverutils.handlers.ServerUtilitiesWorldEventHandler;

/**
 * Sets the active explosion context for HBM's ExplosionNT.
 *
 * ExplosionNT extends Explosion but overrides doExplosionB with no super call, so the
 * vanilla injection targeting Explosion.doExplosionB never fires for HBM. We inject
 * directly into ExplosionNT.doExplosionB so `this` is an Explosion instance and can
 * be recorded for war checks.
 */
@Pseudo
@Mixin(targets = "com.hbm.explosion.ExplosionNT")
public abstract class MixinExplosionContext extends Explosion {

    // Dummy constructor required by the compiler since we extend Explosion.
    // It is never called — Mixin classes are never instantiated directly.
    protected MixinExplosionContext() {
        super(null, null, 0, 0, 0, 0);
    }

    @Inject(method = "func_77279_a(Z)V", at = @At("HEAD"), remap = false)
    private void serverutilities$setCurrentExplosion(boolean spawnParticles, CallbackInfo ci) {
        ServerUtilitiesWorldEventHandler.setCurrentExplosion(this);
        if (ServerUtilitiesConfig.world.log_hbm_explosion_checks) {
            ServerUtilities.LOGGER.info(
                    "[ServerUtilities] HBM explosion context set: explosion={} this={} spawnParticles={}",
                    this,
                    this,
                    spawnParticles);
        }
    }

    @Inject(method = "func_77279_a(Z)V", at = @At("RETURN"), remap = false)
    private void serverutilities$clearCurrentExplosion(boolean spawnParticles, CallbackInfo ci) {
        if (ServerUtilitiesConfig.world.log_hbm_explosion_checks) {
            ServerUtilities.LOGGER.info(
                    "[ServerUtilities] HBM explosion context cleared: explosion={} this={}",
                    ServerUtilitiesWorldEventHandler.getCurrentExplosion(),
                    this);
        }
        ServerUtilitiesWorldEventHandler.clearCurrentExplosion();
    }
}
