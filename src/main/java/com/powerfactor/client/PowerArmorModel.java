package com.powerfactor.client;

import com.powerfactor.PowerFactor;
import com.powerfactor.item.PowerArmorItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/** Complete articulated foundry suit, using the normal humanoid armor animation path. */
public final class PowerArmorModel extends HumanoidModel<HumanoidRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(PowerFactor.id("foundry_suit"), "main");
    public static final ModelLayerLocation BABY_LAYER = new ModelLayerLocation(PowerFactor.id("foundry_suit_baby"), "main");
    public PowerArmorModel(ModelPart root, PowerArmorItem.Part slot) {
        super(root);
        head.visible = slot == PowerArmorItem.Part.HELMET;
        hat.visible = false;
        body.visible = slot == PowerArmorItem.Part.CHEST || slot == PowerArmorItem.Part.LEGS;
        body.getChild("chest_shell").visible = slot == PowerArmorItem.Part.CHEST;
        body.getChild("hip_belt").visible = slot == PowerArmorItem.Part.LEGS;
        leftArm.visible = rightArm.visible = slot == PowerArmorItem.Part.CHEST;
        leftLeg.visible = rightLeg.visible = slot == PowerArmorItem.Part.LEGS || slot == PowerArmorItem.Part.BOOTS;
        for (var leg : new ModelPart[] {leftLeg, rightLeg}) {
            leg.getChild("frame").visible = slot == PowerArmorItem.Part.LEGS;
            leg.getChild("boot").visible = slot == PowerArmorItem.Part.BOOTS;
        }
    }
    private static CubeListBuilder material(int u, int v) { return CubeListBuilder.create().texOffs(u,v); }
    public static MeshDefinition createMesh() {
        var mesh = new MeshDefinition(); var root = mesh.getRoot();
        var head = root.addOrReplaceChild("head", material(0,0).addBox(-4.7F,-8.6F,-4.6F,9.4F,8.8F,9.2F),PartPose.ZERO);
        head.addOrReplaceChild("plates",material(0,64)
                .addBox(-4.6F,-9,-4.6F,9.2F,1.3F,9.2F).addBox(-4.6F,-7.8F,-5,9.2F,1.3F,.8F)
                .addBox(-4.9F,-5.6F,-4.9F,1.8F,4.7F,1.2F).addBox(3.1F,-5.6F,-4.9F,1.8F,4.7F,1.2F),PartPose.ZERO);
        head.addOrReplaceChild("glass",material(128,128).addBox(-4.0F,-6.5F,-4.95F,8,2.6F,.12F),PartPose.ZERO);
        head.addOrReplaceChild("breather",material(0,0).addBox(-2.9F,-3.5F,-5.5F,5.8F,2.8F,1.6F)
                .texOffs(128,64).addBox(-5.8F,-6.8F,-1.7F,1.1F,3.8F,3.4F).addBox(4.7F,-6.8F,-1.7F,1.1F,3.8F,3.4F),PartPose.ZERO);
        var vents=material(0,128);
        for(int i=0;i<5;i++) vents.addBox(-2.2F+i,-2.9F,-5.65F,.35F,1.4F,.15F);
        head.addOrReplaceChild("vents",vents,PartPose.ZERO);
        head.addOrReplaceChild("rear_pack",material(0,0).addBox(-3,-6.5F,4.5F,6,4.5F,.8F)
                .texOffs(128,64).addBox(-2.3F,-5.5F,5.3F,4.6F,.5F,.2F),PartPose.ZERO);
        head.addOrReplaceChild("hat",CubeListBuilder.create(),PartPose.ZERO);
        var body=root.addOrReplaceChild("body",CubeListBuilder.create(),PartPose.ZERO);
        var chest=body.addOrReplaceChild("chest_shell",material(128,0).addBox(-4.5F,.1F,-2.7F,9,11.8F,5.4F),PartPose.ZERO);
        chest.addOrReplaceChild("breastplate",material(0,64).addBox(-4.6F,.5F,-3.3F,9.2F,5.9F,.8F)
                .addBox(-3.9F,8,-3.25F,7.8F,1.2F,.7F).addBox(-3.8F,10,-3.15F,7.6F,1.2F,.7F),PartPose.ZERO);
        chest.addOrReplaceChild("core",material(0,0).addBox(-2.6F,2.7F,-3.9F,5.2F,3,1)
                .texOffs(128,64).addBox(-2.2F,3.1F,-4.02F,4.4F,.6F,.2F),PartPose.ZERO);
        chest.addOrReplaceChild("power_pack",material(0,0).addBox(-3.5F,1,2.7F,7,8.5F,2.2F)
                .texOffs(0,128).addBox(-3.1F,2,4.9F,1.2F,5.5F,.6F).addBox(1.9F,2,4.9F,1.2F,5.5F,.6F)
                .texOffs(128,64).addBox(-1.5F,7.8F,4.9F,3,.5F,.5F),PartPose.ZERO);
        body.addOrReplaceChild("hip_belt",material(0,0).addBox(-4.6F,10.3F,-2.8F,9.2F,2.2F,5.6F)
                .texOffs(128,64).addBox(-2,10.7F,-3.25F,4,1.3F,.6F),PartPose.ZERO);
        for(boolean left:new boolean[]{false,true}) {
            float center=left?1:-1;
            var arm=root.addOrReplaceChild(left?"left_arm":"right_arm",material(128,0)
                    .addBox(center-2.2F,-2,-2.5F,4.4F,11.8F,5),PartPose.offset(left?5:-5,2,0));
            arm.addOrReplaceChild("pauldron",material(0,64).addBox(center-3.3F,-3.8F,-3.6F,6.6F,4.7F,7.2F)
                    .texOffs(0,0).addBox(center-3.5F,.6F,-3.4F,7,1.1F,6.8F),PartPose.ZERO);
            arm.addOrReplaceChild("forearm",material(0,64).addBox(center-2.6F,5.6F,-3.1F,5.2F,4.5F,6.2F)
                    .texOffs(128,64).addBox(center-2.65F,9,-3.15F,5.3F,.6F,6.3F)
                    .texOffs(0,0).addBox(center-1.5F,6.3F,-3.4F,3,2,.4F),PartPose.ZERO);
            var leg=root.addOrReplaceChild(left?"left_leg":"right_leg",CubeListBuilder.create(),PartPose.offset(left?1.9F:-1.9F,12,0));
            // Bias reinforcement outward so the two legs and boots retain a clear center seam.
            float legCenter=left?.35F:-.35F,bootCenter=left?.5F:-.5F;
            var frame=leg.addOrReplaceChild("frame",material(128,0).addBox(legCenter-2.05F,0,-2.4F,4.1F,9,4.8F),PartPose.ZERO);
            frame.addOrReplaceChild("thigh",material(0,64).addBox(legCenter-2.1F,.7F,-2.9F,4.2F,4.7F,.8F)
                    .addBox(legCenter-2.2F,5.4F,-3.2F,4.4F,2.8F,1.1F)
                    .texOffs(128,64).addBox(legCenter-2.1F,6.5F,-3.4F,4.2F,.55F,.2F),PartPose.ZERO);
            frame.addOrReplaceChild("actuator_rails",material(0,128).addBox(legCenter+(left?2.1F:-2.7F),1.3F,1,.6F,7,.7F)
                    .texOffs(0,0).addBox(legCenter-1.8F,1,2.4F,3.6F,2.2F,.6F),PartPose.ZERO);
            var boot=leg.addOrReplaceChild("boot",material(0,0).addBox(bootCenter-2.1F,7.5F,-2.8F,4.2F,4.2F,5.6F),PartPose.ZERO);
            boot.addOrReplaceChild("toe",material(0,64).addBox(bootCenter-2.2F,9.1F,-4.5F,4.4F,2.5F,7.2F)
                    .texOffs(128,64).addBox(bootCenter-2.25F,8.7F,-3.2F,4.5F,.7F,6.1F),PartPose.ZERO);
            boot.addOrReplaceChild("sole",material(128,0).addBox(bootCenter-2.35F,11.35F,-4.7F,4.7F,.9F,7.7F),PartPose.ZERO);
        }
        return mesh;
    }
    public static LayerDefinition createLayer() { return LayerDefinition.create(createMesh(),256,256); }
    public static LayerDefinition createBabyLayer() { return LayerDefinition.create(HumanoidModel.BABY_TRANSFORMER.apply(createMesh()),256,256); }
}
