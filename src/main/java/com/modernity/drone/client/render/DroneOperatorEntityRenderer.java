package com.modernity.drone.client.render;

import com.modernity.drone.entity.DroneOperatorEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/** Renderer for the stationary hostile drone operator. */
public final class DroneOperatorEntityRenderer extends MobRenderer<
        DroneOperatorEntity,
        DroneOperatorRenderState,
        DroneOperatorModel> {
    // The operator predates the fpvdrone compatibility port. Keep its original
    // resource namespace so existing packs can continue replacing this skin.
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            "drone",
            "textures/entity/drone_operator.png"
    );

    public DroneOperatorEntityRenderer(EntityRendererProvider.Context context) {
        super(
                context,
                new DroneOperatorModel(context.bakeLayer(DroneOperatorModel.LAYER_LOCATION)),
                0.5F
        );
    }

    @Override
    public DroneOperatorRenderState createRenderState() {
        return new DroneOperatorRenderState();
    }

    @Override
    public void extractRenderState(
            DroneOperatorEntity operator,
            DroneOperatorRenderState state,
            float partialTicks
    ) {
        super.extractRenderState(operator, state, partialTicks);
        state.targetLocked = operator.isAggressive();
    }

    @Override
    public Identifier getTextureLocation(DroneOperatorRenderState state) {
        return TEXTURE;
    }
}
