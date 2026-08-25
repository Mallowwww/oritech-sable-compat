package com.mallowwww.oritechsablecompat.mixin;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import rearth.oritech.api.energy.containers.SimpleEnergyStorage;
import rearth.oritech.block.base.block.MultiblockMachine;
import rearth.oritech.block.blocks.accelerator.AcceleratorControllerBlock;
import rearth.oritech.block.blocks.generators.BigSolarPanelBlock;
import rearth.oritech.block.blocks.interaction.MachineFrameBlock;
import rearth.oritech.block.blocks.pipes.AbstractPipeBlock;
import rearth.oritech.block.blocks.pipes.GenericPipeBlock;
import rearth.oritech.block.blocks.processing.MachineCoreBlock;
import rearth.oritech.block.entity.MachineCoreEntity;
import rearth.oritech.block.entity.accelerator.AcceleratorControllerBlockEntity;
import rearth.oritech.block.entity.pipes.GenericPipeInterfaceEntity;
import rearth.oritech.util.MultiblockMachineController;

import java.util.Collection;

@Mixin(SubLevelAssemblyHelper.class)
public abstract class SubLevelAssemblyHelperMixin {

    @Shadow
    private static void kickFromContainingSubLevel(ServerLevel level, SubLevelPhysicsSystem physicsSystem, PhysicsPipeline pipeline, ServerSubLevel subLevel, SubLevel containingSubLevel) {};

    @Shadow
    private static void moveTrackingPoints(ServerLevel level, BoundingBox3ic bounds, ServerSubLevel subLevel, SubLevelAssemblyHelper.AssemblyTransform transform) {};

    @Shadow
    private static void moveOtherStuff(ServerLevel level, SubLevelAssemblyHelper.AssemblyTransform transform, Iterable<BlockPos> blocks, BoundingBox3ic bounds) {};

    @Shadow
    private static void moveBlocks(ServerLevel level, SubLevelAssemblyHelper.AssemblyTransform transform, Iterable<BlockPos> blocks) {};

    @Inject(at = @At("HEAD"), method = "assembleBlocks", cancellable = true)
    private static void assembleBlocks(ServerLevel level, BlockPos anchor, Iterable<BlockPos> blocks, BoundingBox3ic bounds, CallbackInfoReturnable<SubLevel> ci) {
        ci.cancel();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        assert container != null;

        SubLevel containingSubLevel = Sable.HELPER.getContaining(level, anchor);
        Pose3d pose = new Pose3d();
        pose.position().set((double)anchor.getX() + (double)0.5F, (double)anchor.getY() + (double)0.5F, (double)anchor.getZ() + (double)0.5F);
        if (containingSubLevel != null) {
            Pose3d containingPose = containingSubLevel.logicalPose();
            containingPose.transformPosition(pose.position());
            pose.orientation().set(containingPose.orientation());
        }

        ServerSubLevel subLevel = (ServerSubLevel)container.allocateNewSubLevel(pose);
        LevelPlot plot = subLevel.getPlot();
        plot.newEmptyChunk(plot.getCenterChunk());
        BlockPos plotAnchor = plot.getCenterBlock();
        SubLevelAssemblyHelper.AssemblyTransform transform = new SubLevelAssemblyHelper.AssemblyTransform(anchor, plotAnchor, 0, Rotation.NONE, level);
        moveOtherStuff(level, transform, blocks, bounds);
        moveBlocks(level, transform, blocks);
        Vector3dc centerOfMass = subLevel.getMassTracker().getCenterOfMass();
        Vec3 subLevelCenter = Vec3.atLowerCornerOf(anchor);
        if (centerOfMass != null) {
            subLevelCenter = subLevelCenter.subtract(Vec3.atLowerCornerOf(plotAnchor)).add(centerOfMass.x(), centerOfMass.y(), centerOfMass.z());
        } else {
            subLevel.logicalPose().rotationPoint().set((double)plotAnchor.getX() + (double)0.5F, (double)plotAnchor.getY() + (double)0.5F, (double)plotAnchor.getZ() + (double)0.5F);
        }

        subLevel.logicalPose().position().set(subLevelCenter.x, subLevelCenter.y, subLevelCenter.z);
        SubLevelPhysicsSystem physicsSystem = container.physicsSystem();
        PhysicsPipeline pipeline = physicsSystem.getPipeline();
        if (containingSubLevel != null) {
            kickFromContainingSubLevel(level, physicsSystem, pipeline, subLevel, containingSubLevel);
        }

        pipeline.teleport(subLevel, subLevel.logicalPose().position(), subLevel.logicalPose().orientation());
        subLevel.updateLastPose();
        moveTrackingPoints(level, bounds, subLevel, transform);
        ci.setReturnValue(subLevel);

        // My code starts here
        blocks.forEach(pos -> {
            var state = level.getBlockState(transform.apply(pos));
            if (state.getBlock() instanceof AbstractPipeBlock pipeBlock) {
                var isInterface = pipeBlock.hasNeighboringMachine(state, level, transform.apply(pos), false);
                GenericPipeInterfaceEntity.addNode(level, transform.apply(pos), isInterface, state, pipeBlock.getNetworkData(level));
                level.setBlockAndUpdate(transform.apply(pos), state);
                pipeBlock.getNetworkData(level).setDirty();
                return;
            }
//            if (state.getBlock() instanceof MachineCoreBlock machineCoreBlock) {
//
//                var entity = (MachineCoreEntity) level.getBlockEntity(transform.apply(pos));
//                if (entity == null) return;
//                entity.setControllerPos(transform.apply(entity.getControllerPos()));
//                var controller = entity.getCachedController();
//                if (controller == null) return;
//                controller.onCoreBroken(transform.apply(pos));
//                return;
//            }
            var entity = level.getBlockEntity(transform.apply(pos));
            if (entity instanceof MultiblockMachineController controller) {
                controller.getConnectedCores().forEach(corePos -> {
                    level.getServer().execute(() -> {
                        var coreState = level.getBlockState(transform.apply(corePos));
                        coreState = coreState.setValue(MachineCoreBlock.USED, false);
                        level.setBlock(transform.apply(corePos), coreState, Block.UPDATE_ALL);
                        controller.onCoreBroken(transform.apply(corePos));

                    });
                });
                controller.getConnectedCores().clear();
                level.setBlock(transform.apply(pos), state.setValue(MultiblockMachine.ASSEMBLED, false), Block.UPDATE_ALL);
                level.getServer().execute(() -> {
                    controller.initMultiblock(level.getBlockState(transform.apply(pos)));
                });

            }


        });
    }
}
