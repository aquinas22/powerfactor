package com.powerfactor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerfactor.scada.DisplayWall;
import com.powerfactor.scada.ScadaDisplayBlockEntity;
import com.powerfactor.scada.ScadaPayloads;
import com.powerfactor.scada.ScadaReading;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws a whole SCADA display wall from its master panel. The page depends on the wall's size
 * (or the page the operator picked): a single device, big system numbers, trends, a device list,
 * or a full HMI with an auto-drawn one-line diagram whose power-flow dots move with the load.
 */
public class ScadaDisplayRenderer implements BlockEntityRenderer<ScadaDisplayBlockEntity, ScadaDisplayRenderer.State> {
    /** Canvas pixels per block. */
    private static final int PX = 64;
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int BG = 0xFF061008, FRAME = 0xFF1B2B23, TEXT = 0xFFB8F5C0, DIM = 0xFF5F8A66, AMBER = 0xFFFFB020;
    private static final int[] LAMP = {0xFF3A3F3C, 0xFF55FF55, 0xFFFFB020, 0xFFFF4040};

    // ------------------------------------------------------------------ client data cache

    private record Cached(List<ScadaReading> readings, ArrayDeque<double[]> history, long time) {}

    private static final Map<BlockPos, Cached> CACHE = new HashMap<>();

    public static void receive(ScadaPayloads.DisplaySnapshot snapshot) {
        Cached old = CACHE.get(snapshot.master());
        ArrayDeque<double[]> history = old == null ? new ArrayDeque<>() : old.history();
        double[] sys = system(snapshot.readings());
        history.addLast(sys);
        while (history.size() > 120) {
            history.removeFirst();
        }
        CACHE.put(snapshot.master(), new Cached(snapshot.readings(), history, System.currentTimeMillis()));
    }

    /** {hz, volts, genFe, genEe, loadFe, loadEe}. */
    private static double[] system(List<ScadaReading> readings) {
        double hz = 0, v = 0, gFe = 0, gEe = 0, lFe = 0, lEe = 0;
        int n = 0;
        for (ScadaReading r : readings) {
            if (r.generator()) {
                gFe += r.fe();
                gEe += r.ee();
                if (r.hertz() > 0) {
                    hz += r.hertz();
                    v += r.volts();
                    n++;
                }
            } else {
                lFe += r.fe();
                lEe += r.ee();
            }
        }
        if (n == 0) {
            hz = readings.isEmpty() ? 0 : 60;
            v = readings.isEmpty() ? 0 : readings.stream().mapToDouble(ScadaReading::volts).average().orElse(0);
            n = 1;
        }
        return new double[] {hz / n, v / n, gFe, gEe, lFe, lEe};
    }

    // ------------------------------------------------------------------ renderer

    public static class State extends BlockEntityRenderState {
        public boolean master;
        public Direction facing = Direction.NORTH;
        public int width, height;
        public ScadaDisplayBlockEntity.Page page = ScadaDisplayBlockEntity.Page.AUTO;
        public @Nullable Cached data;
        public float time;
    }

    private final Font font;

