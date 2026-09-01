package gripe._90.appliede.mixin.crafting.AE2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.me.service.CraftingService;
import gripe._90.appliede.me.misc.TransmutationPattern;
import gripe._90.appliede.me.service.KnowledgeService;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftingService.class, remap = false)
public class CraftingServiceMixin {

    @Final
    @Shadow
    private IGrid grid;

    @Inject(method = "getProviders", at = @At("RETURN"), cancellable = true)
    private void interceptTransmutationRequests(IPatternDetails key, CallbackInfoReturnable<Iterable<ICraftingProvider>> cir) {
        if (grid != null && key instanceof TransmutationPattern) {
            cir.setReturnValue(grid.getService(KnowledgeService.class).getModuleProviders());
        }
    }
}
