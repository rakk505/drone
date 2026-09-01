package com.modernity.drone.entity;

import com.modernity.drone.DroneMod;
import com.modernity.drone.item.FpvGogglesItem;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

/**
 * Server-side FPV view centers. The player entity remains at the pilot's body,
 * while ChunkMap mixins use the linked drone as that player's streaming and
 * entity-observation origin.
 */
public final class DroneViewSessions {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private DroneViewSessions() {
    }

    public static boolean start(ServerPlayer player, UUID droneId) {
        if (player == null || droneId == null) return false;
        ServerLevel level = player.level();
        Entity found = level.getEntityInAnyDimension(droneId);
        if (!(found instanceof DroneEntity drone)
                || drone.level() != level
                || !drone.isAlive()
                || !drone.hasBattery()
                || drone.isAutonomous()
                || !ownedBy(player, drone)
                || !wearsLinkedGoggles(player, droneId)) {
            return false;
        }

        Session previous = SESSIONS.get(player.getUUID());
        if (previous != null
                && previous.level == level
                && previous.droneId.equals(droneId)) {
            return true;
        }
        previous = SESSIONS.remove(player.getUUID());
        if (previous != null) releaseBodyTicket(player.getUUID(), previous);
        ChunkPos bodyChunk = player.chunkPosition();
        if (!DroneMod.PILOT_BODY_CHUNK_TICKETS.forceChunk(
                level, player.getUUID(), bodyChunk.x(), bodyChunk.z(), true, true)) {
            return false;
        }
        SESSIONS.put(player.getUUID(), new Session(droneId, level, bodyChunk));
        level.getChunkSource().move(player);
        return true;
    }

    public static void tick(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) return;
        DroneEntity drone = activeDrone(player);
        if (drone == null || !wearsLinkedGoggles(player, session.droneId)) {
            stop(player);
            return;
        }

        ChunkPos bodyChunk = player.chunkPosition();
        if (!bodyChunk.equals(session.bodyChunk)) {
            releaseBodyTicket(player.getUUID(), session);
            if (!DroneMod.PILOT_BODY_CHUNK_TICKETS.forceChunk(
                    session.level, player.getUUID(), bodyChunk.x(), bodyChunk.z(), true, true)) {
                stop(player);
                return;
            }
            session = new Session(session.droneId, session.level, bodyChunk);
            SESSIONS.put(player.getUUID(), session);
        }

        // The physical player does not move while flying, so vanilla has no
        // reason to refresh its chunk/entity tracking center without this call.
        session.level.getChunkSource().move(player);
    }

    public static void stop(ServerPlayer player) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session == null) return;
        // Removing the session first makes the mixin resolve the real body.
        if (!player.isRemoved() && player.level() == session.level) {
            session.level.getChunkSource().move(player);
        }
        releaseBodyTicket(player.getUUID(), session);
    }

    public static void stopAll(MinecraftServer server) {
        for (Map.Entry<UUID, Session> entry : SESSIONS.entrySet()) {
            releaseBodyTicket(entry.getKey(), entry.getValue());
        }
        SESSIONS.clear();
    }

    public static @Nullable DroneEntity activeDrone(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || player.level() != session.level) return null;
        Entity found = session.level.getEntityInAnyDimension(session.droneId);
        if (!(found instanceof DroneEntity drone)
                || !drone.isAlive()
                || drone.isAutonomous()
                || !ownedBy(player, drone)) {
            return null;
        }
        return drone;
    }

    public static boolean isActive(ServerPlayer player) {
        return activeDrone(player) != null;
    }

    private static boolean wearsLinkedGoggles(ServerPlayer player, UUID droneId) {
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        return helmet.getItem() instanceof FpvGogglesItem
                && FpvGogglesItem.getLinkedDroneId(helmet).filter(droneId::equals).isPresent();
    }

    private static boolean ownedBy(ServerPlayer player, DroneEntity drone) {
        UUID owner = drone.getOwnerUuid();
        return owner == null
                ? player.getUUID().equals(DroneLinkManager.owner(drone.getUUID()))
                : owner.equals(player.getUUID());
    }

    private static void releaseBodyTicket(UUID playerId, Session session) {
        DroneMod.PILOT_BODY_CHUNK_TICKETS.forceChunk(
                session.level,
                playerId,
                session.bodyChunk.x(),
                session.bodyChunk.z(),
                false,
                true
        );
    }

    private record Session(UUID droneId, ServerLevel level, ChunkPos bodyChunk) {
    }
}
