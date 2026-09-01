package com.modernity.drone.mixin;

import com.modernity.drone.entity.DroneEntity;
import com.modernity.drone.entity.DroneViewSessions;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keeps chunk streaming centered on the camera drone without moving the pilot body. */
@Mixin(net.minecraft.server.level.ChunkMap.class)
public abstract class ChunkMapMixin {
    @Redirect(
            method = "move",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/SectionPos;of(Lnet/minecraft/world/level/entity/EntityAccess;)Lnet/minecraft/core/SectionPos;"
            )
    )
    private SectionPos fpvdrone$remoteSectionInMove(EntityAccess original) {
        return remoteSection(original);
    }

    @Redirect(
            method = "updatePlayerPos",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/SectionPos;of(Lnet/minecraft/world/level/entity/EntityAccess;)Lnet/minecraft/core/SectionPos;"
            )
    )
    private SectionPos fpvdrone$remoteLastSection(EntityAccess original) {
        return remoteSection(original);
    }

    @Redirect(
            method = "updateChunkTracking",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;chunkPosition()Lnet/minecraft/world/level/ChunkPos;"
            )
    )
    private ChunkPos fpvdrone$remoteChunk(ServerPlayer player) {
        DroneEntity drone = DroneViewSessions.activeDrone(player);
        return drone == null ? player.chunkPosition() : drone.chunkPosition();
    }

    private static SectionPos remoteSection(EntityAccess original) {
        if (original instanceof ServerPlayer player) {
            DroneEntity drone = DroneViewSessions.activeDrone(player);
            if (drone != null) return SectionPos.of(drone);
        }
        return SectionPos.of(original);
    }
}
