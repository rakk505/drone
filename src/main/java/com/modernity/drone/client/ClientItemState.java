package com.modernity.drone.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.modernity.drone.entity.DroneEntity;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/** Client-only details used while constructing item tooltips. */
public final class ClientItemState {
    private ClientItemState() {
    }

    public static boolean shiftDown() {
        Minecraft minecraft = Minecraft.getInstance();
        return InputConstants.isKeyDown(minecraft.getWindow(), InputConstants.KEY_LSHIFT)
                || InputConstants.isKeyDown(minecraft.getWindow(), InputConstants.KEY_RSHIFT);
    }

    /** @return 1 for powered, 0 for unpowered, and -1 when the drone is not loaded. */
    public static int linkedDronePower(UUID id) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return -1;
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof DroneEntity drone && drone.getUUID().equals(id) && drone.isAlive()) {
                return drone.hasBattery() ? 1 : 0;
            }
        }
        return -1;
    }
}
