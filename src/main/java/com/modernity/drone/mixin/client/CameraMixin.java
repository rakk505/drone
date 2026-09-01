package com.modernity.drone.mixin.client;

import com.modernity.drone.client.DroneControlClient;
import com.modernity.drone.client.FpvCameraTransform;
import com.modernity.drone.entity.DroneEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Places the first-person view at the original camera lens rather than the entity origin. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void fpvdrone$applyLensOffset(float partialTick, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        DroneEntity drone = DroneControlClient.currentDrone();
        if (drone == null
                || minecraft.getCameraEntity() != drone
                || minecraft.options.getCameraType() != CameraType.FIRST_PERSON) {
            return;
        }
        Vec3 position = FpvCameraTransform.position(drone, partialTick);
        setPosition(position.x, position.y, position.z);
    }

    @ModifyConstant(method = "update", constant = @Constant(floatValue = 0.05F))
    private float fpvdrone$useCloseNearPlane(float original) {
        return DroneControlClient.isActive() ? 0.01F : original;
    }
}
