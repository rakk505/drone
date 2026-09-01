package com.modernity.drone.flight;

/**
 * Immutable, normalized body-to-world quaternion.
 *
 * <p>The convenience Euler angles follow Minecraft controls: yaw zero faces
 * +Z and positive yaw turns toward -X; positive pitch raises the nose; positive
 * roll lowers the right side. The quaternion representation avoids gimbal lock
 * during acrobatic flight.</p>
 */
public final class FlightAttitude {
    public static final FlightAttitude IDENTITY = new FlightAttitude(0.0, 0.0, 0.0, 1.0);

    private final double x;
    private final double y;
    private final double z;
    private final double w;

    public FlightAttitude(double x, double y, double z, double w) {
        double magnitude = Math.hypot(Math.hypot(x, y), Math.hypot(z, w));
        if (!Double.isFinite(magnitude) || magnitude < 1.0e-12) {
            this.x = 0.0;
            this.y = 0.0;
            this.z = 0.0;
            this.w = 1.0;
            return;
        }

        double sign = w < 0.0 ? -1.0 : 1.0;
        double scale = sign / magnitude;
        this.x = x * scale;
        this.y = y * scale;
        this.z = z * scale;
        this.w = w * scale;
    }

    public static FlightAttitude fromEulerRadians(double yaw, double pitch, double roll) {
        FlightAttitude yawRotation = axisAngle(0.0, 1.0, 0.0, -FlightMath.wrapRadians(yaw));
        FlightAttitude pitchRotation = axisAngle(1.0, 0.0, 0.0, -FlightMath.wrapRadians(pitch));
        // V1.1.4 applies positive roll around the craft's forward axis.  Keep
        // this sign in lockstep with the renderer's rotationYXZ(..., roll).
        FlightAttitude rollRotation = axisAngle(0.0, 0.0, 1.0, FlightMath.wrapRadians(roll));
        return yawRotation.multiply(pitchRotation).multiply(rollRotation);
    }

    private static FlightAttitude axisAngle(double axisX, double axisY, double axisZ, double angle) {
        double halfAngle = angle * 0.5;
        double sine = Math.sin(halfAngle);
        return new FlightAttitude(axisX * sine, axisY * sine, axisZ * sine, Math.cos(halfAngle));
    }

    /** Integrates pilot-sign body rates over the supplied number of seconds. */
    public FlightAttitude integrate(FlightRates rates, double seconds) {
        if (rates == null || !rates.isFinite() || !Double.isFinite(seconds) || seconds <= 0.0) {
            return this;
        }

        // Convert pilot signs to conventional local-axis angular velocity.
        double omegaX = -rates.pitchRadiansPerSecond();
        double omegaY = -rates.yawRadiansPerSecond();
        double omegaZ = rates.rollRadiansPerSecond();
        double angularSpeed = Math.hypot(omegaX, Math.hypot(omegaY, omegaZ));
        if (angularSpeed < 1.0e-12) {
            return this;
        }

        double angle = angularSpeed * seconds;
        double sineScale = Math.sin(angle * 0.5) / angularSpeed;
        FlightAttitude localDelta = new FlightAttitude(
                omegaX * sineScale,
                omegaY * sineScale,
                omegaZ * sineScale,
                Math.cos(angle * 0.5)
        );
        return multiply(localDelta);
    }

    /** Applies the yaw, pitch, then roll body-axis order used by V1.1.4. */
    public FlightAttitude integrateReferenceRates(FlightRates rates, double seconds) {
        if (rates == null || !rates.isFinite() || !Double.isFinite(seconds) || seconds <= 0.0) {
            return this;
        }
        FlightAttitude yawDelta = axisAngle(
                0.0, 1.0, 0.0,
                -rates.yawRadiansPerSecond() * seconds
        );
        FlightAttitude pitchDelta = axisAngle(
                1.0, 0.0, 0.0,
                -rates.pitchRadiansPerSecond() * seconds
        );
        FlightAttitude rollDelta = axisAngle(
                0.0, 0.0, 1.0,
                rates.rollRadiansPerSecond() * seconds
        );
        return multiply(yawDelta).multiply(pitchDelta).multiply(rollDelta);
    }

    public FlightVector rotate(FlightVector vector) {
        // q * v * conjugate(q), expanded to avoid temporary quaternions.
        double tx = 2.0 * (y * vector.z() - z * vector.y());
        double ty = 2.0 * (z * vector.x() - x * vector.z());
        double tz = 2.0 * (x * vector.y() - y * vector.x());
        return new FlightVector(
                vector.x() + w * tx + (y * tz - z * ty),
                vector.y() + w * ty + (z * tx - x * tz),
                vector.z() + w * tz + (x * ty - y * tx)
        );
    }

    public FlightVector bodyRight() {
        return rotate(new FlightVector(1.0, 0.0, 0.0));
    }

    public FlightVector bodyUp() {
        return rotate(FlightVector.UP);
    }

