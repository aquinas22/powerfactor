package com.powerfactor.storage.craft;

import com.powerfactor.registry.ModStorage;
import com.powerfactor.storage.NodeMenu;
import com.powerfactor.upgrade.UpgradeItem;
import com.powerfactor.upgrade.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Nine pattern slots, shared by the Assembler (crafting patterns + speed cards) and Pattern Provider (processing). */
public class PatternRackMenu extends NodeMenu {
    public static final int PATTERN_X = 62, PATTERN_Y = 22, UPGRADE_X = 152, UPGRADE_Y = 21, BUTTON_BLOCKING = 0;   // upgrade column fills the content zone (y 20-92)

    private final boolean assembler;
    private final @Nullable PatternProviderBlockEntity provider;
    private final DataSlot flags = DataSlot.standalone();

    public PatternRackMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos(), buf.readBoolean(), new SimpleContainer(9), new SimpleContainer(4), null);
    }

    public PatternRackMenu(int id, Inventory inventory, AssemblerBlockEntity a) {
        this(id, inventory, a.getBlockPos(), true, a.patternSlots(), a.upgrades(), null);
    }

    public PatternRackMenu(int id, Inventory inventory, PatternProviderBlockEntity p) {
        this(id, inventory, p.getBlockPos(), false, p.patternSlots(), new SimpleContainer(4), p);
    }

    private PatternRackMenu(int id, Inventory inventory, BlockPos pos, boolean assembler, Container patterns, Container upgrades,
            @Nullable PatternProviderBlockEntity provider) {
        super(ModStorage.PATTERN_RACK_MENU.get(), id, inventory, pos);
        this.assembler = assembler;
        this.provider = provider;
        for (int i = 0; i < 9; i++) {
            addSlot(new Slot(patterns, i, PATTERN_X + (i % 3) * 18, PATTERN_Y + (i / 3) * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return assembler ? EncodedPatternItem.crafting(stack) : EncodedPatternItem.processing(stack);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        if (assembler) {
            for (int i = 0; i < 4; i++) {
                addSlot(new Slot(upgrades, i, UPGRADE_X, UPGRADE_Y + i * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return stack.getItem() instanceof UpgradeItem u && u.type() == UpgradeType.SPEED;
                    }
                });
            }
        }
        addPlayerInventory(inventory, 8, 108);
        addDataSlot(flags);
        if (provider != null) flags.set(provider.blocking() ? 1 : 0);
    }

    public boolean assembler() {
        return assembler;
    }

    public boolean blocking() {
        return flags.get() == 1;
    }

    @Override
    public void broadcastChanges() {
        if (provider != null) flags.set(provider.blocking() ? 1 : 0);
        super.broadcastChanges();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (provider != null && id == BUTTON_BLOCKING) {
            provider.toggleBlocking();
            return true;
        }
        return false;
    }
}
