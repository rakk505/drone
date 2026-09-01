package com.modernity.drone.client.render;

import com.geckolib.animatable.GeoReplacedEntity;
import com.geckolib.animatable.SingletonGeoAnimatable;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;

/**
 * Stateless GeckoLib animatable used to render the mod's normal {@code DroneEntity}.
 *
 * <p>The original model's two animation clips intentionally contain no keyframes;
 * the propellers are articulated from the entity's synchronized spin angle instead.</p>
 */
public final class FpvDroneVisual implements GeoReplacedEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.drone.idle");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public FpvDroneVisual() {
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>("airframe", state -> state.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
