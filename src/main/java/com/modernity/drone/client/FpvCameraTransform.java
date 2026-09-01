package com.modernity.drone.client;

import com.modernity.drone.client.config.FpvClientConfig;
import com.modernity.drone.entity.DroneEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Exact camera mount transform used by FPVtoMinecraft 1.1.4. */
public final class FpvCameraTransform {
    private static final float CAMERA_PIVOT_Y = 0.22122687F;
    private static final float CAMERA_PIVOT_Z = 0.24639937F;
    private static final float CAMERA_LENS_OFFSET_Z = 0.0625F;

    private FpvCameraTransform() {
    }

    public static Angles angles(DroneEntity drone, float partialTick) {
        Basis basis = basis(drone, partialTick);
        float cameraAngle = (float) Math.toRadians(-FpvClientConfig.cameraAngle());
        Quaternionf tilt = new Quaternionf().rotationAxis(cameraAngle, basis.left());
        Vector3f look = tilt.transform(new Vector3f(basis.look()));
        Vector3f up = tilt.transform(new Vector3f(basis.up()));
        float[] angles = worldEulerAngles(look, up);
        return new Angles(angles[0], angles[1], angles[2]);
    }

    public static Vec3 position(DroneEntity drone, float partialTick) {
        Basis basis = basis(drone, partialTick);
        float cameraAngle = (float) Math.toRadians(-FpvClientConfig.cameraAngle());
        float localY = CAMERA_PIVOT_Y + CAMERA_LENS_OFFSET_Z * (float) Math.sin(cameraAngle);
        float localZ = CAMERA_PIVOT_Z + CAMERA_LENS_OFFSET_Z * (float) Math.cos(cameraAngle);
        Vector3f offset = new Vector3f(basis.up()).mul(localY)
                .add(new Vector3f(basis.look()).mul(localZ));
        Vec3 base = drone.getPosition(partialTick);
        return base.add(offset.x, offset.y, offset.z);
    }

    private static Basis basis(DroneEntity drone, float partialTick) {
        float yaw = drone.getYRot(partialTick);
        float pitch = drone.getXRot(partialTick);
        float roll = drone.rollDegrees(partialTick);
        Quaternionf rotation = new Quaternionf().rotationYXZ(
                (float) Math.toRadians(-yaw),
                (float) Math.toRadians(pitch),
                (float) Math.toRadians(roll)
        );
        Vector3f look = rotation.transform(new Vector3f(0.0F, 0.0F, 1.0F));
        Vector3f up = rotation.transform(new Vector3f(0.0F, 1.0F, 0.0F));
        Vector3f left = new Vector3f(up).cross(look).normalize();
        return new Basis(look, up, left);
    }

    // This is the reference mod's world-vector to Minecraft Euler conversion.
    private static float[] worldEulerAngles(Vector3f forward, Vector3f up) {
        float worldYaw = 0.0F;
        float worldPitch = 0.0F;
        float worldRoll = 0.0F;
        if (Math.abs(forward.x) > 1.0E-4F || Math.abs(forward.z) > 1.0E-4F) {
            Vector3f projected = new Vector3f(forward.x, 0.0F, forward.z);
            if (projected.lengthSquared() > 1.0E-4F) projected.normalize();
            float[] yawAxisAngle = lookAtToAxisAngle(projected, new Vector3f(0.0F, 1.0F, 0.0F));
            worldYaw = yawAxisAngle[0] * yawAxisAngle[2] * (float) (180.0 / Math.PI);
            Quaternionf antiYaw = new Quaternionf().rotationAxis(
                    (float) Math.toRadians(-worldYaw), 0.0F, 1.0F, 0.0F);
            Vector3f rotatedForward = antiYaw.transform(new Vector3f(forward));
            float[] pitchAxisAngle = lookAtToAxisAngle(rotatedForward, new Vector3f(0.0F, 1.0F, 0.0F));
            worldPitch = pitchAxisAngle[0] * pitchAxisAngle[1] * (float) (180.0 / Math.PI);
            Quaternionf rolless = fromAngles(
                    (float) Math.toRadians(worldPitch),
                    (float) Math.toRadians(worldYaw),
                    0.0F
            );
            Vector3f rollessUp = rolless.transform(new Vector3f(0.0F, 1.0F, 0.0F));
            Vector3f crossUps = new Vector3f(rollessUp).cross(up);
            if (crossUps.lengthSquared() > 1.0E-4F) crossUps.normalize();
            float flip = Math.abs(angleBetween(crossUps, forward) * (float) (180.0 / Math.PI)) > 90.0F
                    ? -1.0F : 1.0F;
            worldRoll = angleBetween(rollessUp, up) * (float) (180.0 / Math.PI) * flip;
        } else if (forward.y > 0.0F) {
            worldPitch = -90.0F;
            worldRoll = verticalRoll(up);
        } else if (forward.y < 0.0F) {
            worldPitch = 90.0F;
            worldRoll = verticalRoll(up);
        }
        return new float[]{-worldYaw, worldPitch, worldRoll};
    }

