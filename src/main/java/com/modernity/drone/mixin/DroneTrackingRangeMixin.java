package com.modernity.drone.mixin;

import com.modernity.drone.Config;
import com.modernity.drone.entity.DroneEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies the server's observer-range setting to every player and operator drone. */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class DroneTrackingRangeMixin {
    @Shadow
    @Final
    private Entity entity;

    @Inject(method = "getEffectiveRange", at = @At("RETURN"), cancellable = true)
    private void fpvdrone$applyConfiguredObserverRange(CallbackInfoReturnable<Integer> callback) {
        if (entity instanceof DroneEntity) {
            callback.setReturnValue(Config.DRONE_OBSERVER_RANGE.getAsInt());
        }
    }

    @Redirect(
            method = "updatePlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;position()Lnet/minecraft/world/phys/Vec3;"
            )
    )
    private Vec3 fpvdrone$useRemoteObserverPosition(ServerPlayer player) {
        DroneEntity drone = com.modernity.drone.entity.DroneViewSessions.activeDrone(player);
        return drone == null ? player.position() : drone.position();
    }
}
