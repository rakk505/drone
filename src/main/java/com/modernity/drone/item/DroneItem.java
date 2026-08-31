package com.modernity.drone.item;

import com.modernity.drone.DroneMod;
import com.modernity.drone.entity.DroneEntity;
import com.modernity.drone.flight.DroneKind;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

public final class DroneItem extends Item {
    private final DroneKind kind;

    public DroneItem(Properties properties, DroneKind kind) {
        super(properties);
        this.kind = kind;
    }

    public DroneKind kind() {
        return kind;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        DroneEntity entity = DroneMod.DRONE_ENTITY.get().create(serverLevel, EntitySpawnReason.SPAWN_ITEM_USE);
        if (entity == null) {
            return InteractionResult.FAIL;
        }

        Vec3 normal = context.getClickedFace().getUnitVec3();
        Vec3 position = context.getClickLocation().add(normal.scale(0.35));
        entity.snapTo(position.x, position.y, position.z, context.getRotation() + 180.0F, 0.0F);
        entity.configurePlacedDrone(kind, context.getPlayer());
        if (!serverLevel.noCollision(entity)) {
            return InteractionResult.FAIL;
        }
        if (!serverLevel.addFreshEntity(entity)) {
            return InteractionResult.FAIL;
        }
        if (context.getPlayer() == null || !context.getPlayer().hasInfiniteMaterials()) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            TooltipDisplay display,
            Consumer<Component> tooltip,
            TooltipFlag flag
    ) {
        tooltip.accept(Component.translatable(kind == DroneKind.MOSQUITO
                ? "tooltip.drone.mosquito"
                : "tooltip.drone.payload"));
        tooltip.accept(Component.translatable("tooltip.drone.place"));
    }
}
