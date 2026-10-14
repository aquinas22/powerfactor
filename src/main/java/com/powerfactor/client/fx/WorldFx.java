package com.powerfactor.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerfactor.PowerFactor;
import com.powerfactor.weapon.FxPayload;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Quaternionf;

/**
 * Short-lived world effects sent by the server ({@link FxPayload}): railgun ion trails, shockwaves,
 * dash afterimages and collapses, plus the local player's recoil and camera shake.
 */
@EventBusSubscriber(modid = PowerFactor.MODID, value = Dist.CLIENT)
public final class WorldFx {
    private record Effect(FxPayload p, int life, int[] age) {}

    private static final List<Effect> EFFECTS = new ArrayList<>();
    private static float shake, shakeSeed;

    public static void receive(FxPayload p) {
        Minecraft mc = Minecraft.getInstance();
        int life = switch (p.kind()) {
            case FxPayload.RAIL -> 60;
            case FxPayload.SHOCKWAVE -> 30;
            case FxPayload.DASH -> 16;
            case FxPayload.COLLAPSE -> 30;
            case FxPayload.PLASMA_BURST -> 8;
            default -> 0;
        };
        if (life > 0) EFFECTS.add(new Effect(p, life, new int[] {0}));
        if (mc.player != null) {
            if (p.shooter() == mc.player.getId()) {
                // Recoil kicks the muzzle up; the heavier the shot, the harder.
                float kick = p.kind() == FxPayload.RAIL ? 4 + 5 * p.scale() : p.kind() == FxPayload.BOLT ? 1.2F * p.scale() : 0;
                mc.player.setXRot(Mth.clamp(mc.player.getXRot() - kick, -90, 90));
                shake = Math.max(shake, p.kind() == FxPayload.RAIL ? 0.6F * p.scale() : 0.25F * p.scale());
            }
            if (p.kind() == FxPayload.COLLAPSE) {
                double d = mc.player.position().distanceTo(p.from());
                shake = Math.max(shake, (float) Math.max(0, 2.2 - d / 18));
            } else if (p.kind() == FxPayload.SHOCKWAVE) {
                double d = mc.player.position().distanceTo(p.from());
                shake = Math.max(shake, (float) Math.max(0, 0.6 * p.scale() - d / 24));
            }
        }
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        for (Iterator<Effect> it = EFFECTS.iterator(); it.hasNext(); ) {
            Effect e = it.next();
            if (++e.age()[0] > e.life()) it.remove();
        }
        shake *= 0.82F;
        if (shake < 0.01F) shake = 0;
        shakeSeed += 1.7F;
    }