    private static float verticalRoll(Vector3f up) {
        Vector3f projected = new Vector3f(up.x, 0.0F, up.z);
        if (projected.lengthSquared() <= 0.001F) return 0.0F;
        projected.normalize();
        float[] axisAngle = lookAtToAxisAngle(projected, new Vector3f(0.0F, 1.0F, 0.0F));
        return axisAngle[0] * axisAngle[2] * (float) (180.0 / Math.PI);
    }

    private static float[] lookAtToAxisAngle(Vector3f direction, Vector3f up) {
        Vector3f zAxis = new Vector3f(direction).normalize();
        Vector3f xAxis = new Vector3f(up).cross(zAxis);
        if (xAxis.lengthSquared() < 1.0E-4F) xAxis.set(1.0F, 0.0F, 0.0F);
        else xAxis.normalize();
        Vector3f yAxis = new Vector3f(zAxis).cross(xAxis);
        float trace = xAxis.x + yAxis.y + zAxis.z;
        float angle;
        float x;
        float y;
        float z;
        if (trace > 0.0F) {
            float scale = (float) Math.sqrt(trace + 1.0F) * 2.0F;
            float w = 0.25F * scale;
            x = (yAxis.z - zAxis.y) / scale;
            y = (zAxis.x - xAxis.z) / scale;
            z = (xAxis.y - yAxis.x) / scale;
            angle = 2.0F * (float) Math.acos(clampUnit(w));
        } else if (xAxis.x > yAxis.y && xAxis.x > zAxis.z) {
            float scale = (float) Math.sqrt(1.0F + xAxis.x - yAxis.y - zAxis.z) * 2.0F;
            x = 0.25F * scale;
            y = (yAxis.x + xAxis.y) / scale;
            z = (zAxis.x + xAxis.z) / scale;
            float w = (yAxis.z - zAxis.y) / scale;
            angle = 2.0F * (float) Math.acos(clampUnit(w));
        } else if (yAxis.y > zAxis.z) {
            float scale = (float) Math.sqrt(1.0F + yAxis.y - xAxis.x - zAxis.z) * 2.0F;
            x = (yAxis.x + xAxis.y) / scale;
            y = 0.25F * scale;
            z = (zAxis.y + yAxis.z) / scale;
            float w = (zAxis.x - xAxis.z) / scale;
            angle = 2.0F * (float) Math.acos(clampUnit(w));
        } else {
            float scale = (float) Math.sqrt(1.0F + zAxis.z - xAxis.x - yAxis.y) * 2.0F;
            x = (zAxis.x + xAxis.z) / scale;
            y = (zAxis.y + yAxis.z) / scale;
            z = 0.25F * scale;
            float w = (xAxis.y - yAxis.x) / scale;
            angle = 2.0F * (float) Math.acos(clampUnit(w));
        }
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        if (length > 1.0E-4F) {
            x /= length;
            y /= length;
            z /= length;
        }
        return new float[]{angle, x, y, z};
    }

    private static Quaternionf fromAngles(float pitch, float yaw, float roll) {
        float halfPitch = pitch * 0.5F;
        float halfYaw = yaw * 0.5F;
        float halfRoll = roll * 0.5F;
        float sinP = (float) Math.sin(halfPitch);
        float cosP = (float) Math.cos(halfPitch);
        float sinY = (float) Math.sin(halfYaw);
        float cosY = (float) Math.cos(halfYaw);
        float sinR = (float) Math.sin(halfRoll);
        float cosR = (float) Math.cos(halfRoll);
        return new Quaternionf(
                cosR * sinP * cosY + sinR * cosP * sinY,
                cosR * cosP * sinY - sinR * sinP * cosY,
                sinR * cosP * cosY - cosR * sinP * sinY,
                cosR * cosP * cosY + sinR * sinP * sinY
        );
    }

    private static float angleBetween(Vector3f a, Vector3f b) {
        float denominator = a.length() * b.length();
        if (denominator <= 1.0E-6F) return 0.0F;
        float dot = Math.max(-1.0F, Math.min(1.0F, a.dot(b) / denominator));
        return (float) Math.acos(dot);
    }

    private static float clampUnit(float value) {
        return Math.max(-1.0F, Math.min(1.0F, value));
    }

    private record Basis(Vector3f look, Vector3f up, Vector3f left) {
    }

    public record Angles(float yaw, float pitch, float roll) {
    }
}
