package com.powerfactor.scada;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * One row on a SCADA terminal. FE and EE are per tick. For loads, positive EE is drawn and negative is
 * supplied (capacitors); for generators both are output.
 *
 * <p>Devices that offer more publish telemetry {@link Point}s (gauges with a range and an alarm state)
 * and {@link Control}s (buttons, toggles and setpoints) that the terminal's detail view shows and drives.
 */
public record ScadaReading(BlockPos pos, Component name, Status status, int fe, int ee, Component detail, boolean controllable, boolean enabled,
        boolean generator, float volts, float hertz, List<Point> points, List<Control> controls) {
    public enum Status {
        OFF, RUNNING, WARNING, ALARM;

        public static final StreamCodec<ByteBuf, Status> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], Enum::ordinal);
    }

    /** A gauge: value against lo..hi, in a unit; state 0 normal, 1 warning, 2 alarm. */
    public record Point(Component label, float value, String unit, float lo, float hi, int state) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Point> STREAM_CODEC = StreamCodec.of((buf, p) -> {
            ComponentSerialization.STREAM_CODEC.encode(buf, p.label);
            buf.writeFloat(p.value);
            buf.writeUtf(p.unit, 16);
            buf.writeFloat(p.lo);
            buf.writeFloat(p.hi);
            buf.writeVarInt(p.state);
        }, buf -> new Point(ComponentSerialization.STREAM_CODEC.decode(buf), buf.readFloat(), buf.readUtf(16), buf.readFloat(), buf.readFloat(), buf.readVarInt()));
    }

    public static final int BUTTON = 0, TOGGLE = 1, SETPOINT = 2;

    /** A control: kind BUTTON (value ignored), TOGGLE (value 0/1) or SETPOINT (value in min..max by step). Danger controls need arming. */
    public record Control(String id, Component label, int kind, float value, float min, float max, float step, boolean danger) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Control> STREAM_CODEC = StreamCodec.of((buf, c) -> {
            buf.writeUtf(c.id, 32);
            ComponentSerialization.STREAM_CODEC.encode(buf, c.label);
            buf.writeVarInt(c.kind);
            buf.writeFloat(c.value);
            buf.writeFloat(c.min);
            buf.writeFloat(c.max);
            buf.writeFloat(c.step);
            buf.writeBoolean(c.danger);
        }, buf -> new Control(buf.readUtf(32), ComponentSerialization.STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readBoolean()));

        public static Control button(String id, Component label, boolean danger) {
            return new Control(id, label, BUTTON, 0, 0, 0, 0, danger);
        }

        public static Control toggle(String id, Component label, boolean on) {
            return new Control(id, label, TOGGLE, on ? 1 : 0, 0, 1, 1, false);
        }

        public static Control setpoint(String id, Component label, float value, float min, float max, float step) {
            return new Control(id, label, SETPOINT, value, min, max, step, false);
        }
    }

    /** A plain reading: no telemetry or controls beyond on/off. */
    public ScadaReading(BlockPos pos, Component name, Status status, int fe, int ee, Component detail, boolean controllable, boolean enabled,
            boolean generator, float volts, float hertz) {
        this(pos, name, status, fe, ee, detail, controllable, enabled, generator, volts, hertz, List.of(), List.of());
    }

    /** This reading with telemetry and controls added. */
    public ScadaReading with(List<Point> points, List<Control> controls) {
        return new ScadaReading(pos, name, status, fe, ee, detail, controllable, enabled, generator, volts, hertz, points, controls);
    }

    public boolean detailed() {
        return !points.isEmpty() || !controls.isEmpty();
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, ScadaReading> STREAM_CODEC = StreamCodec.of(
            (buf, r) -> {
                BlockPos.STREAM_CODEC.encode(buf, r.pos);
                ComponentSerialization.STREAM_CODEC.encode(buf, r.name);
                Status.STREAM_CODEC.encode(buf, r.status);
                buf.writeVarInt(r.fe);
                buf.writeVarInt(r.ee);
                ComponentSerialization.STREAM_CODEC.encode(buf, r.detail);
                buf.writeBoolean(r.controllable);
                buf.writeBoolean(r.enabled);
                buf.writeBoolean(r.generator);
                buf.writeFloat(r.volts);
                buf.writeFloat(r.hertz);
                buf.writeVarInt(r.points.size());
                for (Point p : r.points) Point.STREAM_CODEC.encode(buf, p);
                buf.writeVarInt(r.controls.size());
                for (Control c : r.controls) Control.STREAM_CODEC.encode(buf, c);
            },
            buf -> {
                BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                Component name = ComponentSerialization.STREAM_CODEC.decode(buf);
                Status status = Status.STREAM_CODEC.decode(buf);
                int fe = buf.readVarInt(), ee = buf.readVarInt();
                Component detail = ComponentSerialization.STREAM_CODEC.decode(buf);
                boolean controllable = buf.readBoolean(), enabled = buf.readBoolean(), generator = buf.readBoolean();
                float volts = buf.readFloat(), hertz = buf.readFloat();
                int np = Math.min(buf.readVarInt(), 32);
                List<Point> points = new ArrayList<>(np);
                for (int i = 0; i < np; i++) points.add(Point.STREAM_CODEC.decode(buf));
                int nc = Math.min(buf.readVarInt(), 32);
                List<Control> controls = new ArrayList<>(nc);
                for (int i = 0; i < nc; i++) controls.add(Control.STREAM_CODEC.decode(buf));
                return new ScadaReading(pos, name, status, fe, ee, detail, controllable, enabled, generator, volts, hertz, points, controls);
            });
}
