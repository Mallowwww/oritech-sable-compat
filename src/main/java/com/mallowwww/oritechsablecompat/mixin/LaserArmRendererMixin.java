package com.mallowwww.oritechsablecompat.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.NeoForgeMod;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rearth.oritech.block.entity.interaction.LaserArmBlockEntity;
import rearth.oritech.client.renderers.LaserArmRenderer;
import rearth.oritech.client.renderers.util.BeamRenderer;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

import java.util.HashMap;

import static rearth.oritech.client.renderers.LaserArmRenderer.*;
import static rearth.oritech.util.Geometry.worldToOffsetPosition;

@Mixin(LaserArmRenderer.class)
public abstract class LaserArmRendererMixin<T extends LaserArmBlockEntity & GeoAnimatable> extends GeoBlockRenderer<T> {

    @Shadow
    private static final Vec3 BEAM_START_OFFSET = new Vec3(0, 1.65, 0);
    @Shadow
    private static final HashMap<Long, Vec3> cachedOffsets = new HashMap<>();

    private LaserArmRendererMixin(GeoModel<T> model) {
        super(model);
    }

    @Inject(at = @At("HEAD"), method = "postRender(Lcom/mojang/blaze3d/vertex/PoseStack;Lrearth/oritech/block/entity/interaction/LaserArmBlockEntity;Lsoftware/bernie/geckolib/cache/object/BakedGeoModel;Lnet/minecraft/client/renderer/MultiBufferSource;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZFIII)V", cancellable = true)
    private <T extends LaserArmBlockEntity & GeoAnimatable> void postRender(PoseStack matrices, T laserEntity, BakedGeoModel model, MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour, CallbackInfo ci) {
        ci.cancel();
        var startPos = Sable.HELPER.projectOutOfSubLevel(laserEntity.getLevel(), laserEntity.laserHead);

        if (Minecraft.getInstance().getDebugOverlay().showDebugScreen())
            DebugRenderer.renderFloatingText(matrices, bufferSource, "Targeting: " + laserEntity.getCurrentTarget(), startPos.x, startPos.y + 2.5f, startPos.z, 0xFFFFFFFF);

        super.postRender(matrices, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);

        if (laserEntity.getCurrentTarget() == null || !laserEntity.isFiring()) return;

        var facing = laserEntity.getBlockState().getValue(BlockStateProperties.FACING);
        var startOffset = new Vec3(0, 1.65f, 0);

        var targetPos = laserEntity.getVisualTarget();
//        System.out.println("pos1: "+startPos);
//        System.out.println("pos2: "+targetPos);

        var targetBlock = laserEntity.getLevel().getBlockState(laserEntity.getCurrentTarget()).getBlock();
        if (laserEntity.isTargetingAtomicForge(targetBlock)) { // adjust so the beam end faces one of the corner pillars
            var moveX = 0.5;
            var moveZ = 0.5;
            if (startPos.x < targetPos.x) moveX = -0.5;
            if (startPos.z < targetPos.z) moveZ = -0.5;
            targetPos = targetPos.add(moveX, 0.2, moveZ);
        } else if (laserEntity.isTargetingDeepdrill(targetBlock)) {
            var offset = cachedOffsets.computeIfAbsent(laserEntity.getBlockPos().asLong(), id -> idToOffset(BlockPos.of(id), 0.5f, laserEntity.getLevel(), laserEntity.getCurrentTarget()));
            targetPos = targetPos.add(offset);
        }

        if (laserEntity.lastRenderPosition == null) laserEntity.lastRenderPosition = targetPos;
//        targetPos = Sable.HELPER.projectOutOfSubLevel(laserEntity.getLevel(), lerp(laserEntity.lastRenderPosition, targetPos, 0.06f));
        targetPos = lerp(laserEntity.lastRenderPosition, targetPos, 0.06f);
        laserEntity.lastRenderPosition = targetPos;

        var targetPosOffset = worldToOffsetPosition(facing, targetPos, startPos).add(startOffset);
//        System.out.println("targetPos: "+targetPos);
//        System.out.println("targetPosOffset"+targetPosOffset);
        var forward = targetPos.subtract(startPos).normalize();
        if (!laserEntity.isTargetingEnergyContainer() && !laserEntity.isTargetingBuddingAmethyst() && laserEntity.getLevel().random.nextFloat() > 0.7) {
            var world = laserEntity.getLevel();
            var p = targetPos.add(0.5, 0, 0.5).subtract(forward.scale(0.6));
            world.addParticle(ParticleTypes.SMALL_FLAME, p.x + (world.random.nextDouble() - 0.5) * 0.8, p.y + (world.random.nextDouble() - 0.5) * 0.6, p.z + (world.random.nextDouble() - 0.5) * 0.8, 0, 0, 0);
        }


        matrices.pushPose();
        var beamConsumer = bufferSource.getBuffer(RenderType.eyes(BEAM_TEXTURE));

        float thickness = (float) (0.03f + Math.sin((laserEntity.getLevel().getGameTime() + partialTick) * 0.3) * 0.015f);

        var deltaVec = targetPosOffset.subtract(startOffset);
//        if (!Sable.HELPER.isInPlotGrid(laserEntity)) {
//            var sublevel = (ClientSubLevel) Sable.HELPER.getContaining(laserEntity);
//            if (sublevel == null) return;
//            deltaVec = new Vec3(sublevel.renderPose().orientation().transformInverse(deltaVec.toVector3f()));
//        }
        var sublevel = (ClientSubLevel) Sable.HELPER.getContaining(laserEntity);
        if (sublevel != null) {
            var pose = sublevel.renderPose();
            var rotation = pose.orientation();
//            matrices.mulPose(new Quaternionf().rotateLocalX((float) -Math.PI / 2f));
            deltaVec = new Vec3(rotation.transformInverse(deltaVec.toVector3f()));

//            matrices.mulPose(startPos.r);
        }

        // glowing core
        BeamRenderer.renderStraightBeam(
                matrices,
                beamConsumer,
                BEAM_START_OFFSET,
                deltaVec,
                thickness * 0.2f,
                LightTexture.FULL_BRIGHT,
                CORE_COLOR_START,
                CORE_COLOR_END
        );

        // outer
        BeamRenderer.renderStraightBeam(
                matrices,
                beamConsumer,
                BEAM_START_OFFSET,
                deltaVec,
                thickness,
                LightTexture.FULL_BRIGHT,
                GLOW_COLOR_START,
                GLOW_COLOR_END
        );

        matrices.popPose();
    }
}