    public ScadaDisplayRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.font();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ScadaDisplayBlockEntity be, State state, float partialTicks, Vec3 camera,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, camera, breakProgress);
        DisplayWall wall = be.wall();
        state.master = wall != null && wall.master().equals(be.getBlockPos());
        if (!state.master) {
            return;
        }
        state.facing = wall.facing();
        state.width = wall.width();
        state.height = wall.height();
        state.page = be.page();
        state.data = CACHE.get(be.getBlockPos());
        state.time = (System.currentTimeMillis() % 1_000_000) / 1000F;
    }

    @Override
    public AABB getRenderBoundingBox(ScadaDisplayBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(DisplayWall.MAX);
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.master) {
            return;
        }
        Canvas c = new Canvas(state.width * PX, state.height * PX);
        paint(c, state);

        pose.pushPose();
        pose.translate(0.5F, 0.5F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(-state.facing.toYRot()));
        pose.translate(-0.5F, 0.5F, 0.502F);
        pose.scale(1F / PX, -1F / PX, 1F / PX);

        List<float[]> rects = c.rects;
        collector.submitCustomGeometry(pose, RenderTypes.textBackground(), (p, buf) -> {
            for (float[] r : rects) {
                int color = (int) r[4];
                float z = r[5];
                // Both windings, so the quad shows whatever the Y flip did to it.
                buf.addVertex(p, r[0], r[1], z).setColor(color).setLight(FULL_BRIGHT);
                buf.addVertex(p, r[0], r[3], z).setColor(color).setLight(FULL_BRIGHT);
                buf.addVertex(p, r[2], r[3], z).setColor(color).setLight(FULL_BRIGHT);
                buf.addVertex(p, r[2], r[1], z).setColor(color).setLight(FULL_BRIGHT);
                buf.addVertex(p, r[2], r[1], z).setColor(color).setLight(FULL_BRIGHT);
                buf.addVertex(p, r[2], r[3], z).setColor(color).setLight(FULL_BRIGHT);
                buf.addVertex(p, r[0], r[3], z).setColor(color).setLight(FULL_BRIGHT);
                buf.addVertex(p, r[0], r[1], z).setColor(color).setLight(FULL_BRIGHT);
            }
        });
        for (Canvas.Text t : c.texts) {
            pose.pushPose();
            pose.translate(t.x(), t.y(), 0.3F);
            pose.scale(t.scale(), t.scale(), 1F);
            collector.submitText(pose, 0, 0, Component.literal(t.text()).getVisualOrderText(), false, Font.DisplayMode.POLYGON_OFFSET,
                    FULL_BRIGHT, t.color(), 0, 0);
            pose.popPose();
        }
        pose.popPose();
    }

    // ------------------------------------------------------------------ painting

    /** Collects rectangles and text in wall pixels (64 per block, y down). */
    private final class Canvas {
        record Text(float x, float y, float scale, String text, int color) {}

        final int w, h;
        final List<float[]> rects = new ArrayList<>();
        final List<Text> texts = new ArrayList<>();

        Canvas(int w, int h) {
            this.w = w;
            this.h = h;
        }

        void fill(float x0, float y0, float x1, float y1, int color, float z) {
            rects.add(new float[] {x0, y0, x1, y1, color, z});
        }

        void fill(float x0, float y0, float x1, float y1, int color) {
            fill(x0, y0, x1, y1, color, 0.1F);
        }

        void text(float x, float y, String s, int color, float scale) {
            texts.add(new Text(x, y, scale, s, color));
        }

        void textRight(float right, float y, String s, int color, float scale) {
            text(right - font.width(s) * scale, y, s, color, scale);
        }

        void textCenter(float cx, float y, String s, int color, float scale) {
            text(cx - font.width(s) * scale / 2, y, s, color, scale);
        }

        String fit(String s, float width, float scale) {
            return font.plainSubstrByWidth(s, (int) (width / scale));
        }

        void line(float x0, float y0, float x1, float y1, float thick, int color) {
            if (x0 == x1) {
                fill(x0 - thick / 2, Math.min(y0, y1), x0 + thick / 2, Math.max(y0, y1), color, 0.15F);
            } else {
                fill(Math.min(x0, x1), y0 - thick / 2, Math.max(x0, x1), y0 + thick / 2, color, 0.15F);
            }
        }
    }

    private void paint(Canvas c, State s) {
        c.fill(0, 0, c.w, c.h, 0xFF2B3036, 0F);
        c.fill(1.5F, 1.5F, c.w - 1.5F, c.h - 1.5F, BG, 0.05F);
        for (float y = 3; y < c.h - 2; y += 2) {
            c.fill(2, y, c.w - 2, y + 0.5F, 0xFF081509, 0.06F);
        }
        Cached data = s.data;
        boolean live = data != null && System.currentTimeMillis() - data.time() < 3_000;
        if (!live) {
            c.textCenter(c.w / 2F, c.h / 2F - 5, "NO LINK", AMBER, s.width == 1 ? 0.8F : 1.2F);
            c.textCenter(c.w / 2F, c.h / 2F + 7, "connect fiber", DIM, 0.5F);
            return;
        }
        ScadaDisplayBlockEntity.Page page = s.page;
        if (page == ScadaDisplayBlockEntity.Page.AUTO) {
            int area = s.width * s.height;
            page = area == 1 ? ScadaDisplayBlockEntity.Page.DEVICE
                    : s.width >= 4 && s.height >= 3 ? ScadaDisplayBlockEntity.Page.DIAGRAM
                    : s.width >= 3 && s.height >= 2 ? ScadaDisplayBlockEntity.Page.LIST
                    : s.width >= 2 && s.height >= 2 ? ScadaDisplayBlockEntity.Page.TREND
                    : ScadaDisplayBlockEntity.Page.SUMMARY;
        }
        switch (page) {
            case DEVICE -> device(c, data, s.time, 0, 0, c.w, c.h);
            case SUMMARY -> summary(c, data, 0, 0, c.w, c.h);
            case TREND -> {
                summaryStrip(c, data, 0, 0, c.w, 20);
                trend(c, data, 4, 22, c.w - 8, c.h - 26);
            }
            case LIST -> {
                summaryStrip(c, data, 0, 0, c.w, 20);
                list(c, data, 4, 22, c.w - 8, c.h - 26);
            }
            case DIAGRAM -> {
                summaryStrip(c, data, 0, 0, c.w, 20);
                float split = c.w * 0.58F;
                diagram(c, data, s.time, 4, 22, split - 6, c.h - (s.height >= 4 ? 86 : 26));
                list(c, data, split, 22, c.w - split - 4, c.h - (s.height >= 4 ? 86 : 26));
                if (s.height >= 4) {
                    trend(c, data, 4, c.h - 62, c.w - 8, 58);
                }
            }
            default -> {
            }
        }
    }

    private void summaryStrip(Canvas c, Cached d, float x, float y, float w, float h) {
        double[] sys = system(d.readings());
        c.fill(x + 2, y + 2, x + w - 2, y + h, 0xFF0F2016);
        c.text(x + 5, y + 5, "SCADA", TEXT, 0.6F);
        String hz = String.format("%.2f Hz", sys[0]);
        String v = Math.round(sys[1] * 480) + " V";
        double ce = Math.hypot(sys[2], sys[3]);
        String pf = ce < 0.5 ? "PF ---" : String.format("PF %.2f", sys[2] / ce);
        c.text(x + 5, y + 12, hz, ElectricalPanel.freqColor(sys[0]), 0.5F);
        c.text(x + 5 + font.width(hz) * 0.5F + 6, y + 12, v, ElectricalPanel.voltColor(sys[1]), 0.5F);
        c.textRight(x + w - 5, y + 5, Math.round(sys[2]) + " FE/t", GuiUtil.FE_COLOR, 0.6F);
        c.textRight(x + w - 5, y + 12, pf, ce < 0.5 ? DIM : ElectricalPanel.pfColor(sys[2] / ce), 0.5F);
    }

    private void summary(Canvas c, Cached d, float x, float y, float w, float h) {
        double[] sys = system(d.readings());
        double ce = Math.hypot(sys[2], sys[3]);
        float big = Math.min(w, h * 2) / 64F;
        c.text(x + 5, y + 5, "GRID", DIM, 0.5F * big);
        c.text(x + 5, y + 14 * big, String.format("%.2f Hz", sys[0]), ElectricalPanel.freqColor(sys[0]), 1.2F * big);
        c.text(x + 5, y + 28 * big, Math.round(sys[1] * 480) + " V", ElectricalPanel.voltColor(sys[1]), 1.0F * big);
        c.textRight(x + w - 5, y + 14 * big, Math.round(sys[2]) + " FE", GuiUtil.FE_COLOR, 1.0F * big);
        c.textRight(x + w - 5, y + 28 * big, ce < 0.5 ? "PF ---" : String.format("PF %.2f", sys[2] / ce),
                ce < 0.5 ? DIM : ElectricalPanel.pfColor(sys[2] / ce), 1.0F * big);
    }

    private void device(Canvas c, Cached d, float time, float x, float y, float w, float h) {
        List<ScadaReading> all = d.readings();
        if (all.isEmpty()) {
            c.textCenter(x + w / 2, y + h / 2 - 3, "no devices", DIM, 0.6F);
            return;
        }
        ScadaReading r = all.get((int) (time / 3) % all.size());
        c.fill(x + 5, y + 5, x + 10, y + 10, LAMP[r.status().ordinal()]);
        c.text(x + 13, y + 5, c.fit(r.name().getString(), w - 18, 0.55F), TEXT, 0.55F);
        c.text(x + 6, y + 18, r.fe() + " FE", GuiUtil.FE_COLOR, 1.1F);
        c.text(x + 6, y + 32, r.ee() + " EE", GuiUtil.EE_COLOR, 0.8F);
        c.text(x + 6, y + 44, c.fit(r.detail().getString(), w - 10, 0.4F), DIM, 0.4F);
        c.textRight(x + w - 4, y + h - 8, ((int) (time / 3) % all.size() + 1) + "/" + all.size(), DIM, 0.4F);
    }

    private void list(Canvas c, Cached d, float x, float y, float w, float h) {
        float rowH = 13;
        int rows = (int) (h / rowH);
        List<ScadaReading> all = d.readings();
        for (int i = 0; i < Math.min(rows, all.size()); i++) {
            ScadaReading r = all.get(i);
            float ry = y + i * rowH;
            c.fill(x, ry + rowH - 0.5F, x + w, ry + rowH, FRAME);
            c.fill(x + 1, ry + 2, x + 5, ry + 6, LAMP[r.status().ordinal()]);
            c.text(x + 8, ry + 1.5F, c.fit(r.name().getString(), w * 0.5F, 0.45F), TEXT, 0.45F);
            c.textRight(x + w * 0.78F, ry + 1.5F, String.valueOf(r.fe()), GuiUtil.FE_COLOR, 0.45F);
            c.textRight(x + w - 2, ry + 1.5F, String.valueOf(r.ee()), GuiUtil.EE_COLOR, 0.45F);
            c.text(x + 8, ry + 7, c.fit(r.detail().getString(), w - 10, 0.33F), DIM, 0.33F);
        }
        if (all.size() > rows) {
            c.textRight(x + w - 2, y + h - 4, "+" + (all.size() - rows) + " more", DIM, 0.35F);
        }
    }

    private void trend(Canvas c, Cached d, float x, float y, float w, float h) {
        c.fill(x, y, x + w, y + h, 0xFF030805);
        for (int i = 1; i < 4; i++) {
            c.fill(x, y + h * i / 4, x + w, y + h * i / 4 + 0.4F, FRAME);
        }
        int n = d.history().size(), i = 0;
        float prevX = 0, prevHz = 0, prevV = 0;
        for (double[] s : d.history()) {
            float px = x + w - (n - 1 - i) * w / 119F;
            float hy = y + (float) Mth.clamp((61 - s[0]) / 4, 0, 1) * h;
            float vy = y + (float) Mth.clamp((1.2 - s[1]) / 0.4, 0, 1) * h;
            if (i > 0) {
                c.fill(Math.min(prevX, px), Math.min(prevV, vy) - 0.5F, Math.max(prevX, px) + 0.6F, Math.max(prevV, vy) + 0.5F, 0xFF55FF55, 0.2F);
                c.fill(Math.min(prevX, px), Math.min(prevHz, hy) - 0.5F, Math.max(prevX, px) + 0.6F, Math.max(prevHz, hy) + 0.5F, AMBER, 0.2F);
            }
            prevX = px;
            prevHz = hy;
            prevV = vy;
            i++;
        }
        c.text(x + 2, y + 2, "Hz", AMBER, 0.4F);
        c.text(x + 12, y + 2, "V", 0xFF55FF55, 0.4F);
        c.textRight(x + w - 2, y + h - 5, "60 s", DIM, 0.35F);
    }

    /** One-line diagram: generators above a bus bar, loads below, power-flow dots moving with the load. */
    private void diagram(Canvas c, Cached d, float time, float x, float y, float w, float h) {
        List<ScadaReading> gens = new ArrayList<>(), loads = new ArrayList<>();
        for (ScadaReading r : d.readings()) {
            (r.generator() ? gens : loads).add(r);
        }
        float busY = y + h * 0.42F;
        c.line(x + 4, busY, x + w - 4, busY, 2.2F, 0xFF8FA3B0);
        c.text(x + 4, busY + 2, "480 V BUS", DIM, 0.33F);
        place(c, gens, time, x, y + 4, w, busY, true);
        place(c, loads, time, x, busY, w, y + h - 4, false);
    }

    private void place(Canvas c, List<ScadaReading> items, float time, float x, float top, float w, float bottom, boolean generators) {
        if (items.isEmpty()) {
            return;
        }
        int n = Math.min(items.size(), 10);
        float slot = (w - 8) / n;
        for (int i = 0; i < n; i++) {
            ScadaReading r = items.get(i);
            float cx = x + 4 + slot * (i + 0.5F);
            float symY = generators ? top + 8 : bottom - 14;
            float lineFrom = generators ? symY + 7 : top;
            float lineTo = generators ? top + (bottom - top) : symY - 7;
            int color = LAMP[r.status().ordinal()];
            c.line(cx, lineFrom, cx, lineTo, 1.2F, 0xFF5B6B74);
            // Power flow: dots run from source to load, faster with more FE.
            if (r.fe() > 0) {
                float speed = Math.min(3, 0.4F + r.fe() / 60F);
                float len = Math.abs(lineTo - lineFrom);
                for (int k = 0; k < 3; k++) {
                    float t = ((time * speed + k / 3F) % 1F);
                    float dy = generators ? lineFrom + t * len : lineFrom + t * len;
                    c.fill(cx - 1, dy - 1, cx + 1, dy + 1, 0xFFFFE070, 0.25F);
                }
            }
            String symbol = generators ? "G" : r.ee() < 0 ? "C" : r.ee() > 0 ? "M" : r.controllable() ? "R" : "I";
            c.fill(cx - 7, symY - 7, cx + 7, symY + 7, color, 0.2F);
            c.fill(cx - 6, symY - 6, cx + 6, symY + 6, 0xFF0A140D, 0.22F);
            c.textCenter(cx, symY - 3, symbol, color, 0.6F);
            float labelY = generators ? symY - 14 : symY + 8;
            c.textCenter(cx, labelY, c.fit(r.name().getString(), slot - 2, 0.3F), TEXT, 0.3F);
            c.textCenter(cx, labelY + 4, r.fe() + " FE", GuiUtil.FE_COLOR, 0.3F);
        }
    }
}
