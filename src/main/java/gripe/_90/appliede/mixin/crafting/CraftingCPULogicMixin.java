package gripe._90.appliede.mixin.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import gripe._90.appliede.me.misc.TransmutationPattern;
import gripe._90.appliede.me.service.KnowledgeService;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
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

    /**
     * Remove temporary pattern after execution
     */
    @Inject(method = "executeCrafting",
            at = @At(value = "INVOKE", target = "Ljava/util/Iterator;remove()V"),
            locals = LocalCapture.CAPTURE_FAILHARD)
    private void removeOnFinishStep(
            int maxPatterns,
            CraftingService craftingService,
            IEnergyService energyService,
            Level level,
            CallbackInfoReturnable<Integer> cir,
            ExecutingCraftingJob job,
            int pushedPatterns,
            Iterator<?> it,
            Map.Entry<IPatternDetails, ?> task) {
        appliede$removeTemporaryPattern(task.getKey());
    }

    /**
     * Remove unused temporary patterns on job cancel
     */
    @Inject(method = "finishJob",
            at = @At(value = "INVOKE", target = "Ljava/util/Map$Entry;getKey()Ljava/lang/Object;"),
            locals = LocalCapture.CAPTURE_FAILHARD)
    private void removeOnCancel(
            boolean success,
            CallbackInfo ci,
            Iterator<?> it,
            Map.Entry<IPatternDetails, ?> entry) {

        appliede$removeTemporaryPattern(entry.getKey());
    }

    /**
     * Remount provider after job finish/cancel to update offered patterns
     */
    @Inject(method = "finishJob",
            at = @At("HEAD"))
    private void updateOnFinish(
            boolean success,
            CallbackInfo ci) {
        var grid = cluster.getGrid();
        if (grid != null) {
            var ks = grid.getService(KnowledgeService.class);
            if (ks != null) {
                ks.updatePatterns();
            }
        }
    }

    /**
     * Add all temporary patterns used in calculation to provider
     * <p>
     * Remount provider
     */
    @Inject(method = "trySubmitJob",
            at = @At(value = "INVOKE",
                    target = "Lappeng/me/cluster/implementations/CraftingCPUCluster;markDirty()V",
                    shift = At.Shift.AFTER))
    private void addOnJobStart(
            IGrid grid,
            ICraftingPlan plan,
            IActionSource src,
            ICraftingRequester requester,
            CallbackInfoReturnable<ICraftingSubmitResult> cir) {

        if (grid != null) {
            KnowledgeService ks = grid.getService(KnowledgeService.class);
            if (ks != null) {
                for (IPatternDetails pattern : plan.patternTimes().keySet()) {
                    if (pattern instanceof TransmutationPattern) {
                        ks.addTemporaryPattern(pattern);
                    }
                }
                ks.updatePatterns();
            }
        }
    }

    @Unique
    private void appliede$removeTemporaryPattern(IPatternDetails pattern) {
        if (pattern instanceof TransmutationPattern) {
            var grid = cluster.getGrid();

            if (grid != null) {
                grid.getService(KnowledgeService.class).removeTemporaryPattern(pattern);
            }
        }
    }
}
