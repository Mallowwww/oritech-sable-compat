package com.mallowwww.oritechsablecompat.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rearth.oritech.block.base.block.MultiblockMachine;
import rearth.oritech.block.blocks.augmenter.AugmentResearchStationBlock;
import rearth.oritech.block.blocks.processing.RefineryModuleBlock;
import rearth.oritech.block.blocks.storage.LargeStorageBlock;
import rearth.oritech.client.renderers.BlockOutlineRenderer;
import rearth.oritech.init.BlockContent;
import rearth.oritech.init.ItemContent;
import rearth.oritech.init.TagContent;
import rearth.oritech.util.Geometry;
import rearth.oritech.util.MultiblockMachineController;

import java.util.ArrayList;

@Mixin(BlockOutlineRenderer.class)
public class BlockOutlineRendererMixin {

    @Shadow
    private static Direction getFacingFromState(BlockState state) { return null; }

    @Inject(at = @At("HEAD"), method = "renderBlockPlacementPreviewOutline", cancellable = true)
    private static void renderBlockPlacementPreviewOutline(ClientLevel world, Camera camera, PoseStack matrixStack, MultiBufferSource consumer, ItemStack itemStack, LocalPlayer player, BlockPos blockPos, CallbackInfo ci) {
        ci.cancel();
        var hasBlockItem = itemStack.getItem() instanceof BlockItem || itemStack.getItem().equals(ItemContent.UNSTABLE_CONTAINER);

        if (!hasBlockItem) return;

        var block = itemStack.getItem() instanceof BlockItem ? ((BlockItem) itemStack.getItem()).getBlock() : BlockContent.UNSTABLE_CONTAINER;

        if (!(block instanceof EntityBlock entityProvider) || !block.defaultBlockState().hasProperty(MultiblockMachine.ASSEMBLED))
            return;

        var machinePos = blockPos.offset(((BlockHitResult) Minecraft.getInstance().hitResult).getDirection().getNormal());
        if (itemStack.getItem().equals(ItemContent.UNSTABLE_CONTAINER))
            machinePos = blockPos;
        var placementState = block.getStateForPlacement(new BlockPlaceContext(player, player.swingingArm, itemStack, (BlockHitResult) Minecraft.getInstance().hitResult));
        var entity = entityProvider.newBlockEntity(machinePos, placementState);
        if (!(entity instanceof MultiblockMachineController multiblockController)) return;

        if (itemStack.getItem().equals(ItemContent.UNSTABLE_CONTAINER)) {
            var blockState = world.getBlockState(machinePos);
            var isValid = blockState.is(TagContent.UNSTABLE_CONTAINER_SOURCES_LOW) || blockState.is(TagContent.UNSTABLE_CONTAINER_SOURCES_MEDIUM) || blockState.is(TagContent.UNSTABLE_CONTAINER_SOURCES_HIGH);
            if (!isValid) return;
        }

        var coreOffsets = multiblockController.getCorePositions();
        var machineFacing = getFacingFromState(placementState);

        if (block instanceof LargeStorageBlock) {    // the large block is weird
            machineFacing = player.getDirection().getOpposite();
        } else if (block instanceof AugmentResearchStationBlock) {
            machineFacing = player.getNearestViewDirection();
        } else if (!(block instanceof MultiblockMachine || block instanceof RefineryModuleBlock)) {
            machineFacing = machineFacing.getOpposite();
        }

        var fullList = new ArrayList<>(coreOffsets);
        fullList.add(Vec3i.ZERO);

        matrixStack.pushPose();
        var cameraPos = camera.getPosition();
        matrixStack.translate(-cameraPos.x(), -cameraPos.y(), -cameraPos.z());
        matrixStack.translate(0.005f, 0.005f, 0.005f); // slight offset to avoid z fighting
        var sublevel = (ClientSubLevel) SableCompanion.INSTANCE.getContaining(world, blockPos);
        if (sublevel != null) {
            var subLevelMat = sublevel.renderPose().bakeIntoMatrix(new Matrix4d());
//            System.out.println(subLevelMat);
//            matrixStack.mulPose(new Matrix4f(subLevelMat));

        }

        var shape = Shapes.block();
        for (var coreOffset : fullList) {
            var fixedOffset = new Vec3i(coreOffset.getX(), coreOffset.getY(), coreOffset.getZ());
            var worldOffsetTemp = Geometry.offsetToWorldPosition(machineFacing, fixedOffset, machinePos);
            var worldOffset = Sable.HELPER.projectOutOfSubLevel(world, new Vec3(worldOffsetTemp.getX(), worldOffsetTemp.getY(), worldOffsetTemp.getZ()));


            shape = Shapes.or(shape, Shapes.box(worldOffset.x, worldOffset.y, worldOffset.z, worldOffset.x + 1, worldOffset.y + 1, worldOffset.z + 1));

        }

        LevelRenderer.renderShape(matrixStack, consumer.getBuffer(RenderType.lines()), shape, 0, 0, 0, 1f, 1f, 1f, 0.7F);
        matrixStack.popPose();
    }
}
