package com.powerfactor.client;

import net.minecraft.client.model.HumanoidModel;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;

/** Isolated enum parameter supplier, loaded only when the client humanoid pose enum is extended. */
public final class WingsuitPose {
    public static final net.minecraft.util.context.ContextKey<Float> OPEN = new net.minecraft.util.context.ContextKey<>(net.minecraft.resources.Identifier.fromNamespaceAndPath("powerfactor", "wing_progress"));
    public static final EnumProxy<HumanoidModel.ArmPose> POSE = new EnumProxy<>(HumanoidModel.ArmPose.class, true, true,
            (IArmPoseTransformer) (model, state, arm) -> {
                float progress = state.getRenderDataOrDefault(OPEN, 1F);
                model.leftArm.xRot = model.rightArm.xRot = -0.1F * progress;
                model.leftArm.yRot = model.rightArm.yRot = 0;
                model.leftArm.zRot = -1.05F * progress; model.rightArm.zRot = 1.05F * progress;
                model.leftLeg.zRot = -0.07F * progress; model.rightLeg.zRot = 0.07F * progress;
            });
    private WingsuitPose() {}
}
