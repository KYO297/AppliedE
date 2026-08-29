package gripe._90.appliede.mixin.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.me.service.CraftingService;
import com.mojang.logging.LogUtils;
import gripe._90.appliede.me.misc.TransmutationPattern;
import gripe._90.appliede.me.service.KnowledgeService;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;


@Mixin(value = CraftingService.class, remap = false)
public class CraftingServiceMixin {
    @Unique
    private static final Logger appliedE$LOGGER = LogUtils.getLogger();

    @Final
    @Shadow
    private IGrid grid;

    @Unique
    private ICraftingCPU appliede$CPU;
    @Unique
    private ICraftingPlan appliede$job;

    @Inject(method = "submitJob", at = @At("HEAD"))
    private void interceptCPU(
            ICraftingPlan job,
            ICraftingRequester requestingMachine,
            ICraftingCPU target,
            boolean prioritizePower,
            IActionSource src,
            CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        appliede$CPU = target;
        appliede$job = job;
    }


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
            for (IPatternDetails details : appliede$job.patternTimes().keySet()) {
                if (details instanceof TransmutationPattern pattern && pattern.getPrimaryOutput().amount() != 1) {
                    usedTransmutationPatterns.add(pattern);
                }
            }
            if (!usedTransmutationPatterns.isEmpty()) {
                if (appliede$CPU == null) {
                    appliedE$LOGGER.warn("Target null");
                } else {
                    appliedE$LOGGER.info("CPU tracked");
                    grid.getService(KnowledgeService.class).trackCPU(appliede$CPU, usedTransmutationPatterns);
                }

            }
        }
    }
}
