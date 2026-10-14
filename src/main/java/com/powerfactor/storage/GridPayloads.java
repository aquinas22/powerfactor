package com.powerfactor.storage;

import com.powerfactor.PowerFactor;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.transfer.item.ItemResource;

/** Terminal networking: the item list, clicks on it, craft planning and the crafting-status view. */
public final class GridPayloads {
    /** A change to one item in the terminal's view. Zero count and not craftable removes it. */
    public record Entry(ItemResource resource, long count, boolean craftable) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ItemResource.STREAM_CODEC, Entry::resource, ByteBufCodecs.VAR_LONG, Entry::count, ByteBufCodecs.BOOL, Entry::craftable, Entry::new);
    }

    /** Server to client: item view changes plus the network's status line. status: 0 online, 1 no power, 2 conflict, 3 no network. */
    public record Sync(int containerId, boolean reset, int status, long usedBytes, long totalBytes, int jobs, List<Entry> entries)
            implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(PowerFactor.id("grid_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Sync::containerId, ByteBufCodecs.BOOL, Sync::reset, ByteBufCodecs.VAR_INT, Sync::status,
                ByteBufCodecs.VAR_LONG, Sync::usedBytes, ByteBufCodecs.VAR_LONG, Sync::totalBytes, ByteBufCodecs.VAR_INT, Sync::jobs,
                Entry.STREAM_CODEC.apply(ByteBufCodecs.list(TerminalMenu.SYNC_BATCH)), Sync::entries, Sync::new);

        @Override
        public Type<Sync> type() {
            return TYPE;
        }
    }

    /** Client to server: a click on a grid item (or on empty grid space, with EMPTY). */
    public record Action(int containerId, ItemResource resource, int action) implements CustomPacketPayload {
        public static final int TAKE_STACK = 0, TAKE_HALF = 1, TAKE_ONE = 2, SHIFT = 3, INSERT_ALL = 4, INSERT_ONE = 5, CLEAR_GRID = 6;
        public static final Type<Action> TYPE = new Type<>(PowerFactor.id("grid_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Action::containerId, ItemResource.STREAM_CODEC, Action::resource, ByteBufCodecs.VAR_INT, Action::action,
                Action::new);

        @Override
        public Type<Action> type() {
            return TYPE;
        }
    }

    /** Client to server: plan (and optionally start) a craft. */
    public record CraftRequest(int containerId, ItemResource resource, long amount, boolean start) implements CustomPacketPayload {
        public static final Type<CraftRequest> TYPE = new Type<>(PowerFactor.id("craft_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CraftRequest> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, CraftRequest::containerId, ItemResource.STREAM_CODEC, CraftRequest::resource,
                ByteBufCodecs.VAR_LONG, CraftRequest::amount, ByteBufCodecs.BOOL, CraftRequest::start, CraftRequest::new);

        @Override
        public Type<CraftRequest> type() {
            return TYPE;
        }
    }

    /** One line of a plan preview: taken from storage, to be crafted, and missing. */
    public record PlanLine(ItemResource resource, long used, long crafted, long missing) {
        public static final StreamCodec<RegistryFriendlyByteBuf, PlanLine> STREAM_CODEC = StreamCodec.composite(
                ItemResource.STREAM_CODEC, PlanLine::resource, ByteBufCodecs.VAR_LONG, PlanLine::used, ByteBufCodecs.VAR_LONG, PlanLine::crafted,
                ByteBufCodecs.VAR_LONG, PlanLine::missing, PlanLine::new);
    }

    /** Server to client: the plan. result: 0 preview, 1 started, 2 no free processor, 3 storage changed, 4 offline. */
    public record PlanReply(ItemResource resource, long amount, int result, long crafts, List<PlanLine> lines) implements CustomPacketPayload {
        public static final Type<PlanReply> TYPE = new Type<>(PowerFactor.id("craft_plan"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PlanReply> STREAM_CODEC = StreamCodec.composite(
                ItemResource.STREAM_CODEC, PlanReply::resource, ByteBufCodecs.VAR_LONG, PlanReply::amount, ByteBufCodecs.VAR_INT, PlanReply::result,
                ByteBufCodecs.VAR_LONG, PlanReply::crafts, PlanLine.STREAM_CODEC.apply(ByteBufCodecs.list(512)), PlanReply::lines, PlanReply::new);

        @Override
        public Type<PlanReply> type() {
            return TYPE;
        }
    }

    /** One job in the crafting-status view. */
    public record JobLine(int cpu, int index, ItemResource target, long amount, float progress, int state, boolean quantum,
            List<PlanLine> pending) {
        public static final StreamCodec<RegistryFriendlyByteBuf, JobLine> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, JobLine::cpu, ByteBufCodecs.VAR_INT, JobLine::index, ItemResource.STREAM_CODEC, JobLine::target,
                ByteBufCodecs.VAR_LONG, JobLine::amount, ByteBufCodecs.FLOAT, JobLine::progress, ByteBufCodecs.VAR_INT, JobLine::state,
                ByteBufCodecs.BOOL, JobLine::quantum, PlanLine.STREAM_CODEC.apply(ByteBufCodecs.list(64)), JobLine::pending, JobLine::new);
    }

    /** Server to client: running jobs and processor count. */
    public record Jobs(int processors, int freeProcessors, List<JobLine> jobs) implements CustomPacketPayload {
        public static final Type<Jobs> TYPE = new Type<>(PowerFactor.id("craft_jobs"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Jobs> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Jobs::processors, ByteBufCodecs.VAR_INT, Jobs::freeProcessors,
                JobLine.STREAM_CODEC.apply(ByteBufCodecs.list(64)), Jobs::jobs, Jobs::new);

        @Override
        public Type<Jobs> type() {
            return TYPE;
        }
    }

    /** Client to server: watch (or stop watching) jobs, or cancel one. op: 0 watch, 1 unwatch, 2 cancel. */
    public record JobControl(int containerId, int op, int cpu, int index) implements CustomPacketPayload {
        public static final Type<JobControl> TYPE = new Type<>(PowerFactor.id("craft_job_control"));
        public static final StreamCodec<RegistryFriendlyByteBuf, JobControl> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, JobControl::containerId, ByteBufCodecs.VAR_INT, JobControl::op, ByteBufCodecs.VAR_INT, JobControl::cpu,
                ByteBufCodecs.VAR_INT, JobControl::index, JobControl::new);

        @Override
        public Type<JobControl> type() {
            return TYPE;
        }
    }

    /**
     * Client to server: a recipe moved over from a recipe viewer. Each input slot lists acceptable
     * alternatives; the pattern terminal writes them as ghosts (processing patterns keep counts), the
     * crafting terminal pulls real items from the network or the player's inventory.
     */
    public record Transfer(int containerId, boolean processing, boolean max, List<List<ItemStack>> inputs, List<ItemStack> outputs)
            implements CustomPacketPayload {
        public static final Type<Transfer> TYPE = new Type<>(PowerFactor.id("grid_transfer"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Transfer> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Transfer::containerId, ByteBufCodecs.BOOL, Transfer::processing, ByteBufCodecs.BOOL, Transfer::max,
                ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(64)).apply(ByteBufCodecs.list(81)), Transfer::inputs,
                ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(9)), Transfer::outputs, Transfer::new);

        @Override
        public Type<Transfer> type() {
            return TYPE;
        }
    }

    public static Consumer<Sync> syncHandler = s -> {};
    public static Consumer<PlanReply> planHandler = s -> {};
    public static Consumer<Jobs> jobsHandler = s -> {};

    private static TerminalMenu menu(ServerPlayer player, int id) {
        return player.containerMenu instanceof TerminalMenu m && m.containerId == id ? m : null;
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(Sync.TYPE, Sync.STREAM_CODEC, (p, c) -> syncHandler.accept(p));
        registrar.playToClient(PlanReply.TYPE, PlanReply.STREAM_CODEC, (p, c) -> planHandler.accept(p));
        registrar.playToClient(Jobs.TYPE, Jobs.STREAM_CODEC, (p, c) -> jobsHandler.accept(p));
        registrar.playToServer(Action.TYPE, Action.STREAM_CODEC, (p, c) -> {
            if (c.player() instanceof ServerPlayer sp && menu(sp, p.containerId()) instanceof TerminalMenu m) m.handleAction(sp, p.resource(), p.action());
        });
        registrar.playToServer(CraftRequest.TYPE, CraftRequest.STREAM_CODEC, (p, c) -> {
            if (c.player() instanceof ServerPlayer sp && menu(sp, p.containerId()) instanceof TerminalMenu m) m.handleCraft(sp, p.resource(), p.amount(), p.start());
        });
        registrar.playToServer(Transfer.TYPE, Transfer.STREAM_CODEC, (p, c) -> {
            if (c.player() instanceof ServerPlayer sp && menu(sp, p.containerId()) instanceof TerminalMenu m) m.handleTransfer(sp, p);
        });
        registrar.playToServer(JobControl.TYPE, JobControl.STREAM_CODEC, (p, c) -> {
            if (c.player() instanceof ServerPlayer sp && menu(sp, p.containerId()) instanceof TerminalMenu m) m.handleJobs(sp, p.op(), p.cpu(), p.index());
        });
    }

    private GridPayloads() {}
}
