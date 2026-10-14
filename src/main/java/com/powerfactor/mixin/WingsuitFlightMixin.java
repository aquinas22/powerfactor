package com.powerfactor.mixin;

import com.powerfactor.item.WingsuitItem;
import com.powerfactor.movement.GrappleHookEntity;
import com.powerfactor.movement.MovementPhysics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Replace only this mod's flight force calculation; vanilla handles pose, collision and damage. */
@Mixin(LivingEntity.class)
public abstract class WingsuitFlightMixin {
    @Inject(method = "updateFallFlyingMovement", at = @At("HEAD"), cancellable = true)
    private void powerfactor$flight(Vec3 velocity, CallbackInfoReturnable<Vec3> callback) {
        if ((Object) this instanceof Player player && WingsuitItem.worn(player.getItemBySlot(EquipmentSlot.CHEST))) {
            callback.setReturnValue(GrappleHookEntity.vec(MovementPhysics.glide(GrappleHookEntity.vector(velocity),
                    player.getYRot(), player.getXRot(), player.isShiftKeyDown())));
        }
    }
}
