package com.powerfactor.storage;

import com.powerfactor.registry.ModStorage;
import com.powerfactor.storage.craft.CraftingJob;
import com.powerfactor.storage.craft.CraftingPlanner;
import com.powerfactor.storage.craft.CraftingProcessorBlockEntity;
import com.powerfactor.storage.craft.EncodedPatternItem;
import com.powerfactor.storage.craft.PatternData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/**
 * The terminal menu. The item grid itself is virtual (synced by {@link GridPayloads.Sync}); real slots
 * are the player's inventory, the crafting terminal's 3x3 grid and result, and the pattern
 * terminal's ghost recipe, blank-pattern and encoded-pattern slots.
 */
public class TerminalMenu extends AbstractContainerMenu {
    public static final int SYNC_BATCH = 512;
    public static final int WIDTH = 195, GRID_X = 9, GRID_Y = 19, COLS = 9;
    public static final int BUTTON_ENCODE = 0, BUTTON_MODE = 1, BUTTON_CLEAR_PATTERN = 2, BUTTON_CLEAR_GRID = 3;

    /** What a terminal is attached to: a terminal block, or a wireless terminal's linked controller. */
    public interface Host {
        @Nullable StorageNetwork hostNetwork();

        boolean hostValid(Player player);
    }

    private final TerminalBlockEntity.Kind kind;
    private final @Nullable Host host;
    private final Player player;
    private final int rows;

    // Crafting terminal.
    private final TransientCraftingContainer craft;
    private final ResultContainer craftResult = new ResultContainer();
    // Pattern terminal: 9 ghost inputs, 3 ghost outputs, blank in, encoded out.
    private final SimpleContainer ghostIn = new SimpleContainer(9), ghostOut = new SimpleContainer(3), patternIo = new SimpleContainer(2);
    private final DataSlot processing = DataSlot.standalone();

    private int firstPlayerSlot, firstCraftSlot = -1, resultSlot = -1, firstGhost = -1, firstGhostOut = -1, patternInSlot = -1, patternOutSlot = -1;

    // Server sync state.
    private final Map<ItemResource, Long> sent = new HashMap<>();
    private final Set<ItemResource> sentCraftable = new HashSet<>();
    private long sentVersion = -1;
    private int sentStatus = -1;
    private boolean watchingJobs;

