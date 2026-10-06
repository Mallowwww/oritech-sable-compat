package com.mallowwww.oritechsablecompat.mixin;

import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import rearth.oritech.block.blocks.reactor.NuclearExplosionBlock;
import rearth.oritech.block.entity.reactor.NuclearExplosionEntity;
import rearth.oritech.init.SoundContent;

import java.util.HashSet;
import java.util.Set;

@Mixin(NuclearExplosionEntity.class)
public abstract class NuclearExplosionEntityMixin extends BlockEntity implements BlockEntityTicker<NuclearExplosionEntity> {
    @Shadow
    private long startTime = -1;
    @Shadow
    private final Set<BlockPos> removedBlocks = new HashSet<>();
    @Shadow
    private final Set<BlockPos> borderBlocks = new HashSet<>();
    @Shadow
    private final Set<?> waves = new HashSet<>();
    @Shadow
    @Final
    private final int size = 0;
    @Shadow
    private ServerPlayer nukePlayerEntity = null;


    private NuclearExplosionEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void tick(Level world, BlockPos sublevel_pos, BlockState state, NuclearExplosionEntity blockEntity, CallbackInfo ci) {
        if (Sable.HELPER.isInPlotGrid(world, sublevel_pos)) {
            ci.cancel();
            if (world.isClientSide) return;

            // Do the explosion in the real world, not in plotgrid
            var location = Sable.HELPER.projectOutOfSubLevel(world, sublevel_pos.getCenter());
            var pos = BlockPos.containing(location);

            var initialRadius = size;

            if (startTime == -1) {
                startTime = world.getGameTime();
                explosionSphere(initialRadius + 7, 200, pos);
                explosionSphere(initialRadius + 7, 200, sublevel_pos);
                world.playSound(null, pos, SoundContent.NUKE_EXPLOSION, SoundSource.BLOCKS, 30f, 1f);
            }

            var age = world.getGameTime() - startTime;

            if (age == 1) {
                createExplosionWaves(initialRadius);
            }

            if (age > 1) {
                waves.forEach(wave -> ((DirectionExplosionWaveMixin) wave).yourmod$nextGeneration());
                processBorderBlocks(initialRadius * initialRadius);
            }

            if (age > initialRadius * 2) {
                // done
                world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
        }
    }
    @Shadow
    protected abstract int explosionSphere(int radius, int power, BlockPos pos);
    @Shadow
    protected abstract void processBorderBlocks(int maxDist);
    @Shadow
    protected abstract void createExplosionWaves(int initialRadius);
}
