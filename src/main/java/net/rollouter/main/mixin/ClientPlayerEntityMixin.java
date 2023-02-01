package net.rollouter.main.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import net.rollouter.main.Rollouter;
import net.rollouter.main.util.UnrollTimer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin implements UnrollTimer {
    @Unique
    private long ticks;

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        if (--this.ticks == 0) {
            Rollouter.unrollNextBrush();
        }
    }

    @Override
    public void unroll_setTimer(int ticks) {
        this.ticks = ticks;
    }
}
