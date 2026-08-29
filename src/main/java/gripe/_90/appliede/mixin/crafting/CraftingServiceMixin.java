package gripe._90.appliede.mixin.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.me.service.CraftingService;
import gripe._90.appliede.me.misc.TransmutationPattern;
import gripe._90.appliede.me.service.KnowledgeService;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = CraftingService.class, remap = false)
public class CraftingServiceMixin {

    @Final
    @Shadow
    private IGrid grid;


    @Inject(method = "submitJob", at = @At("RETURN"))
    private void onJobSubmitted(
            ICraftingPlan job,
            ICraftingRequester requestingMachine,
            ICraftingCPU target,
            boolean prioritizePower,
            IActionSource src,
            CallbackInfoReturnable<ICraftingSubmitResult> cir) {

        if (cir.getReturnValue().successful()) {
            List<TransmutationPattern> usedTransmutationPatterns = new ArrayList<>();
            for (IPatternDetails details : job.patternTimes().keySet()) {
                if (details instanceof TransmutationPattern pattern && pattern.getPrimaryOutput().amount() != 1) {
                    usedTransmutationPatterns.add(pattern);
                }
            }
            if (!usedTransmutationPatterns.isEmpty()) {
                grid.getService(KnowledgeService.class).trackCPU(target, usedTransmutationPatterns);
            }
        }
    }
}
