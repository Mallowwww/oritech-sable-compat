package com.mallowwww.oritechsablecompat.mixin;

import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import rearth.oritech.block.blocks.processing.MachineCoreBlock;
import rearth.oritech.block.entity.MachineCoreEntity;
import rearth.oritech.block.entity.interaction.LaserArmBlockEntity;
import rearth.oritech.init.OritechConfig;
import rearth.oritech.init.TagContent;
import rearth.oritech.util.MachineAddonController;

import java.util.ArrayDeque;
import java.util.Objects;

@Mixin(value = LaserArmBlockEntity.class, remap = false)
public abstract class EndericLaserBlockEntityMixin extends BlockEntity {

    @Shadow
    private ArrayDeque<BlockPos> pendingArea;
    @Shadow
    private BlockPos targetDirection;
    @Shadow
    public Vec3 laserHead;
    @Shadow
    private final int range = OritechConfig.laserArmConfig.range.get();
    @Shadow
    private BlockPos currentTarget = BlockPos.ZERO;
    @Shadow
    public int areaSize = 1;
    @Shadow
    private int targetBlockEnergyNeeded = OritechConfig.laserArmConfig.blockBreakEnergyBase.get();
    @Shadow
    private MachineAddonController.BaseAddonData addonData = MachineAddonController.BaseAddonData.DEFAULT_ADDON_DATA;
    @Shadow
    private LivingEntity currentLivingTarget;
    @Shadow
    public int hunterAddons = 0;

    private EndericLaserBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    @Shadow
    abstract BlockPos getLaserHeadPosition();

    @Shadow
    abstract boolean trySetNewTarget(BlockPos targetPos, boolean alsoSetDirection);

    @Shadow
    abstract BlockPos basicRaycast(Vec3 from, Vec3 direction, int range, float searchOffset);

    @Shadow
    abstract ArrayDeque<BlockPos> findNextAreaBlockTarget(BlockPos center, int scanDist);

    @Inject(at = @At("HEAD"), method = "basicRaycast", cancellable = true)
    public void basicRaycast(Vec3 from, Vec3 direction, int range, float searchOffset, CallbackInfoReturnable<BlockPos> ci) {
        ci.cancel();

        var result = level.clip(new ClipContext(from.subtract(direction), from.add(direction.scale(300)), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, CollisionContext.empty()));
        ci.setReturnValue(result.getBlockPos());
    }

    @Inject(at = @At("HEAD"), method = "getVisualTarget", cancellable = true)
    public void getVisualTarget(CallbackInfoReturnable<Vec3> ci) {
        ci.cancel();
        if (hunterAddons > 0 && currentLivingTarget != null) {
//            return currentLivingTarget.getEyePosition().subtract(0.5f, 0, 0.5f);
            ci.setReturnValue(Sable.HELPER.projectOutOfSubLevel(level, currentLivingTarget.getEyePosition().subtract(0.5f, 0, 0.5f)));
        } else {
//            return getCurrentTarget().getCenter();
            ci.setReturnValue(Sable.HELPER.projectOutOfSubLevel(level, currentTarget.getCenter()));
        }
    }

    @Inject(at = @At("HEAD"), method = "findNextBlockBreakTarget", cancellable = true)
    public void findNextBlockBreakTarget(CallbackInfo ci) {
        ci.cancel();

        while (pendingArea != null && !pendingArea.isEmpty()) {
            if (trySetNewTarget(pendingArea.pop(), false)) {
                if (pendingArea.isEmpty()) pendingArea = null;
                return;
            }
        }
        var targetDirVec3 = targetDirection.getCenter();
        var laserHeadPosVec3 = getLaserHeadPosition().getCenter();
        var direction = targetDirVec3.subtract(laserHeadPosVec3);
        var from = laserHead.add(direction.scale(1.5));

        var nextBlock = basicRaycast(from, direction, range, 0.45F);
        if (nextBlock == null) {
            currentTarget = BlockPos.ZERO;
            return;
        }

        var maxSize = (int) from.distanceTo(nextBlock.getCenter()) - 1;
        var scanDist = Math.min(areaSize, maxSize);
        if (scanDist > 1)
            pendingArea = findNextAreaBlockTarget(nextBlock, scanDist);


        if (!trySetNewTarget(nextBlock, false)) {
            currentTarget = BlockPos.ZERO;   // out of range or invalid for another reason
        }

    }

    @Unique
    private static float manhattanDist(Vec3 a, Vec3 b) {
        return (float) Math.abs((b.x - a.x) + (b.y - a.y) + (b.z - a.z));
    }

    @Inject(at = @At("HEAD"), method = "trySetNewTarget", cancellable = true)
    public void trySetNewTarget(BlockPos targetPos, boolean alsoSetDirection, CallbackInfoReturnable<Boolean> ci) {
        ci.cancel();
        System.out.println("Got to mixin");
        // if target is coreblock, adjust it to point to controller if connected
        var targetState = Objects.requireNonNull(level).getBlockState(targetPos);
        if (targetState.getBlock() instanceof MachineCoreBlock && targetState.getValue(MachineCoreBlock.USED)) {
            var coreEntity = (MachineCoreEntity) level.getBlockEntity(targetPos);
            var controllerPos = Objects.requireNonNull(coreEntity).getControllerPos();
            if (controllerPos != null) targetPos = controllerPos;
        }
        var targetPosVec3 = Sable.HELPER.projectOutOfSubLevel(level, targetPos.getCenter());
        var worldPositionVec3 = Sable.HELPER.projectOutOfSubLevel(level, worldPosition.getCenter());
        System.out.println("target: " + targetPosVec3 + " " + targetPos);
        System.out.println("world pos: " + worldPositionVec3 + " " + worldPosition);
//        var distance = targetPos.distManhattan(worldPosition);
        var distance = manhattanDist(targetPosVec3, worldPositionVec3);
        var blockHardness = targetState.getBlock().defaultDestroyTime();
        if (distance > range || blockHardness < 0.0 || targetState.getBlock().equals(Blocks.AIR)) {
            System.out.println("Out of range");
            ci.setReturnValue(false);
            return;
        }
        System.out.println("Made it past range check");

        this.targetBlockEnergyNeeded = (int) (OritechConfig.laserArmConfig.blockBreakEnergyBase.get() * Math.pow(blockHardness, OritechConfig.blockBreakHardnessExponentialFactor.get()) * addonData.efficiency());

        if (targetState.is(TagContent.LASER_FAST_BREAKING))
            targetBlockEnergyNeeded /= 8;

        this.currentTarget = targetPos;

        if (alsoSetDirection) {
            this.targetDirection = targetPos;
            pendingArea = null;
            setChanged();
            System.out.println("Set direction");
        }
        this.setChanged();

        ci.setReturnValue(true);
    }
}
