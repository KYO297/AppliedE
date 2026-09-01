package gripe._90.appliede.mixin.crafting.AE2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import gripe._90.appliede.me.service.KnowledgeService;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Iterator;
import java.util.Map;

@Mixin(value = CraftingCpuLogic.class, remap = false)
public abstract class CraftingCPULogicMixin {
    @Shadow
    @Final
    CraftingCPUCluster cluster;

    @Inject(method = "trySubmitJob", at = @At("RETURN"))
    private void onJobStart(IGrid grid, ICraftingPlan plan, IActionSource src, ICraftingRequester requester, CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        if (cir.getReturnValue().successful() && grid != null) {
            for (var details : plan.patternTimes().keySet()) {
                KnowledgeService.addTemporaryPattern(details, cluster.getGrid());
            }
            KnowledgeService.updatePatterns(true, cluster.getGrid());
        }
    }

    @Inject(method = "finishJob", at = @At(value = "INVOKE", target = "Ljava/util/Map$Entry;getKey()Ljava/lang/Object;"), locals = LocalCapture.CAPTURE_FAILHARD)
    private void onJobCancel(boolean success, CallbackInfo ci, Iterator<?> it, Map.Entry<IPatternDetails, ?> entry) {
        KnowledgeService.addTemporaryPattern(entry.getKey(), cluster.getGrid());
    }
}
