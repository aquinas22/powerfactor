package com.powerfactor.client;

import com.powerfactor.item.PowerArmorItem;
import java.util.EnumMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/** Separate models per slot avoid changing visibility on models already queued for rendering. */
public final class PowerArmorModels implements IClientItemExtensions {
    private static final EnumMap<PowerArmorItem.Part,PowerArmorModel> ADULT=new EnumMap<>(PowerArmorItem.Part.class),
            BABY=new EnumMap<>(PowerArmorItem.Part.class);
    public static void invalidate() { ADULT.clear(); BABY.clear(); }
    // The normal extension copies the stock slot model's visibility, which would unhide other suit pieces.
    // This model animates directly from HumanoidRenderState and retains its immutable slot mask.
    @Override public Model getGenericArmorModel(ItemStack stack, EquipmentClientInfo.LayerType type, Model original) {
        return getHumanoidArmorModel(stack,type,original);
    }
    @Override public Model getHumanoidArmorModel(ItemStack stack, EquipmentClientInfo.LayerType type, Model original) {
        if (!(original instanceof HumanoidModel) || !(stack.getItem() instanceof PowerArmorItem item)) return original;
        boolean baby=type==EquipmentClientInfo.LayerType.HUMANOID_BABY;
        return (baby?BABY:ADULT).computeIfAbsent(item.part(),part->new PowerArmorModel(
                Minecraft.getInstance().getEntityModels().bakeLayer(baby?PowerArmorModel.BABY_LAYER:PowerArmorModel.LAYER),part));
    }
}
