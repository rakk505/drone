package com.modernity.drone.client.gui.osd;

import com.modernity.drone.client.osd.OsdLayout;
import com.modernity.drone.client.osd.OsdLayoutStore;
import com.modernity.drone.entity.DroneEntity;
import java.util.Objects;
import net.minecraft.client.Minecraft;

/** Client-only entry point used by the Betaflight-item interaction hook. */
public final class OsdScreens {
    private OsdScreens() {
    }

    /**
     * Opens a detached editor copy for this drone. No common-side item class needs to link a
     * client screen; the caller should invoke this only from its client interaction event.
     */
    public static void openFor(DroneEntity drone) {
        Objects.requireNonNull(drone, "drone");
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            OsdLayout layout = OsdLayoutStore.get(drone.getUUID()).copy();
            minecraft.gui.setScreen(new OsdBuilderScreen(minecraft.gui.screen(), drone, layout));
        });
    }
}
