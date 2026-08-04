package serverutils.mixins.late.hbm;

import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import serverutils.handlers.ServerUtilitiesWorldEventHandler;
import serverutils.lib.math.ChunkDimPos;

@Pseudo
@Mixin(targets = "com.hbm.world.WorldUtil")
public abstract class MixinHbmWorldUtilExplosions {

    private static long serverutilities$cacheWorldTime = Long.MIN_VALUE;
    private static int serverutilities$cacheDim;
    private static int serverutilities$cacheChunkX;
    private static int serverutilities$cacheChunkZ;
    private static int serverutilities$cacheExplosionId;
    private static boolean serverutilities$cacheAllowed;
    private static boolean serverutilities$cacheValid;

    @Inject(
            method = "canExplosionAffect(Lnet/minecraft/world/World;III)Z",
            at = @At("HEAD"),
            remap = false,
            cancellable = true)
    private static void serverutilities$guardHbmExplosion(World world, int x, int y, int z,
            CallbackInfoReturnable<Boolean> cir) {
        if (world == null || world.isRemote) {
            return;
        }

        int dim = world.provider.dimensionId;
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        Object currentExplosion = ServerUtilitiesWorldEventHandler.getCurrentExplosion();
        int explosionId = currentExplosion == null ? 0 : System.identityHashCode(currentExplosion);
        long worldTime = world.getTotalWorldTime();

        if (serverutilities$cacheValid
                && serverutilities$cacheWorldTime == worldTime
                && serverutilities$cacheDim == dim
                && serverutilities$cacheChunkX == chunkX
                && serverutilities$cacheChunkZ == chunkZ
                && serverutilities$cacheExplosionId == explosionId) {
            cir.setReturnValue(serverutilities$cacheAllowed);
            return;
        }

        ChunkDimPos targetChunkPos = new ChunkDimPos(x, y, z, dim);
        boolean allowed = ServerUtilitiesWorldEventHandler.canExplosionAffect(
                world,
                targetChunkPos,
                ServerUtilitiesWorldEventHandler.getCurrentExplosion());

        serverutilities$cacheWorldTime = worldTime;
        serverutilities$cacheDim = dim;
        serverutilities$cacheChunkX = chunkX;
        serverutilities$cacheChunkZ = chunkZ;
        serverutilities$cacheExplosionId = explosionId;
        serverutilities$cacheAllowed = allowed;
        serverutilities$cacheValid = true;

        cir.setReturnValue(allowed);
    }
}
