package com.mallowwww.oritechsablecompat.mixin;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import rearth.oritech.block.blocks.pipes.AbstractPipeBlock;
import rearth.oritech.block.blocks.pipes.GenericPipeBlock;

import java.util.List;

@Mixin(GenericPipeBlock.class)
public abstract class GenericPipeBlockMixin extends AbstractPipeBlock {

    private GenericPipeBlockMixin(Properties settings) {
        super(settings);
    }
    @Inject(method = "getInteractDirection", at = @At("HEAD"), cancellable = true)
    private void getInteractDirection(BlockState state, BlockPos pos, Player player, CallbackInfoReturnable<Direction> cir) {
        if (Sable.HELPER.isInPlotGrid(player.level(), pos)) {
            var sublevel = Sable.HELPER.getContaining(player.level(), pos);
            if (sublevel == null) return;
            cir.cancel();
            var shapes = getActiveShapes(state);
            var start = player.getEyePosition(0f);
            var viewVec = player.getViewVector(0);

            start = sublevel.logicalPose().transformPositionInverse(start);
            viewVec = sublevel.logicalPose().transformNormalInverse(viewVec);
            var end = start.add(viewVec.scale(5));

            var targetShape = shapes.getFirst();
            var distance = Double.MAX_VALUE;
            var hitPos = Vec3.ZERO;
            for (var shape : shapes) {
                var hitResult = shape.clip(start, end, pos);
                if (hitResult == null) continue;

                // skip center if we already matched one of the outer ones
                if (shape.equals(shapes.getLast()) && distance < Double.MAX_VALUE) continue;

                var shapeDistance = hitResult.getLocation().distanceTo(start);
                if (shapeDistance < distance) {
                    distance = shapeDistance;
                    targetShape = shape;
                    hitPos = hitResult.getLocation();
                }
            }

            var center = targetShape.bounds().getCenter();
            var diff = center.subtract(new Vec3(0.5, 0.5, 0.5));
            if (diff.equals(Vec3.ZERO))
                // center hit
                diff = hitPos.subtract(center.add(Vec3.atLowerCornerOf(pos)));

            cir.setReturnValue(Direction.getNearest(diff.x, diff.y, diff.z));
        }
    }

    @Shadow
    abstract protected List<VoxelShape> getActiveShapes(BlockState state);
}
