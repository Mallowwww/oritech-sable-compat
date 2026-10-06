package com.mallowwww.oritechsablecompat.mixin;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rearth.oritech.block.blocks.reactor.NukeBlock;
import rearth.oritech.init.BlockContent;
import rearth.oritech.init.OritechConfig;

@Mixin(NukeBlock.class)
public abstract class NukeBlockMixin extends Block {
    @Shadow
    @Final
    private final boolean small = true;

    private NukeBlockMixin(Properties properties) {
        super(properties);
    }
    @Inject(method = "primeTnt", at = @At("HEAD"), cancellable = true)
    public void primeTnt(Level world, BlockPos pos, CallbackInfo ci) {
        if (Sable.HELPER.isInPlotGrid(world, pos)) {
            ci.cancel();
            if (OritechConfig.boringNukes.get()) {
                var center = Sable.HELPER.projectOutOfSubLevel(world, pos.getCenter());
                world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                world.explode(null, center.x, center.y, center.z, 3, true, Level.ExplosionInteraction.TNT);
                world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.LAVA_POP, SoundSource.BLOCKS, 1.0F, 1.0F);
                return;
            }

            var target = small ? BlockContent.REACTOR_EXPLOSION_MEDIUM : BlockContent.REACTOR_EXPLOSION_LARGE;
            world.setBlockAndUpdate(pos, target.defaultBlockState());
            world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }
}
