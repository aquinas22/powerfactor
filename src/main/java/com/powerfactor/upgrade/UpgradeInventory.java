package com.powerfactor.upgrade;

import com.powerfactor.power.UpgradeEffects;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** A machine's four upgrade slots: only supported cards, one slot per card type. */
public class UpgradeInventory extends SimpleContainer {
    public static final int SLOTS = 4;

    private final Set<UpgradeType> supported;
    private final Runnable onChange;
    private final java.util.function.Predicate<Player> stillValid;
    private UpgradeEffects effects = UpgradeEffects.NONE;

    public UpgradeInventory(Set<UpgradeType> supported, Runnable onChange, java.util.function.Predicate<Player> stillValid) {
        super(SLOTS);
        this.supported = supported;
        this.onChange = onChange;
        this.stillValid = stillValid;
    }

    /** Client-side placeholder with every type allowed; the server copy does the real checking. */
    public static UpgradeInventory client() {
        return new UpgradeInventory(Set.of(UpgradeType.values()), () -> {}, p -> true);
    }

    public Set<UpgradeType> supported() {
        return supported;
    }

    public boolean accepts(int slot, ItemStack stack) {
        if (!(stack.getItem() instanceof UpgradeItem upgrade) || !supported.contains(upgrade.type())) {
            return false;
        }
        for (int i = 0; i < SLOTS; i++) {
            ItemStack other = getItem(i);
            if (i != slot && other.getItem() instanceof UpgradeItem o && o.type() == upgrade.type()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return accepts(slot, stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid.test(player);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        recompute();
        onChange.run();
    }

    public UpgradeEffects effects() {
        return effects;
    }

    private void recompute() {
        Map<UpgradeType, Integer> counts = new EnumMap<>(UpgradeType.class);
        for (int i = 0; i < SLOTS; i++) {
            ItemStack s = getItem(i);
            if (s.getItem() instanceof UpgradeItem u && supported.contains(u.type())) {
                counts.merge(u.type(), Math.min(s.getCount(), u.type().maxCount), Integer::sum);
            }
        }
        effects = new UpgradeEffects(counts.getOrDefault(UpgradeType.SPEED, 0), counts.getOrDefault(UpgradeType.EFFICIENCY, 0),
                counts.getOrDefault(UpgradeType.CAPACITOR, 0), counts.containsKey(UpgradeType.VFD), counts.containsKey(UpgradeType.SURGE),
                counts.getOrDefault(UpgradeType.CAPACITY, 0));
    }

    public void load(ValueInput input) {
        ContainerHelper.loadAllItems(input.childOrEmpty("upgrades"), getItems());
        recompute();
    }

    public void save(ValueOutput output) {
        ContainerHelper.saveAllItems(output.child("upgrades"), getItems());
    }
}
