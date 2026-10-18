package com.powerfactor.pocketvoid;

import com.powerfactor.PowerFactor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Clicks on the Pocket Void's cells (client → server). */
public final class PocketVoidPayloads {
    public static final int TAKE_OR_PUT = 0, HALF_OR_ONE = 1, TO_INVENTORY = 2, LOCK = 3, SELECT = 4, MODE = 5;

    public record Click(int cell, int action) implements CustomPacketPayload {
        public static final Type<Click> TYPE = new Type<>(PowerFactor.id("pocket_void_click"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Click> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Click::cell, ByteBufCodecs.VAR_INT, Click::action, Click::new);

        @Override
        public Type<Click> type() {
            return TYPE;
        }
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(Click.TYPE, Click.STREAM_CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer player && player.containerMenu instanceof PocketVoidMenu menu && menu.valid()) {
                apply(player, menu, p.cell(), p.action());
            }
        });
    }

    static void apply(ServerPlayer player, PocketVoidMenu menu, int cell, int action) {
        ItemStack self = menu.voidStack();
        int cap = ((PocketVoidItem) self.getItem()).capacity();
        VoidContents c = PocketVoidItem.contents(self);
        ItemStack carried = menu.getCarried();
        if (action == MODE) {
            c = c.withMode(c.mode().next());
        } else if (cell >= 0 && cell < VoidContents.SLOTS) {
            var slot = c.slot(cell);
            switch (action) {
                case LOCK -> c = c.toggleLock(cell);
                case SELECT -> c = c.withSelected(cell);
                case TO_INVENTORY -> {
                    int max = slot.item().map(t -> t.create().getMaxStackSize()).orElse(64);
                    var t = c.take(cell, max);
                    ItemStack out = t.stack();
                    if (!out.isEmpty()) {
                        int before = out.getCount();
                        player.getInventory().add(out);
                        // Whatever didn't fit goes back in.
                        c = t.contents();
                        if (!out.isEmpty()) c = c.insert(out, cap, true, false).contents();
                        if (out.getCount() == before) c = PocketVoidItem.contents(self);
                    }
                }
                case TAKE_OR_PUT, HALF_OR_ONE -> {
                    boolean half = action == HALF_OR_ONE;
                    if (!carried.isEmpty()) {
                        ItemStack put = half ? carried.copyWithCount(1) : carried;
                        // Clicking a cell puts into that cell's kind, or starts it if the cell is empty.
                        var r = slot.item().isEmpty() || slot.holds(put) ? c.insert(put, cap, true, false) : new VoidContents.Inserted(c, 0);
                        if (r.taken() > 0) {
                            carried.shrink(r.taken());
                            c = r.contents();
                        }
                    } else if (slot.count() > 0) {
                        int max = slot.display().getMaxStackSize();
                        int n = half ? Math.max(1, Math.min(slot.count(), max) / 2) : max;
                        var t = c.take(cell, n);
                        menu.setCarried(t.stack());
                        c = t.contents();
                    }
                }
                default -> {}
            }
        }
        PocketVoidItem.set(self, c);
        menu.broadcastChanges();
    }

    private PocketVoidPayloads() {}
}
