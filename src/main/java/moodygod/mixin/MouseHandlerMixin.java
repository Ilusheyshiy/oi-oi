package moodygod.mixin;

import moodygod.client.GodHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Во время сцен игрок физически не может повернуть камеру. Если сигнатура метода в версии другая — миксин тихо пропускается (require = 0). */
@Mixin(targets = "net.minecraft.client.MouseHandler")
public class MouseHandlerMixin {
    @Inject(method = "turnPlayer(D)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void moodygod$lockCamera(double d, CallbackInfo ci) {
        if (GodHud.camLocked) ci.cancel();
    }
}