    public TerminalMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, TerminalBlockEntity.Kind.values()[buf.readVarInt()], null);
    }

    public TerminalMenu(int id, Inventory inventory, TerminalBlockEntity.Kind kind, @Nullable Host host) {
        super(ModStorage.TERMINAL_MENU.get(), id);
        this.kind = kind;
        this.host = host;
        this.player = inventory.player;
        this.rows = inventory.player.level().isClientSide() ? rows(kind) : (kind == TerminalBlockEntity.Kind.GRID ? 7 : 4);
        this.craft = new TransientCraftingContainer(this, 3, 3);
        int lowerTop = GRID_Y + rows * 18 + 6;
        if (kind == TerminalBlockEntity.Kind.CRAFTING) {
            resultSlot = slots.size();
            addSlot(new NetworkResultSlot(player, craft, craftResult, 0, 124, lowerTop + 19));
            firstCraftSlot = slots.size();
            for (int i = 0; i < 9; i++) addSlot(new Slot(craft, i, 30 + (i % 3) * 18, lowerTop + 1 + (i / 3) * 18));
        } else if (kind == TerminalBlockEntity.Kind.PATTERN) {
            firstGhost = slots.size();
            for (int i = 0; i < 9; i++) addSlot(new IoPortMenu.GhostSlot(ghostIn, i, 9 + (i % 3) * 18, lowerTop + 1 + (i / 3) * 18));
            firstGhostOut = slots.size();
            for (int i = 0; i < 3; i++) addSlot(new IoPortMenu.GhostSlot(ghostOut, i, 99, lowerTop + 1 + i * 18));
            patternInSlot = slots.size();
            addSlot(new Slot(patternIo, 0, 147, lowerTop + 1) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.is(ModStorage.BLANK_PATTERN.get()) || stack.getItem() instanceof EncodedPatternItem;
                }
            });
            patternOutSlot = slots.size();
            addSlot(new Slot(patternIo, 1, 147, lowerTop + 37) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
            addDataSlot(processing);
        }
        firstPlayerSlot = slots.size();
        addStandardInventorySlots(inventory, 9, inventoryTop(kind, rows));
    }

    /**
     * The client's scaled GUI height, set every client tick, so terminals use as many grid rows as fit
     * (AE2-style). Slot positions only matter client-side; the server keeps the default.
     */
    public static volatile int clientGuiHeight = 0;

    public static int rows(TerminalBlockEntity.Kind kind) {
        int fallback = kind == TerminalBlockEntity.Kind.GRID ? 7 : 4;
        if (clientGuiHeight <= 0) return fallback;
        int fixed = GRID_Y + 6 + lowerHeight(kind) + 14 + 84 + 6;
        return Math.clamp((clientGuiHeight - fixed) / 18, 3, 11);
    }

    public static int lowerHeight(TerminalBlockEntity.Kind kind) {
        return kind == TerminalBlockEntity.Kind.GRID ? 0 : 60;
    }

    public static int inventoryTop(TerminalBlockEntity.Kind kind, int rows) {
        return GRID_Y + rows * 18 + 6 + lowerHeight(kind) + 14;
    }

    public static int height(TerminalBlockEntity.Kind kind, int rows) {
        return inventoryTop(kind, rows) + 76 + 8;
    }

    public TerminalBlockEntity.Kind kind() {
        return kind;
    }

    public int rows() {
        return rows;
    }

    public boolean processingMode() {
        return processing.get() == 1;
    }

    public int firstGhost() {
        return firstGhost;
    }

    public int firstGhostOut() {
        return firstGhostOut;
    }

    private @Nullable StorageNetwork network() {
        if (host == null) return null;
        StorageNetwork n = host.hostNetwork();
        return n != null && n.online() ? n : null;
    }

    @Override
    public boolean stillValid(Player player) {
        return host == null || host.hostValid(player);
    }

    // ------------------------------------------------------------------ sync

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        // Players without PowerFactor's channel (and test mock players) get no grid view.
        if (!(player instanceof ServerPlayer sp) || host == null || !sp.connection.hasChannel(GridPayloads.Sync.TYPE)) return;
        StorageNetwork raw = host.hostNetwork();
        int status = raw == null ? 3 : raw.conflict() ? 2 : !raw.online() ? 1 : 0;
        StorageNetwork n = status == 0 ? raw : null;
        long version = n == null ? -2 : n.version();
        boolean statusChanged = status != sentStatus;
        if (watchingJobs && sp.level().getGameTime() % 5 == 0) sendJobs(sp);
        if (version == sentVersion && !statusChanged) return;
        boolean reset = sentVersion == -1 || statusChanged;
        sentVersion = version;
        sentStatus = status;
        Map<ItemResource, Long> now = n == null ? Map.of() : n.totals();
        Set<ItemResource> craftable = n == null ? Set.of() : n.craftables();
        List<GridPayloads.Entry> changes = new ArrayList<>();
        if (reset) {
            sent.clear();
            sentCraftable.clear();
        }
        for (var e : now.entrySet()) {
            boolean c = craftable.contains(e.getKey());
            Long old = sent.get(e.getKey());
            if (old == null || old.longValue() != e.getValue() || c != sentCraftable.contains(e.getKey())) {
                changes.add(new GridPayloads.Entry(e.getKey(), e.getValue(), c));
            }
        }
        for (ItemResource r : craftable) {
            if (!now.containsKey(r) && (sent.containsKey(r) || !sentCraftable.contains(r))) changes.add(new GridPayloads.Entry(r, 0, true));
        }
        for (ItemResource r : sent.keySet()) {
            if (!now.containsKey(r) && !craftable.contains(r)) changes.add(new GridPayloads.Entry(r, 0, false));
        }
        for (ItemResource r : sentCraftable) {
            if (!now.containsKey(r) && !craftable.contains(r) && !sent.containsKey(r)) changes.add(new GridPayloads.Entry(r, 0, false));
        }
        sent.clear();
        sent.putAll(now);
        sentCraftable.clear();
        sentCraftable.addAll(craftable);
        long used = n == null ? 0 : n.usedBytes(), total = n == null ? 0 : n.totalBytes();
        int jobs = n == null ? 0 : n.jobs().size();
        if (changes.isEmpty() && !reset) {
            sp.connection.send(new GridPayloads.Sync(containerId, false, status, used, total, jobs, List.of()));
            return;
        }
        for (int i = 0; i < Math.max(1, changes.size()); i += SYNC_BATCH) {
            List<GridPayloads.Entry> batch = changes.subList(i, Math.min(changes.size(), i + SYNC_BATCH));
            sp.connection.send(new GridPayloads.Sync(containerId, reset && i == 0, status, used, total, jobs, List.copyOf(batch)));
        }
    }

    // ------------------------------------------------------------------ grid clicks

    void handleAction(ServerPlayer sp, ItemResource r, int action) {
        StorageNetwork n = network();
        if (n == null) return;
        ItemStack carried = getCarried();
        switch (action) {
            case GridPayloads.Action.INSERT_ALL, GridPayloads.Action.INSERT_ONE -> {
                if (carried.isEmpty()) return;
                int want = action == GridPayloads.Action.INSERT_ONE ? 1 : carried.getCount();
                long put = n.insert(ItemResource.of(carried), want, false);
                carried.shrink((int) put);
                setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
            }
            case GridPayloads.Action.TAKE_STACK, GridPayloads.Action.TAKE_HALF, GridPayloads.Action.TAKE_ONE -> {
                if (r.isEmpty()) return;
                if (!carried.isEmpty() && !(action == GridPayloads.Action.TAKE_ONE && r.matches(carried))) {
                    // Clicking the grid while holding something deposits it instead.
                    long put = n.insert(ItemResource.of(carried), carried.getCount(), false);
                    carried.shrink((int) put);
                    setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
                    return;
                }
                int max = r.getMaxStackSize();
                int want = switch (action) {
                    case GridPayloads.Action.TAKE_STACK -> max;
                    case GridPayloads.Action.TAKE_HALF -> (int) Math.max(1, Math.min(max, (n.count(r) + 1) / 2));
                    default -> carried.isEmpty() || carried.getCount() < max ? 1 : 0;
                };
                long got = n.extract(r, want, false);
                if (got <= 0) return;
                if (carried.isEmpty()) setCarried(r.toStack((int) got));
                else carried.grow((int) got);
            }
            case GridPayloads.Action.SHIFT -> {
                if (r.isEmpty()) return;
                ItemStack stack = r.toStack((int) n.extract(r, r.getMaxStackSize(), true));
                if (stack.isEmpty()) return;
                int before = stack.getCount();
                moveItemStackTo(stack, firstPlayerSlot, firstPlayerSlot + 36, true);
                int moved = before - stack.getCount();
                if (moved > 0) n.extract(r, moved, false);
            }
            case GridPayloads.Action.CLEAR_GRID -> returnCraftGrid(n);
            default -> {}
        }
        broadcastChanges();
    }

    private void returnCraftGrid(StorageNetwork n) {
        for (int i = 0; i < craft.getContainerSize(); i++) {
            ItemStack s = craft.getItem(i);
            if (s.isEmpty()) continue;
            long put = n.insert(ItemResource.of(s), s.getCount(), false);
            s.shrink((int) put);
            craft.setItem(i, s.isEmpty() ? ItemStack.EMPTY : s);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        StorageNetwork n = network();
        if (index >= firstPlayerSlot) {
            // Inventory to network.
            if (n == null || player.level().isClientSide()) return ItemStack.EMPTY;
            ItemStack stack = slot.getItem();
            long put = n.insert(ItemResource.of(stack), stack.getCount(), false);
            if (put <= 0) return ItemStack.EMPTY;
            stack.shrink((int) put);
            slot.setChanged();
            return ItemStack.EMPTY;
        }
        if (index == resultSlot) {
            ItemStack stack = slot.getItem();
            ItemStack original = stack.copy();
            if (!moveItemStackTo(stack, firstPlayerSlot, firstPlayerSlot + 36, true)) return ItemStack.EMPTY;
            slot.onQuickCraft(stack, original);
            if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
            else slot.setChanged();
            slot.onTake(player, stack);
            return original;
        }
        if (slot instanceof IoPortMenu.GhostSlot) {
            slot.set(ItemStack.EMPTY);
            return ItemStack.EMPTY;
        }
        // Crafting grid and pattern slots go to the network first, then the inventory.
        ItemStack stack = slot.getItem();
        if (n != null && !player.level().isClientSide()) {
            long put = n.insert(ItemResource.of(stack), stack.getCount(), false);
            stack.shrink((int) put);
        }
        if (!stack.isEmpty()) moveItemStackTo(stack, firstPlayerSlot, firstPlayerSlot + 36, true);
        slot.setChanged();
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != craftResult && !(slot instanceof IoPortMenu.GhostSlot);
    }

    // ------------------------------------------------------------------ crafting terminal

    @Override
    public void slotsChanged(Container container) {
        if (container == craft && player.level() instanceof ServerLevel level) updateResult(level);
        super.slotsChanged(container);
    }

    private void updateResult(ServerLevel level) {
        CraftingInput input = craft.asCraftInput();
        ItemStack result = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(h -> h.value().assemble(input)).orElse(ItemStack.EMPTY);
        craftResult.setItem(0, result);
        if (resultSlot >= 0) setRemoteSlot(resultSlot, result);
        if (player instanceof ServerPlayer sp) {
            sp.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(containerId, incrementStateId(),
                    resultSlot, result));
        }
    }

    /** Taking the result refills the grid from the network, like an AE2 crafting terminal. */
    private class NetworkResultSlot extends ResultSlot {
        NetworkResultSlot(Player player, TransientCraftingContainer craft, Container result, int id, int x, int y) {
            super(player, craft, result, id, x, y);
        }

        @Override
        public void onTake(Player player, ItemStack carried) {
            ItemResource[] before = new ItemResource[9];
            for (int i = 0; i < 9; i++) before[i] = craft.getItem(i).isEmpty() ? ItemResource.EMPTY : ItemResource.of(craft.getItem(i));
            super.onTake(player, carried);
            StorageNetwork n = network();
            if (n == null || player.level().isClientSide()) return;
            for (int i = 0; i < 9; i++) {
                if (!before[i].isEmpty() && craft.getItem(i).isEmpty() && n.extract(before[i], 1, false) == 1) {
                    craft.setItem(i, before[i].toStack(1));
                }
            }
        }
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player.level().isClientSide()) return;
        StorageNetwork n = network();
        if (n != null) returnCraftGrid(n);
        clearContainer(player, craft);
        // The pattern terminal's blank/encoded slots belong to the player.
        clearContainer(player, patternIo);
    }

    // ------------------------------------------------------------------ pattern terminal

    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player player) {
        if (slotId >= 0 && slotId < slots.size() && slots.get(slotId) instanceof IoPortMenu.GhostSlot ghost && input == ContainerInput.PICKUP) {
            ItemStack carried = getCarried();
            ItemStack current = ghost.getItem();
            boolean counts = processingMode();
            if (carried.isEmpty()) {
                if (button == 1 && counts && current.getCount() > 1) current.setCount(current.getCount() / 2);
                else ghost.set(ItemStack.EMPTY);
            } else if (counts && ItemStack.isSameItemSameComponents(carried, current)) {
                current.setCount(Math.min(current.getMaxStackSize() * 4, current.getCount() + (button == 1 ? 1 : carried.getCount())));
            } else {
                ghost.set(carried.copyWithCount(counts && button == 0 ? carried.getCount() : 1));
            }
            ghost.setChanged();
            return;
        }
        super.clicked(slotId, button, input, player);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        switch (id) {
            case BUTTON_MODE -> {
                processing.set(processingMode() ? 0 : 1);
                if (!processingMode()) for (int i = 0; i < 9; i++) if (!ghostIn.getItem(i).isEmpty()) ghostIn.getItem(i).setCount(1);
                return true;
            }
            case BUTTON_CLEAR_PATTERN -> {
                ghostIn.clearContent();
                ghostOut.clearContent();
                return true;
            }
            case BUTTON_CLEAR_GRID -> {
                StorageNetwork n = network();
                if (n != null) returnCraftGrid(n);
                return true;
            }
            case BUTTON_ENCODE -> {
                return encode(player);
            }
            default -> {
                return false;
            }
        }
    }

    private boolean encode(Player player) {
        if (kind != TerminalBlockEntity.Kind.PATTERN || !patternIo.getItem(1).isEmpty()) return false;
        ItemStack blank = patternIo.getItem(0);
        if (blank.isEmpty()) return false;
        PatternData data = buildPattern(player);
        if (data == null || !data.valid()) return false;
        blank.shrink(1);
        patternIo.setItem(0, blank.isEmpty() ? ItemStack.EMPTY : blank);
        patternIo.setItem(1, EncodedPatternItem.encode(data));
        return true;
    }

    private @Nullable PatternData buildPattern(Player player) {
        List<PatternData.Entry> in = new ArrayList<>();
        for (int i = 0; i < 9; i++) in.add(PatternData.Entry.of(ghostIn.getItem(i)));
        if (processingMode()) {
            List<PatternData.Entry> out = new ArrayList<>();
            for (int i = 0; i < 3; i++) if (!ghostOut.getItem(i).isEmpty()) out.add(PatternData.Entry.of(ghostOut.getItem(i)));
            List<PatternData.Entry> compact = in.stream().filter(e -> !e.isEmpty()).toList();
            return new PatternData(true, compact, out);
        }
        if (!(player.level() instanceof ServerLevel level)) return null;
        List<ItemStack> grid = new ArrayList<>();
        for (int i = 0; i < 9; i++) grid.add(ghostIn.getItem(i).copyWithCount(1));
        CraftingInput input = CraftingInput.of(3, 3, grid);
        ItemStack result = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(h -> h.value().assemble(input)).orElse(ItemStack.EMPTY);
        if (result.isEmpty()) return null;
        List<PatternData.Entry> cells = new ArrayList<>();
        for (ItemStack s : grid) cells.add(PatternData.Entry.of(s));
        return new PatternData(false, cells, List.of(PatternData.Entry.of(result)));
    }

    // ------------------------------------------------------------------ recipe-viewer transfer

    public void handleTransfer(ServerPlayer sp, GridPayloads.Transfer t) {
        List<List<ItemStack>> inputs = t.inputs().size() > 81 ? t.inputs().subList(0, 81) : t.inputs();
        StorageNetwork n = network();
        if (kind == TerminalBlockEntity.Kind.PATTERN) {
            processing.set(t.processing() ? 1 : 0);
            ghostIn.clearContent();
            ghostOut.clearContent();
            if (t.processing()) {
                // Processing patterns: merge identical inputs, keep amounts, up to nine kinds.
                List<ItemStack> merged = new ArrayList<>();
                for (List<ItemStack> alts : inputs) {
                    ItemStack pick = preferred(n, alts);
                    if (pick.isEmpty()) continue;
                    merge(merged, pick);
                }
                for (int i = 0; i < Math.min(9, merged.size()); i++) ghostIn.setItem(i, merged.get(i));
                List<ItemStack> outs = new ArrayList<>();
                for (ItemStack o : t.outputs()) if (!o.isEmpty()) merge(outs, o.copy());
                for (int i = 0; i < Math.min(3, outs.size()); i++) ghostOut.setItem(i, outs.get(i));
            } else {
                for (int i = 0; i < Math.min(9, inputs.size()); i++) ghostIn.setItem(i, preferred(n, inputs.get(i)).copyWithCount(1));
            }
        } else if (kind == TerminalBlockEntity.Kind.CRAFTING && !t.processing()) {
            if (n != null) returnCraftGrid(n);
            for (int i = 0; i < craft.getContainerSize(); i++) {
                if (!craft.getItem(i).isEmpty()) moveItemStackTo(craft.getItem(i), firstPlayerSlot, firstPlayerSlot + 36, true);
            }
            // One of each first, preferring whichever alternative the network holds most of.
            for (int i = 0; i < Math.min(9, inputs.size()); i++) {
                // Never overwrite something that could not be put away.
                if (!craft.getItem(i).isEmpty()) continue;
                List<ItemStack> alts = new ArrayList<>(inputs.get(i).stream().filter(x -> !x.isEmpty()).toList());
                alts.sort(java.util.Comparator.comparingLong((ItemStack x) -> n == null ? 0 : n.count(ItemResource.of(x))).reversed());
                for (ItemStack alt : alts) {
                    ItemStack got = pull(n, sp, alt, 1);
                    if (got.isEmpty()) continue;
                    craft.setItem(i, got);
                    break;
                }
            }
            // Shift-transfer then tops every slot up a round at a time, so all slots craft equally often.
            if (t.max()) {
                boolean more = true;
                while (more) {
                    List<Integer> round = new ArrayList<>();
                    for (int i = 0; i < 9 && more; i++) {
                        ItemStack s = craft.getItem(i);
                        if (s.isEmpty()) continue;
                        if (s.getCount() >= s.getMaxStackSize() || pull(n, sp, s, 1).isEmpty()) more = false;
                        else round.add(i);
                    }
                    if (round.isEmpty()) break;
                    if (more) {
                        for (int i : round) craft.getItem(i).grow(1);
                    } else {
                        // This round could not finish: hand back what it took.
                        for (int i : round) {
                            ItemStack back = craft.getItem(i).copyWithCount(1);
                            if (n != null) back.shrink((int) n.insert(ItemResource.of(back), 1, false));
                            if (!back.isEmpty() && !sp.getInventory().add(back)) sp.drop(back, false);
                        }
                    }
                }
            }
            slotsChanged(craft);
        }
        broadcastChanges();
    }

    /** The alternative the network holds most of, else the first. */
    private static ItemStack preferred(@Nullable StorageNetwork n, List<ItemStack> alts) {
        ItemStack best = ItemStack.EMPTY;
        long bestCount = -1;
        for (ItemStack alt : alts) {
            if (alt.isEmpty()) continue;
            long c = n == null ? 0 : n.count(ItemResource.of(alt));
            if (c > bestCount) {
                best = alt;
                bestCount = c;
            }
        }
        return best.copy();
    }

    private static void merge(List<ItemStack> list, ItemStack stack) {
        for (ItemStack s : list) {
            if (ItemStack.isSameItemSameComponents(s, stack)) {
                s.grow(stack.getCount());
                return;
            }
        }
        list.add(stack);
    }

    /** Takes up to {@code limit} of an item from the network, then from the player's inventory. */
    private ItemStack pull(@Nullable StorageNetwork n, ServerPlayer sp, ItemStack like, int limit) {
        int got = 0;
        if (n != null) got = (int) n.extract(ItemResource.of(like), limit, false);
        Inventory inv = sp.getInventory();
        for (int s = 0; s < inv.getContainerSize() && got < limit; s++) {
            ItemStack here = inv.getItem(s);
            if (!ItemStack.isSameItemSameComponents(here, like)) continue;
            int take = Math.min(limit - got, here.getCount());
            here.shrink(take);
            got += take;
        }
        return got <= 0 ? ItemStack.EMPTY : like.copyWithCount(got);
    }

    // ------------------------------------------------------------------ autocrafting

    void handleCraft(ServerPlayer sp, ItemResource target, long amount, boolean start) {
        StorageNetwork n = network();
        if (n == null || amount <= 0 || target.isEmpty()) {
            sp.connection.send(new GridPayloads.PlanReply(target, amount, 4, 0, List.of()));
            return;
        }
        var outcome = com.powerfactor.storage.craft.CraftingService.order(n, target, amount, sp.getGameProfile().name(), start);
        var plan = outcome.plan();
        int result = outcome.result().ordinal();
        sp.connection.send(new GridPayloads.PlanReply(target, amount, result, plan.totalCrafts(), lines(plan)));
    }

    private static List<GridPayloads.PlanLine> lines(CraftingPlanner.Plan<ItemResource> plan) {
        Map<ItemResource, long[]> m = new LinkedHashMap<>();
        plan.missing().forEach((k, v) -> m.computeIfAbsent(k, x -> new long[3])[2] += v);
        plan.crafted().forEach((k, v) -> m.computeIfAbsent(k, x -> new long[3])[1] += v);
        plan.used().forEach((k, v) -> m.computeIfAbsent(k, x -> new long[3])[0] += v);
        List<GridPayloads.PlanLine> out = new ArrayList<>();
        m.forEach((k, v) -> out.add(new GridPayloads.PlanLine(k, v[0], v[1], v[2])));
        return out.size() > 512 ? out.subList(0, 512) : out;
    }

    void handleJobs(ServerPlayer sp, int op, int cpu, int index) {
        switch (op) {
            case 0 -> {
                watchingJobs = true;
                sendJobs(sp);
            }
            case 1 -> watchingJobs = false;
            case 2 -> {
                StorageNetwork n = network();
                if (n == null) return;
                List<CraftingProcessorBlockEntity> cpus = n.processors();
                if (cpu >= 0 && cpu < cpus.size()) cpus.get(cpu).cancel(index, n);
                sendJobs(sp);
            }
            default -> {}
        }
    }

    private void sendJobs(ServerPlayer sp) {
        StorageNetwork n = network();
        if (n == null) {
            sp.connection.send(new GridPayloads.Jobs(0, 0, List.of()));
            return;
        }
        List<CraftingProcessorBlockEntity> cpus = n.processors();
        List<GridPayloads.JobLine> lines = new ArrayList<>();
        int free = 0;
        for (int c = 0; c < cpus.size(); c++) {
            CraftingProcessorBlockEntity cpu = cpus.get(c);
            if (cpu.hasRoom()) free++;
            List<CraftingJob> jobs = cpu.jobs();
            for (int j = 0; j < jobs.size() && lines.size() < 64; j++) {
                CraftingJob job = jobs.get(j);
                List<GridPayloads.PlanLine> pending = new ArrayList<>();
                for (var step : job.steps()) {
                    if (step.remaining <= 0 || step.pattern == null) continue;
                    for (var out : step.pattern.outputMap().entrySet()) {
                        if (pending.size() < 64) pending.add(new GridPayloads.PlanLine(out.getKey(), 0, out.getValue() * step.remaining, 0));
                    }
                }
                for (var a : job.awaiting().entrySet()) {
                    if (pending.size() < 64) pending.add(new GridPayloads.PlanLine(a.getKey(), a.getValue(), 0, 0));
                }
                int state = job.state() == CraftingJob.State.RUNNING ? (job.stalled() ? 3 : 0) : 1;
                lines.add(new GridPayloads.JobLine(c, j, job.target(), job.amount(), job.progress(), state, cpu.quantum(), pending));
            }
        }
        sp.connection.send(new GridPayloads.Jobs(cpus.size(), free, lines));
    }
}
