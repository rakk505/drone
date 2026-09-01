package com.modernity.drone.entity;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server-side durable-in-memory ownership index used by linked remotes and goggles. */
public final class DroneLinkManager {
    private static final Map<UUID, UUID> LINKS = new ConcurrentHashMap<>();

    private DroneLinkManager() {
    }

    public static void link(UUID droneId, UUID ownerId) {
        if (droneId != null && ownerId != null) LINKS.put(droneId, ownerId);
    }

    public static void unlink(UUID droneId) {
        if (droneId != null) LINKS.remove(droneId);
    }

    public static boolean isLinked(UUID droneId) {
        return droneId != null && LINKS.containsKey(droneId);
    }

    public static UUID owner(UUID droneId) {
        return LINKS.get(droneId);
    }

    public static Set<UUID> drones() {
        return Set.copyOf(LINKS.keySet());
    }

    public static void clear() {
        LINKS.clear();
    }
}
