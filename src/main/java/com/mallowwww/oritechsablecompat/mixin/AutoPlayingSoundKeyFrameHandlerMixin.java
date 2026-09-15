package com.mallowwww.oritechsablecompat.mixin;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rearth.oritech.init.OritechConfig;
import rearth.oritech.util.AutoPlayingSoundKeyframeHandler;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.keyframe.event.SoundKeyframeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

@Mixin(AutoPlayingSoundKeyframeHandler.class)
public abstract class AutoPlayingSoundKeyFrameHandlerMixin<A extends GeoAnimatable> implements AnimationController.SoundKeyframeHandler<A>{
    @Shadow
    @Final
    private final Map<ResourceLocation, Long> lastPlayedAt = new HashMap<>();
    @Shadow
    @Final
    private final Supplier<Float> speedSupplier = () -> 1f;

    private AutoPlayingSoundKeyFrameHandlerMixin() {
    }


    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    public void handle(SoundKeyframeEvent<A> event, CallbackInfo ci) {
        ci.cancel();
        var segments = event.getKeyframeData().getSound().split("\\|");
        var sound = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse(segments[0]));

        if (sound != null) {

            var time = Minecraft.getInstance().player.clientLevel.getGameTime();
            var age = time - lastPlayedAt.getOrDefault(sound.getLocation(), 0L);
            if (age < 30) return;  // don't play sounds if we just played it

            var pos = Minecraft.getInstance().player.getEyePosition();
            if (event.getAnimatable() instanceof BlockEntity blockEntity) {
                pos = blockEntity.getBlockPos().getCenter();
                if (SableCompanion.INSTANCE.isInPlotGrid(blockEntity))
                    pos = Sable.HELPER.projectOutOfSubLevel(blockEntity.getLevel(), pos);
            }

            var distance = Math.sqrt(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(pos));
            var volumeFalloff = Math.min(1f, 1f / (distance / 4f));
            if (distance > 25) return;
            var speed = speedSupplier.get();
            speed = Math.min(Math.max(speed, 0.125f), 8f);

            var random = Minecraft.getInstance().level.random;

            var volume = segments.length > 1 ? Float.parseFloat(segments[1]) : 1f;
            volume *= (float) (OritechConfig.machineVolumeMultiplier.get() * getPitchRandomMultiplier(random) * volumeFalloff * 0.5f);
            var pitch = segments.length > 2 ? Float.parseFloat(segments[2]) : 1f;
            pitch *= speed * getPitchRandomMultiplier(random);
            var source = SoundSource.BLOCKS;

            Minecraft.getInstance().player.clientLevel.playLocalSound(BlockPos.containing(pos), sound, source, volume, pitch, true);

            lastPlayedAt.put(sound.getLocation(), time);
        }
    }
    @Shadow
    private float getPitchRandomMultiplier(RandomSource random) {
        return random.nextFloat() * 0.15f + 1;
    }

}
