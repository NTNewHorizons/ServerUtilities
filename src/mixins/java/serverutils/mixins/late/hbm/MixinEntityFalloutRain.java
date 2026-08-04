package serverutils.mixins.late.hbm;

import java.lang.reflect.Method;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import serverutils.data.ClaimedChunks;
import serverutils.handlers.ServerUtilitiesWorldEventHandler;
import serverutils.lib.math.ChunkDimPos;

@Pseudo
@Mixin(targets = "com.hbm.entity.effect.EntityFalloutRain", remap = false)
public abstract class MixinEntityFalloutRain {

    @Inject(method = "stomp(IID)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void serverutilities$guardFalloutColumn(int x, int z, double distance, CallbackInfo ci) {
        World world = serverutilities$getCurrentWorld();
        if (world == null || world.isRemote || !ClaimedChunks.isActive()) {
            return;
        }

        ChunkDimPos targetChunkPos = new ChunkDimPos(x, 0, z, world.provider.dimensionId);
        if (!ServerUtilitiesWorldEventHandler.canExplosionAffect(world, targetChunkPos,
                ServerUtilitiesWorldEventHandler.getCurrentExplosion())) {
            ci.cancel();
        }
    }

    @Inject(method = "setBlock(IIILnet/minecraft/block/Block;I)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void serverutilities$guardFalloutBlockMutation(int x, int y, int z, Block block, int meta, CallbackInfo ci) {
        World world = serverutilities$getCurrentWorld();
        if (world == null || world.isRemote || !ClaimedChunks.isActive()) {
            return;
        }

        ChunkDimPos targetChunkPos = new ChunkDimPos(x, y, z, world.provider.dimensionId);
        boolean allowed = ServerUtilitiesWorldEventHandler.canExplosionAffect(
                world,
                targetChunkPos,
                ServerUtilitiesWorldEventHandler.getCurrentExplosion());

        if (!allowed) {
            ci.cancel();
        }
    }

    @Redirect(
            method = "func_70071_h_()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/hbm/world/WorldUtil;setBiome(Lnet/minecraft/world/World;IILnet/minecraft/world/biome/BiomeGenBase;)V",
                    remap = false),
            remap = false)
    private void serverutilities$guardFalloutBiomeMutation(World world, int x, int z, BiomeGenBase biome) {
        if (world == null || world.isRemote || !ClaimedChunks.isActive()) {
            if (world != null) {
                serverutilities$invokeSetBiome(world, x, z, biome);
            }
            return;
        }

        ChunkDimPos targetChunkPos = new ChunkDimPos(x, 0, z, world.provider.dimensionId);
        boolean allowed = ServerUtilitiesWorldEventHandler.canExplosionAffect(
                world,
                targetChunkPos,
                ServerUtilitiesWorldEventHandler.getCurrentExplosion());

        if (allowed) {
            serverutilities$invokeSetBiome(world, x, z, biome);
            return;
        }
    }

    private static void serverutilities$invokeSetBiome(World world, int x, int z, BiomeGenBase biome) {
        try {
            Class<?> worldUtilClass = Class.forName("com.hbm.world.WorldUtil");
            Method setBiome = worldUtilClass.getMethod("setBiome", World.class, int.class, int.class,
                    BiomeGenBase.class);
            setBiome.invoke(null, world, x, z, biome);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to invoke HBM WorldUtil.setBiome", e);
        }
    }

    private World serverutilities$getCurrentWorld() {
        return ((Entity) (Object) this).worldObj;
    }
}
