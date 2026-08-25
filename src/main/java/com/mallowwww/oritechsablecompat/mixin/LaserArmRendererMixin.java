package com.mallowwww.oritechsablecompat.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.debug.DebugRenderer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rearth.oritech.block.entity.interaction.LaserArmBlockEntity;
import rearth.oritech.client.renderers.LaserArmRenderer;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;

@Mixin(LaserArmRenderer.class)
public class LaserArmRendererMixin {

    @Inject(at = @At("HEAD"), method = "postRender(Lcom/mojang/blaze3d/vertex/PoseStack;Lrearth/oritech/block/entity/interaction/LaserArmBlockEntity;Lsoftware/bernie/geckolib/cache/object/BakedGeoModel;Lnet/minecraft/client/renderer/MultiBufferSource;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZFIII)V")
    private static <T extends LaserArmBlockEntity & GeoAnimatable> void postRender(PoseStack matrices, T laserEntity, BakedGeoModel model, MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour, CallbackInfo ci) {
        var head = laserEntity.laserHead;
        DebugRenderer.renderFloatingText(matrices, bufferSource, "Targeting: " + laserEntity.getCurrentTarget(), head.x, head.y + 2.5f, head.z, 0xFFFFFFFF);

    }
}
