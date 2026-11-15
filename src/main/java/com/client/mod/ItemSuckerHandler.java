package com.client.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Pulls/moves the player toward nearby dropped items (or packet-blinks in TP mode) to collect them.
 */
public final class ItemSuckerHandler {
    private static int tpCooldownTicks = 0;

    private ItemSuckerHandler() {
    }

    public static void tick(Minecraft mc) {
        ModConfig config = ModConfig.getInstance();
        if (!config.itemSuckerEnabled || mc == null || mc.player == null || mc.level == null) {
            tpCooldownTicks = 0;
            return;
        }
        if (mc.gui.screen() != null || mc.player.isSpectator()) {
            return;
        }
        if (tpCooldownTicks > 0) {
            tpCooldownTicks--;
        }

        double range = config.itemSuckerRange;
        AABB box = mc.player.getBoundingBox().inflate(range);
        List<ItemEntity> items = mc.level.getEntitiesOfClass(ItemEntity.class, box, item -> {
            if (item == null || item.isRemoved()) return false;
            if (config.itemSuckerOnlyPickable && item.hasPickUpDelay()) return false;
            if (config.itemSuckerOnlyOnGround && !item.onGround()) return false;
            return item.distanceToSqr(mc.player) <= range * range;
        });
        if (items.isEmpty()) {
            return;
        }

        ItemEntity nearest = null;
        double bestDist = Double.MAX_VALUE;
        for (ItemEntity item : items) {
            double d = item.distanceToSqr(mc.player);
            if (d < bestDist) {
                bestDist = d;
                nearest = item;
            }
        }
        if (nearest == null || bestDist < 0.8) {
            return;
        }

        if (config.itemSuckerMode == 1) {
            if (tpCooldownTicks > 0 || mc.getConnection() == null) {
                return;
            }
            double px = mc.player.getX();
            double py = mc.player.getY();
            double pz = mc.player.getZ();
            mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(
                    nearest.getX(), nearest.getY(), nearest.getZ(), nearest.onGround(), false));
            mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(
                    px, py, pz, mc.player.onGround(), mc.player.horizontalCollision));
            tpCooldownTicks = 4;
            return;
        }

        double dx = nearest.getX() - mc.player.getX();
        double dz = nearest.getZ() - mc.player.getZ();
        double horiz = Math.hypot(dx, dz);
        if (horiz > 0.05) {
            double speed = config.itemSuckerSpeed;
            Vec3 vel = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement((dx / horiz) * speed, vel.y, (dz / horiz) * speed);
        }
    }
}
