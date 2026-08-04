package serverutils.mixins.late.hbm;

import net.minecraft.block.Block;
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
@Mixin(targets = "com.hbm.handler.radiation.ChunkRadiationHandlerSimple", remap = false)
public abstract class MixinChunkRadiationHandlerSimple {

    @Inject(
            method = "setRadiation(Lnet/minecraft/world/World;IIIF)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void serverutilities$guardRadiationStorage(World world, int x, int y, int z, float radiation,
            CallbackInfo ci) {
        if (world == null || world.isRemote || !ClaimedChunks.isActive()) {
            return;
        }

        ChunkDimPos targetChunkPos = new ChunkDimPos(x, y, z, world.provider.dimensionId);
        if (!ServerUtilitiesWorldEventHandler.canExplosionAffect(world, targetChunkPos,
                ServerUtilitiesWorldEventHandler.getCurrentExplosion())) {
            ci.cancel();
        }
    }

    @Inject(
            method = "incrementRad(Lnet/minecraft/world/World;IIIF)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void serverutilities$guardRadiationDeposit(World world, int x, int y, int z, float radiation,
            CallbackInfo ci) {
        if (world == null || world.isRemote || !ClaimedChunks.isActive()) {
            return;
        }

        ChunkDimPos targetChunkPos = new ChunkDimPos(x, y, z, world.provider.dimensionId);
        if (!ServerUtilitiesWorldEventHandler.canExplosionAffect(world, targetChunkPos,
                ServerUtilitiesWorldEventHandler.getCurrentExplosion())) {
            ci.cancel();
        }
    }

    @Redirect(
            method = "handleWorldDestruction()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;setBlock(IIILnet/minecraft/block/Block;)Z",
                    remap = false),
            remap = false)
    private boolean serverutilities$guardRadiationWorldMutation(World world, int x, int y, int z, Block block) {
        if (world == null || world.isRemote || !ClaimedChunks.isActive()) {
            return world != null && world.setBlock(x, y, z, block);
        }

        ChunkDimPos targetChunkPos = new ChunkDimPos(x, y, z, world.provider.dimensionId);
        boolean allowed = ServerUtilitiesWorldEventHandler.canExplosionAffect(
                world,
                targetChunkPos,
                ServerUtilitiesWorldEventHandler.getCurrentExplosion());

        if (allowed) {
            return world.setBlock(x, y, z, block);
        }

        if (ServerUtilitiesConfig.world.log_hbm_explosion_checks) {
            ServerUtilities.LOGGER.info(
                    "[ServerUtilities] HBM radiation world mutation blocked in protected chunk {} at ({}, {}, {})",
                    targetChunkPos,
                    x,
                    y,
                    z);
        }
        return false;
    }
}
