package com.modernity.drone.mixin.client;

import com.modernity.drone.client.DroneControlClient;
import com.modernity.drone.entity.DroneEntity;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The original keeps the integrated server running while an armed FPV craft is in flight. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void fpvdrone$drainLockedPilotKeys(CallbackInfo callback) {
        Minecraft minecraft = (Minecraft) (Object) this;
        if (!DroneControlClient.isActive()) return;

        while (minecraft.options.keyDrop.consumeClick()) {
        }
        while (minecraft.options.keySwapOffhand.consumeClick()) {
        }
        for (KeyMapping hotbarKey : minecraft.options.keyHotbarSlots) {
            while (hotbarKey.consumeClick()) {
            }
        }
        while (minecraft.options.keyInventory.consumeClick()) {
        }
    }

    @Inject(method = "isPaused", at = @At("HEAD"), cancellable = true)
    private void fpvdrone$keepArmedFlightRunning(CallbackInfoReturnable<Boolean> callback) {
        DroneEntity drone = DroneControlClient.currentDrone();
        if (drone != null && drone.isArmed()) callback.setReturnValue(false);
    }
}
