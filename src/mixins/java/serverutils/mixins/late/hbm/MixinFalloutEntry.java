package serverutils.mixins.late.hbm;

import net.minecraft.block.Block;
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
@Mixin(targets = "com.hbm.config.FalloutConfigJSON$FalloutEntry", remap = false)
public abstract class MixinFalloutEntry {

    @Redirect(
            method = "eval(Lnet/minecraft/world/World;IIILnet/minecraft/block/Block;ID)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;setBlock(IIILnet/minecraft/block/Block;II)Z",
                    remap = false),
            remap = false)
    private boolean serverutilities$guardFalloutConfigMutation(World world, int x, int y, int z, Block block, int meta,
            int flags) {
        if (world == null || world.isRemote || !ClaimedChunks.isActive()) {
            return world != null && world.setBlock(x, y, z, block, meta, flags);
        }

        ChunkDimPos targetChunkPos = new ChunkDimPos(x, y, z, world.provider.dimensionId);
        boolean allowed = ServerUtilitiesWorldEventHandler.canExplosionAffect(
                world,
                targetChunkPos,
                ServerUtilitiesWorldEventHandler.getCurrentExplosion());

        if (allowed) {
            return world.setBlock(x, y, z, block, meta, flags);
        }

        if (ServerUtilitiesConfig.world.log_hbm_explosion_checks) {
            ServerUtilities.LOGGER.info(
                    "[ServerUtilities] HBM fallout conversion blocked in protected chunk {} at ({}, {}, {})",
                    targetChunkPos,
                    x,
                    y,
                    z);
        }
        return false;
    }
}
