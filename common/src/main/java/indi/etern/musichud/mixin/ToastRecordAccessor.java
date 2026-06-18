package indi.etern.musichud.mixin;

import icyllis.modernui.widget.Toast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "icyllis.modernui.widget.ToastManager$ToastRecord", remap = false)
public interface ToastRecordAccessor {

    @Accessor("mToken")
    Toast getToken();
}