    @SubscribeEvent
    static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (shake <= 0) return;
        float s = shake * 3.5F;
        event.setPitch(event.getPitch() + Mth.sin(shakeSeed * 1.9F) * s);
        event.setYaw(event.getYaw() + Mth.cos(shakeSeed * 2.3F) * s);
        event.setRoll(event.getRoll() + Mth.sin(shakeSeed * 3.1F) * s * 0.6F);
    }

    @SubscribeEvent
    static void onSubmit(SubmitCustomGeometryEvent event) {
        if (EFFECTS.isEmpty()) return;
        var cam = event.getLevelRenderState().cameraRenderState;
        Vec3 camPos = cam.pos;
        Quaternionf orientation = cam.orientation;
        SubmitNodeCollector c = event.getSubmitNodeCollector();
        PoseStack pose = event.getPoseStack();
        float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        for (Effect e : List.copyOf(EFFECTS)) {
            FxPayload p = e.p();
            float age = e.age()[0] + partial, f = Mth.clamp(age / e.life(), 0, 1), fade = 1 - f;
            pose.pushPose();
            pose.translate(-camPos.x, -camPos.y, -camPos.z);
            switch (p.kind()) {
                case FxPayload.RAIL -> {
                    // A white-hot core that thins fast, inside an ionised sheath that lingers and fades blue.
                    // The slug's white-hot core lasts a moment; the ionised sheath lingers, widens and fades.
                    float core = Math.max(0, 1 - f * 4);
                    float sheath = (float) Math.sqrt(fade);
                    Fx.beam(c, pose, p.from(), p.to(), 0.07F * p.scale() * (0.4F + core), -age * 0.5F, Fx.alpha(0xFFFFFFFF, 0.3F + 0.7F * core),
                            Fx.alpha(p.color(), 0.85F * sheath));
                    Fx.beam(c, pose, p.from(), p.to(), 0.18F * p.scale() * (1 + 1.5F * f), age * 0.1F, Fx.alpha(p.color(), 0.6F * sheath), 0);
                    // Spiral rings of ionised air along the path.
                    Vec3 d = p.to().subtract(p.from());
                    double len = d.length();
                    Vec3 n = d.scale(1 / Math.max(1e-4, len));
                    float yaw = (float) Math.toDegrees(Math.atan2(n.x, n.z)), pitch = (float) Math.toDegrees(Math.asin(-n.y));
                    for (double s = 1.5; s < Math.min(len, 60); s += 2.5) {
                        Vec3 at = p.from().add(n.scale(s));
                        pose.pushPose();
                        pose.translate(at.x, at.y, at.z);
                        pose.mulPose(Axis.YP.rotationDegrees(yaw));
                        pose.mulPose(Axis.XP.rotationDegrees(pitch + 90));
                        Fx.ring(c, pose, (0.12F + 0.5F * f) * p.scale(), 0.05F, 16, (float) s * 0.3F, Fx.alpha(p.color(), 0.8F * sheath));
                        pose.popPose();
                    }
                    pose.pushPose();
                    pose.translate(p.from().x, p.from().y, p.from().z);
                    Fx.glow(c, pose, orientation, 0.9F * core + 0.1F, Fx.alpha(0xFFFFFFFF, core));
                    pose.popPose();
                }
                case FxPayload.SHOCKWAVE -> {
                    pose.translate(p.from().x, p.from().y, p.from().z);
                    Fx.ring(c, pose, 0.3F + p.scale() * 2.4F * f, 0.25F * fade + 0.05F, 48, age * 0.1F, Fx.alpha(p.color(), fade));
                    pose.mulPose(Axis.XP.rotationDegrees(90));
                    Fx.ring(c, pose, 0.2F + p.scale() * 1.6F * f, 0.18F * fade + 0.04F, 48, -age * 0.1F, Fx.alpha(p.color(), 0.7F * fade));
                    Fx.glow(c, pose, orientation, 1.5F * p.scale() * fade, Fx.alpha(0xFFFFFFFF, fade));
                }
                case FxPayload.DASH -> {
                    Fx.beam(c, pose, p.from(), p.to(), 0.5F * fade, age * 0.3F, Fx.alpha(0xFFFFFFFF, 0.5F * fade), Fx.alpha(p.color(), 0.6F * fade));
                    for (int k = 0; k <= 4; k++) {
                        Vec3 at = p.from().lerp(p.to(), k / 4.0);
                        pose.pushPose();
                        pose.translate(at.x, at.y, at.z);
                        Fx.glow(c, pose, orientation, 1.4F * fade, Fx.alpha(p.color(), 0.6F * fade));
                        pose.popPose();
                    }
                }
                case FxPayload.COLLAPSE -> {
                    pose.translate(p.from().x, p.from().y, p.from().z);
                    float r = 1 + p.scale() * 3 * (float) Math.sqrt(f);
                    for (int k = 0; k < 3; k++) {
                        pose.pushPose();
                        pose.mulPose(Axis.XP.rotationDegrees(60 * k));
                        pose.mulPose(Axis.YP.rotationDegrees(age * 3));
                        Fx.ring(c, pose, r * (1 - k * 0.12F), 0.5F * fade, 64, age * 0.05F, Fx.alpha(k == 1 ? 0xFFFFB347 : p.color(), fade));
                        pose.popPose();
                    }
                    Fx.glow(c, pose, orientation, r * 2.4F, Fx.alpha(p.color(), 0.5F * fade));
                }
                case FxPayload.PLASMA_BURST -> {
                    pose.translate(p.from().x, p.from().y, p.from().z);
                    Fx.glow(c, pose, orientation, 2.2F * (0.4F + f), Fx.alpha(p.color(), fade));
                    Fx.ring(c, pose, 0.3F + 1.3F * f, 0.1F * fade, 24, 0, Fx.alpha(p.color(), fade));
                }
                default -> {}
            }
            pose.popPose();
        }
    }

    private WorldFx() {}
}
