package com.powerfactor.exotic;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** An exotic tool: which material and kind, with abilities from {@link ExoticAbilities}. */
public interface ExoticTool {
    enum Kind {
        SWORD(3F, -2.4F), PICKAXE(1F, -2.8F), AXE(5.5F, -3.0F), SHOVEL(1.5F, -3.0F), HOE(-4F, 0F), PAXEL(4F, -2.8F);

        public final float damage, speed;

        Kind(float damage, float speed) {
            this.damage = damage;
            this.speed = speed;
        }

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    ExoticMaterial material();

    Kind kind();

    /** Item properties for a material and kind: tool rules, attributes, rarity, glint and unbreakability. */
    static Item.Properties properties(Item.Properties p, ExoticMaterial m, Kind k) {
        p = switch (k) {
            case SWORD -> p.sword(m.tool, k.damage, k.speed);
            case PICKAXE -> p.pickaxe(m.tool, k.damage, k.speed);
            case AXE, SHOVEL, HOE, PAXEL -> p;
        };
        p = p.rarity(m.rarity).fireResistant();
        if (m.unbreakable()) p = p.component(DataComponents.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE);
        if (m == ExoticMaterial.QUANTUM) p = p.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return p;
    }

    static Item create(ExoticMaterial m, Kind k, Item.Properties p) {
        Item.Properties props = properties(p, m, k);
        return switch (k) {
            case SWORD, PICKAXE -> new Simple(m, k, props);
            case AXE -> new Axe(m, props);
            case SHOVEL -> new Shovel(m, props);
            case HOE -> new Hoe(m, props);
            case PAXEL -> new Paxel(m, props);
        };
    }

    static void tooltip(ExoticTool tool, Consumer<Component> out) {
        ExoticMaterial m = tool.material();
        String base = "tooltip.powerfactor.exotic.";
        if (tool.kind() == Kind.SWORD) {
            out.accept(Component.translatable(base + "sword." + m.id).withColor(m.color));
            return;
        }
        if (tool.kind() == Kind.PAXEL) out.accept(Component.translatable(base + "paxel").withStyle(ChatFormatting.GRAY));
        if (m.excavates() && (tool.kind() == Kind.PICKAXE || tool.kind() == Kind.SHOVEL || tool.kind() == Kind.PAXEL)) out.accept(Component.translatable(base + "excavate").withColor(m.color));
        if (m.veinMines() && (tool.kind() == Kind.PICKAXE || tool.kind() == Kind.AXE || tool.kind() == Kind.PAXEL)) out.accept(Component.translatable(base + "vein").withColor(m.color));
        if (m.magnetic()) out.accept(Component.translatable(base + "magnet").withColor(m.color));
        if (m.excavates() || m.veinMines()) out.accept(Component.translatable(base + "sneak").withStyle(ChatFormatting.DARK_GRAY));
    }

    final class Simple extends Item implements ExoticTool {
        private final ExoticMaterial material;
        private final Kind kind;

        Simple(ExoticMaterial material, Kind kind, Properties properties) {
            super(properties);
            this.material = material;
            this.kind = kind;
        }

        @Override public ExoticMaterial material() { return material; }
        @Override public Kind kind() { return kind; }

        @Override
        public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            super.postHurtEnemy(stack, target, attacker);
            ExoticAbilities.onHit(this, stack, target, attacker);
        }

        @Override
        public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity owner) {
            boolean r = super.mineBlock(stack, level, state, pos, owner);
            ExoticAbilities.onMined(this, stack, level, state, pos, owner);
            return r;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
            ExoticTool.tooltip(this, builder);
        }
    }

    final class Axe extends AxeItem implements ExoticTool {
        private final ExoticMaterial material;

        Axe(ExoticMaterial material, Properties properties) {
            super(material.tool, Kind.AXE.damage, Kind.AXE.speed, properties);
            this.material = material;
        }

        @Override public ExoticMaterial material() { return material; }
        @Override public Kind kind() { return Kind.AXE; }

        @Override
        public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity owner) {
            boolean r = super.mineBlock(stack, level, state, pos, owner);
            ExoticAbilities.onMined(this, stack, level, state, pos, owner);
            return r;
        }

        @Override
        public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            super.postHurtEnemy(stack, target, attacker);
            ExoticAbilities.onHit(this, stack, target, attacker);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
            ExoticTool.tooltip(this, builder);
        }
    }

    final class Shovel extends ShovelItem implements ExoticTool {
        private final ExoticMaterial material;

        Shovel(ExoticMaterial material, Properties properties) {
            super(material.tool, Kind.SHOVEL.damage, Kind.SHOVEL.speed, properties);
            this.material = material;
        }

        @Override public ExoticMaterial material() { return material; }
        @Override public Kind kind() { return Kind.SHOVEL; }

        @Override
        public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity owner) {
            boolean r = super.mineBlock(stack, level, state, pos, owner);
            ExoticAbilities.onMined(this, stack, level, state, pos, owner);
            return r;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
            ExoticTool.tooltip(this, builder);
        }
    }

    final class Hoe extends HoeItem implements ExoticTool {
        private final ExoticMaterial material;

        Hoe(ExoticMaterial material, Properties properties) {
            super(material.tool, Kind.HOE.damage, Kind.HOE.speed, properties);
            this.material = material;
        }

        @Override public ExoticMaterial material() { return material; }
        @Override public Kind kind() { return Kind.HOE; }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
            ExoticTool.tooltip(this, builder);
        }
    }

    /** The exotic paxel: every tool's abilities in one, fused in the Starforge. */
    final class Paxel extends PaxelItem implements ExoticTool {
        private final ExoticMaterial material;

        Paxel(ExoticMaterial material, Properties properties) {
            super(material.tool, Kind.PAXEL.damage, properties);
            this.material = material;
        }

        @Override public ExoticMaterial material() { return material; }
        @Override public Kind kind() { return Kind.PAXEL; }

        @Override
        public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity owner) {
            boolean r = super.mineBlock(stack, level, state, pos, owner);
            ExoticAbilities.onMined(this, stack, level, state, pos, owner);
            return r;
        }

        @Override
        public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            super.postHurtEnemy(stack, target, attacker);
            ExoticAbilities.onHit(this, stack, target, attacker);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
            ExoticTool.tooltip(this, builder);
        }
    }
}