    public FlightVector bodyForward() {
        return rotate(new FlightVector(0.0, 0.0, 1.0));
    }

    public double yawRadians() {
        FlightVector forward = bodyForward();
        if (Math.hypot(forward.x(), forward.z()) > 1.0e-9) {
            return FlightMath.wrapRadians(Math.atan2(-forward.x(), forward.z()));
        }
        FlightVector right = bodyRight();
        return FlightMath.wrapRadians(Math.atan2(right.z(), right.x()));
    }

    public double pitchRadians() {
        return Math.asin(FlightMath.clamp(bodyForward().y(), -1.0, 1.0));
    }

    public double rollRadians() {
        double yaw = yawRadians();
        FlightVector headingRight = new FlightVector(Math.cos(yaw), 0.0, Math.sin(yaw));
        FlightVector levelUp = bodyForward().cross(headingRight).normalizedOrZero();
        if (levelUp == FlightVector.ZERO) {
            return 0.0;
        }
        FlightVector actualUp = bodyUp();
        return FlightMath.wrapRadians(-Math.atan2(actualUp.dot(headingRight), actualUp.dot(levelUp)));
    }

    /**
     * Reproduces V1.1.4's ground-contact levelling: blend the body-up vector
     * toward world-up, then rebuild an orthonormal basis without changing the
     * craft's heading any more than necessary.
     */
    public FlightAttitude levelTowardWorldUp(double amount) {
        double alpha = FlightMath.clamp(FlightMath.finiteOr(amount, 0.0), 0.0, 1.0);
        if (alpha <= 0.0) return this;

        FlightVector forward = bodyForward().normalizedOrZero();
        FlightVector up = bodyUp().multiply(1.0 - alpha).add(FlightVector.UP.multiply(alpha)).normalizedOrZero();
        FlightVector right = up.cross(forward).normalizedOrZero();
        if (right.dot(right) < 1.0e-12) {
            right = up.cross(new FlightVector(0.0, 0.0, 1.0)).normalizedOrZero();
        }
        if (right.dot(right) < 1.0e-12) {
            right = new FlightVector(1.0, 0.0, 0.0);
        }
        forward = right.cross(up).normalizedOrZero();
        return fromBasis(right, up, forward);
    }

    private static FlightAttitude fromBasis(FlightVector right, FlightVector up, FlightVector forward) {
        // Rotation matrix columns are the transformed local X/Y/Z axes.
        double m00 = right.x();
        double m01 = up.x();
        double m02 = forward.x();
        double m10 = right.y();
        double m11 = up.y();
        double m12 = forward.y();
        double m20 = right.z();
        double m21 = up.z();
        double m22 = forward.z();
        double trace = m00 + m11 + m22;
        double qx;
        double qy;
        double qz;
        double qw;
        if (trace > 0.0) {
            double scale = Math.sqrt(trace + 1.0) * 2.0;
            qw = 0.25 * scale;
            qx = (m21 - m12) / scale;
            qy = (m02 - m20) / scale;
            qz = (m10 - m01) / scale;
        } else if (m00 > m11 && m00 > m22) {
            double scale = Math.sqrt(1.0 + m00 - m11 - m22) * 2.0;
            qw = (m21 - m12) / scale;
            qx = 0.25 * scale;
            qy = (m01 + m10) / scale;
            qz = (m02 + m20) / scale;
        } else if (m11 > m22) {
            double scale = Math.sqrt(1.0 + m11 - m00 - m22) * 2.0;
            qw = (m02 - m20) / scale;
            qx = (m01 + m10) / scale;
            qy = 0.25 * scale;
            qz = (m12 + m21) / scale;
        } else {
            double scale = Math.sqrt(1.0 + m22 - m00 - m11) * 2.0;
            qw = (m10 - m01) / scale;
            qx = (m02 + m20) / scale;
            qy = (m12 + m21) / scale;
            qz = 0.25 * scale;
        }
        return new FlightAttitude(qx, qy, qz, qw);
    }

    private FlightAttitude multiply(FlightAttitude other) {
        return new FlightAttitude(
                w * other.x + x * other.w + y * other.z - z * other.y,
                w * other.y - x * other.z + y * other.w + z * other.x,
                w * other.z + x * other.y - y * other.x + z * other.w,
                w * other.w - x * other.x - y * other.y - z * other.z
        );
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public double w() {
        return w;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof FlightAttitude other)) {
            return false;
        }
        return Double.compare(x, other.x) == 0
                && Double.compare(y, other.y) == 0
                && Double.compare(z, other.z) == 0
                && Double.compare(w, other.w) == 0;
    }

    @Override
    public int hashCode() {
        int result = Double.hashCode(x);
        result = 31 * result + Double.hashCode(y);
        result = 31 * result + Double.hashCode(z);
        result = 31 * result + Double.hashCode(w);
        return result;
    }

    @Override
    public String toString() {
        return "FlightAttitude[x=" + x + ", y=" + y + ", z=" + z + ", w=" + w + ']';
    }
}
