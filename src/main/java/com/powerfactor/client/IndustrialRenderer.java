package com.powerfactor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerfactor.PowerFactor;
import com.powerfactor.machine.MachineBlock;
import com.powerfactor.machine.MachineBlockEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Job-specific moving components in the detailed static frames, with tick-driven coast-down. */
public final class IndustrialRenderer<T extends MachineBlockEntity> implements BlockEntityRenderer<T, IndustrialRenderer.State> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(PowerFactor.id("machine_motion"), "main");
    private final ModelPart fan, wheel, roll, drum, electrode, piston, bubble, fluid;
    public static class State extends BlockEntityRenderState { String kind = ""; Direction facing = Direction.NORTH; float phase, fluid; boolean running; }
    public IndustrialRenderer(BlockEntityRendererProvider.Context context) {
        var root = context.bakeLayer(LAYER); fan=root.getChild("fan");wheel=root.getChild("wheel");roll=root.getChild("roll");
        drum=root.getChild("drum");electrode=root.getChild("electrode");piston=root.getChild("piston");bubble=root.getChild("bubble");fluid=root.getChild("fluid");
    }
    public static LayerDefinition createLayer() {
        var mesh = new MeshDefinition(); var root=mesh.getRoot();
        var fan=root.addOrReplaceChild("fan",CubeListBuilder.create().texOffs(0,0).addBox(-1,-1,-0.5F,2,2,1),PartPose.ZERO);
        for(int i=0;i<6;i++) fan.addOrReplaceChild("vane"+i,CubeListBuilder.create().texOffs(8,0).addBox(-0.7F,0.8F,-0.3F,1.5F,3.4F,0.6F),PartPose.rotation(0,0.25F,i*(float)Math.PI/3));
        var wheel=root.addOrReplaceChild("wheel",CubeListBuilder.create().texOffs(32,0).addBox(-1,-1,-1,2,2,2),PartPose.ZERO);
        for(int i=0;i<8;i++) wheel.addOrReplaceChild("rim"+i,CubeListBuilder.create().texOffs(36,4).addBox(-1.8F,3.9F,-0.7F,3.6F,1.2F,1.4F)
                .texOffs(36,12).addBox(-0.3F,1,-0.5F,0.6F,3.4F,1),PartPose.rotation(0,0,i*(float)Math.PI/4));
        root.addOrReplaceChild("roll",CubeListBuilder.create().texOffs(0,64).addBox(-4,-2.5F,-1.8F,8,5,3.6F)
                .texOffs(0,76).addBox(-4,-1.8F,-2.5F,8,3.6F,5).texOffs(24,64).addBox(-5,-0.6F,-0.6F,10,1.2F,1.2F),PartPose.ZERO);
        var drum=root.addOrReplaceChild("drum",CubeListBuilder.create().texOffs(0,0).addBox(-1,-1,0,2,2,6),PartPose.ZERO);
        for(int i=0;i<8;i++)drum.addOrReplaceChild("rib"+i,CubeListBuilder.create().texOffs(16,16).addBox(-0.4F,3.8F,0,0.8F,0.9F,8)
                .texOffs(16,32).addBox(-1.7F,3.8F,-0.5F,3.4F,1,1),PartPose.rotation(0,0,i*(float)Math.PI/4));
        root.addOrReplaceChild("electrode",CubeListBuilder.create().texOffs(64,64).addBox(-0.65F,-3,-0.65F,1.3F,6,1.3F),PartPose.ZERO);
        root.addOrReplaceChild("piston",CubeListBuilder.create().texOffs(0,32).addBox(-2,-1,-2,4,2,4)
                .texOffs(16,32).addBox(-0.5F,0,-0.5F,1,4,1),PartPose.ZERO);
        root.addOrReplaceChild("bubble",CubeListBuilder.create().texOffs(96,0).addBox(-0.5F,-0.5F,-0.5F,1,1,1),PartPose.ZERO);
        root.addOrReplaceChild("fluid",CubeListBuilder.create().texOffs(96,0).addBox(-3.8F,-1,-3.3F,7.6F,1,6.6F),PartPose.ZERO);
        return LayerDefinition.create(mesh,128,128);
    }
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(T be,State s,float partial,Vec3 camera,ModelFeatureRenderer.@Nullable CrumblingOverlay breaking){
        BlockEntityRenderer.super.extractRenderState(be,s,partial,camera,breaking);
        s.kind=BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).getPath();s.phase=be.industrialPhase(partial);
        s.facing=be.getBlockState().getValueOrElse(MachineBlock.FACING,Direction.NORTH);s.running=be.getBlockState().getValueOrElse(MachineBlock.RUNNING,false);
        s.fluid = be instanceof com.powerfactor.machine.RecipeMachineBlockEntity recipe && recipe.getTank() != null
                ? (float) recipe.getTank().getAmountAsInt(0) / com.powerfactor.machine.RecipeMachineBlockEntity.TANK_CAPACITY : 0;
    }
    private void part(ModelPart model,State s,PoseStack pose,SubmitNodeCollector collector,double x,double y,double z,float rx,float rz){
        pose.pushPose();pose.translate(x,y,z);pose.scale(1,-1,-1);pose.mulPose(Axis.XP.rotation(rx));pose.mulPose(Axis.ZP.rotation(rz));
        collector.submitModelPart(model,pose,RenderTypes.entityCutout(PowerFactor.id("textures/entity/machine_motion.png")),s.lightCoords,OverlayTexture.NO_OVERLAY,null,-1,s.breakProgress);
        pose.popPose();
    }
    @Override public void submit(State s,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
        pose.pushPose();pose.translate(0.5,0,0.5);pose.mulPose(Axis.YP.rotationDegrees(-s.facing.toYRot()));
        switch(s.kind){
            case "wire_drawer" -> part(wheel,s,pose,collector,0,0.61,0.34,0,s.phase);
            case "rolling_mill" -> {part(roll,s,pose,collector,0,7/16.0,0.2,s.phase,0);part(roll,s,pose,collector,0,12/16.0,0.2,-s.phase,0);}
            case "ore_washer" -> {
                part(drum,s,pose,collector,0,9/16.0,0.35,0,s.phase);
                if (s.fluid > 0) {pose.pushPose();pose.translate(0,13.1/16,-0.03125);pose.scale(1,s.fluid*2.7F,1);part(fluid,s,pose,collector,0,0,0,0,0);pose.popPose();}
            }
            case "arc_furnace" -> {for(int i=-1;i<=1;i++)part(electrode,s,pose,collector,i*0.25,0.7+(s.running?Math.sin(s.phase+i)*0.014:0),0,0,0);}
            case "alloy_furnace" -> part(fan,s,pose,collector,0,0.7,-0.46,0,-s.phase);
            case "electric_furnace","charger","fuel_cell" -> part(fan,s,pose,collector,0,0.63,-0.46,0,s.phase);
            case "diesel_generator" -> {part(wheel,s,pose,collector,0,0.54,0.34,0,s.phase);for(int i=-1;i<=1;i++)part(piston,s,pose,collector,i*0.22,0.68+Math.sin(s.phase*2+i*2)*0.035,-0.06,0,0);}
            case "electric_pump" -> {pose.pushPose();pose.translate(0,0.95,0);pose.mulPose(Axis.XP.rotationDegrees(90));part(fan,s,pose,collector,0,0,0,0,s.phase);pose.popPose();}
            case "electrolyzer" -> {if(s.running)for(int i=0;i<6;i++){double t=((s.phase+i*0.9)/(Math.PI*2))%1;part(bubble,s,pose,collector,(i%2==0?-1:1)*0.22,0.32+t*0.4,Math.sin(i*2.1)*0.09,0,0);}}
            default -> {}
        }
        pose.popPose();
    }
}
