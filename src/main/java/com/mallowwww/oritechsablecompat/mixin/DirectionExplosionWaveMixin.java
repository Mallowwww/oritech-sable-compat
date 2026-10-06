package com.mallowwww.oritechsablecompat.mixin;

import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "rearth.oritech.block.entity.reactor.NuclearExplosionEntity$DirectionExplosionWave")
public interface DirectionExplosionWaveMixin {
    @Invoker("nextGeneration")
    void yourmod$nextGeneration();

    @Accessor("lastPosition")
    BlockPos yourmod$getLastPosition();

    @Accessor("lastRadius")
    int yourmod$getLastRadius();

}
