package com.modernity.drone.client.render;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.GeoRenderState;
import com.modernity.drone.flight.DroneKind;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

public final class DroneRenderState extends EntityRenderState implements GeoRenderState {
    private final Map<DataTicket<?>, Object> geckolibData = new HashMap<>();
    public final ItemStackRenderState item = new ItemStackRenderState();
    public DroneKind kind = DroneKind.MOSQUITO;
    public float yaw;
    public float pitch;
    public float roll;
    public float propellerAngle;
    public float throttle;
    public boolean batteryInstalled;
    public boolean rpgInstalled;
    public boolean thermal;
    public boolean localFirstPerson;

    @Override
    public <D> void addGeckolibData(DataTicket<D> dataTicket, D data) {
        // GeckoLib mixes a concrete implementation of this method and a
        // private data map into EntityRenderState. Since this custom state
        // supplies its own map for the GeoRenderState generic bound, letting
        // the inherited mixin method run would write to the superclass map
        // while getGeckolibData reads this map. Keep every operation on the
        // same store so mandatory entries such as ANIMATABLE_MANAGER survive
        // render-state extraction.
        this.geckolibData.put(dataTicket, data);
    }

    @Override
    public boolean hasGeckolibData(DataTicket<?> dataTicket) {
        return this.geckolibData.containsKey(dataTicket);
    }

    @Override
    public Map<DataTicket<?>, Object> getDataMap() {
        return this.geckolibData;
    }
}
