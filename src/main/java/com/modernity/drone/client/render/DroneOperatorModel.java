package com.modernity.drone.client.render;

import com.modernity.drone.DroneMod;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * A villager-proportioned field operator in fatigues with an over-ear radio
 * headset, boom microphone, and compact drone controller.
 */
public final class DroneOperatorModel extends EntityModel<DroneOperatorRenderState> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(DroneMod.MOD_ID, "drone_operator"),
            "main"
    );

    private final ModelPart head;
    private final ModelPart leftArm;
    private final ModelPart rightArm;
    private final ModelPart leftLeg;
    private final ModelPart rightLeg;
    private final ModelPart controller;

    public DroneOperatorModel(ModelPart root) {
        super(root);
        this.head = root.getChild("head");
        this.leftArm = root.getChild("left_arm");
        this.rightArm = root.getChild("right_arm");
        this.leftLeg = root.getChild("left_leg");
        this.rightLeg = root.getChild("right_leg");
        this.controller = root.getChild("controller");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition head = root.addOrReplaceChild(
                "head",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-4.0F, -10.0F, -4.0F, 8.0F, 10.0F, 8.0F),
                PartPose.ZERO
        );
        head.addOrReplaceChild(
                "nose",
                CubeListBuilder.create()
                        .texOffs(24, 0)
                        .addBox(-1.0F, -1.0F, -6.0F, 2.0F, 4.0F, 2.0F),
                PartPose.offset(0.0F, -2.0F, 0.0F)
        );

        // The following parts all sample the dedicated charcoal patch in the
        // upper-right quadrant of the operator texture.
        head.addOrReplaceChild(
                "headset_band",
                CubeListBuilder.create()
                        .texOffs(32, 0)
                        .addBox(-5.0F, -11.0F, -1.0F, 10.0F, 1.0F, 2.0F),
                PartPose.ZERO
        );
        head.addOrReplaceChild(
                "left_earcup",
                CubeListBuilder.create()
                        .texOffs(32, 0)
                        .addBox(-5.0F, -7.5F, -2.0F, 2.0F, 4.0F, 4.0F),
                PartPose.ZERO
        );
        head.addOrReplaceChild(
                "right_earcup",
                CubeListBuilder.create()
                        .texOffs(32, 0)
                        .addBox(3.0F, -7.5F, -2.0F, 2.0F, 4.0F, 4.0F),
                PartPose.ZERO
        );
        head.addOrReplaceChild(
                "microphone_boom",
                CubeListBuilder.create()
                        .texOffs(32, 0)
                        .addBox(3.8F, -4.8F, -5.2F, 1.0F, 1.0F, 4.0F),
                PartPose.ZERO
        );
        head.addOrReplaceChild(
                "microphone",
                CubeListBuilder.create()
                        .texOffs(32, 0)
                        .addBox(2.5F, -5.3F, -6.3F, 2.0F, 2.0F, 2.0F),
                PartPose.ZERO
        );

        PartDefinition body = root.addOrReplaceChild(
                "body",
                CubeListBuilder.create()
                        .texOffs(16, 20)
                        .addBox(-4.0F, 0.0F, -3.0F, 8.0F, 12.0F, 6.0F),
                PartPose.ZERO
        );
        body.addOrReplaceChild(
                "fatigue_jacket",
                CubeListBuilder.create()
                        .texOffs(16, 20)
                        .addBox(-4.0F, 0.0F, -3.0F, 8.0F, 12.0F, 6.0F, new CubeDeformation(0.3F)),
                PartPose.ZERO
        );

        PartDefinition rightArm = root.addOrReplaceChild(
                "right_arm",
                CubeListBuilder.create()
                        .texOffs(16, 20)
                        .addBox(-3.0F, -1.5F, -2.0F, 4.0F, 8.0F, 4.0F),
                PartPose.offsetAndRotation(-5.0F, 2.0F, 0.0F, -0.82F, 0.0F, 0.1F)
        );
        rightArm.addOrReplaceChild(
                "right_hand",
                CubeListBuilder.create()
                        .texOffs(44, 22)
                        .addBox(-3.0F, 6.0F, -2.0F, 4.0F, 3.0F, 4.0F),
                PartPose.ZERO
        );

        PartDefinition leftArm = root.addOrReplaceChild(
                "left_arm",
                CubeListBuilder.create()
                        .texOffs(16, 20)
                        .mirror()
                        .addBox(-1.0F, -1.5F, -2.0F, 4.0F, 8.0F, 4.0F),
                PartPose.offsetAndRotation(5.0F, 2.0F, 0.0F, -0.82F, 0.0F, -0.1F)
        );
        leftArm.addOrReplaceChild(
                "left_hand",
                CubeListBuilder.create()
                        .texOffs(44, 22)
                        .mirror()
                        .addBox(-1.0F, 6.0F, -2.0F, 4.0F, 3.0F, 4.0F),
                PartPose.ZERO
        );

        PartDefinition controller = root.addOrReplaceChild(
                "controller",
                CubeListBuilder.create()
                        .texOffs(32, 0)
                        .addBox(-3.0F, -1.5F, -1.0F, 6.0F, 3.0F, 2.0F),
                PartPose.offsetAndRotation(0.0F, 8.0F, -5.0F, -0.18F, 0.0F, 0.0F)
        );
        controller.addOrReplaceChild(
                "left_antenna",
                CubeListBuilder.create()
                        .texOffs(32, 0)
                        .addBox(-2.4F, -4.5F, -0.5F, 1.0F, 4.0F, 1.0F),
                PartPose.rotation(0.0F, 0.0F, -0.22F)
        );
        controller.addOrReplaceChild(
                "right_antenna",
                CubeListBuilder.create()
                        .texOffs(32, 0)
                        .addBox(1.4F, -4.5F, -0.5F, 1.0F, 4.0F, 1.0F),
                PartPose.rotation(0.0F, 0.0F, 0.22F)
        );

        PartDefinition rightLeg = root.addOrReplaceChild(
                "right_leg",
                CubeListBuilder.create()
                        .texOffs(0, 22)
                        .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offset(-2.0F, 12.0F, 0.0F)
        );
        rightLeg.addOrReplaceChild(
                "right_boot",
                CubeListBuilder.create()
                        .texOffs(32, 0)
                        .addBox(-2.1F, 8.0F, -2.4F, 4.2F, 4.0F, 4.8F),
                PartPose.ZERO
        );

        PartDefinition leftLeg = root.addOrReplaceChild(
                "left_leg",
                CubeListBuilder.create()
                        .texOffs(0, 22)
                        .mirror()
                        .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offset(2.0F, 12.0F, 0.0F)
        );
        leftLeg.addOrReplaceChild(
                "left_boot",
                CubeListBuilder.create()
                        .texOffs(32, 0)
                        .addBox(-2.1F, 8.0F, -2.4F, 4.2F, 4.0F, 4.8F),
                PartPose.ZERO
        );

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(DroneOperatorRenderState state) {
        super.setupAnim(state);

        this.head.yRot = state.yRot * Mth.DEG_TO_RAD;
        this.head.xRot = state.xRot * Mth.DEG_TO_RAD;
        this.head.zRot = 0.0F;

        // The mob is deliberately planted: even if network interpolation reports
        // a tiny displacement, its feet never perform a walking cycle.
        this.leftLeg.xRot = 0.0F;
        this.leftLeg.yRot = 0.0F;
        this.rightLeg.xRot = 0.0F;
        this.rightLeg.yRot = 0.0F;

        float inputMotion = state.targetLocked ? Mth.sin(state.ageInTicks * 0.42F) * 0.025F : 0.0F;
        this.leftArm.xRot = -0.82F + inputMotion;
        this.leftArm.zRot = -0.1F;
        this.rightArm.xRot = -0.82F - inputMotion;
        this.rightArm.zRot = 0.1F;
        this.controller.xRot = -0.18F + inputMotion * 0.35F;
    }
}
