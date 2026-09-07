package gripe._90.appliede.mixin.crafting.NeoEco;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ae2.ECOAE2PatternVariant;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ae2.ECOAE2PlanAssembler;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ae2.ECOAE2PlanningSnapshot;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanCandidate;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solver.ECOHyperflowResult;
import gripe._90.appliede.me.key.EMCKey;
import gripe._90.appliede.me.misc.TransmutationPattern;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Mixin(value = ECOAE2PlanAssembler.class, remap = false)
public class ECOAE2PlanAssemblerMixin {

    @Unique
    private static final KeyCounter appliedE$newEMC = new KeyCounter();

    @Inject(method = "aggregatePatternExecutions",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;copyOf(Ljava/util/Map;)Ljava/util/Map;"),
            locals = LocalCapture.CAPTURE_FAILHARD)
    private static void aggregatePatternExecutions(ECOPlanCandidate<ECOAE2PatternVariant> candidate, CallbackInfoReturnable<Map<IPatternDetails, Long>> cir, Map<IPatternDetails, Long> result) {

        final List<IPatternDetails> transmutationPatterns = result.keySet().stream()
                .filter(pattern -> pattern instanceof TransmutationPattern && pattern.getPrimaryOutput().what() instanceof AEItemKey)
                .toList();

        if (transmutationPatterns.isEmpty()) return;

        appliedE$newEMC.clear();

        for (IPatternDetails pattern : transmutationPatterns) {
            final long count = result.remove(pattern);
            final AEItemKey item = (AEItemKey) pattern.getPrimaryOutput().what();
            final var newPattern = new TransmutationPattern(item, count);
            result.put(newPattern, 1L);

            Arrays.stream(newPattern.getInputs())
                    .flatMap(input -> Arrays.stream(input.getPossibleInputs()))
                    .forEach(EMCStack -> appliedE$newEMC.add(EMCStack.what(), EMCStack.amount()));
        }

        result.keySet().stream()
                .filter(pattern -> pattern instanceof TransmutationPattern && pattern.getPrimaryOutput().what() instanceof EMCKey)
                .toList()
                .forEach(result::remove);
    }

    @Inject(method = "assemble", at = @At("RETURN"))
    private static void swapEMCInputs(ECOAE2PlanningSnapshot snapshot, ECOHyperflowResult<ECOAE2PatternVariant> result, CallbackInfoReturnable<Optional<CraftingPlan>> cir) {
        if (cir.getReturnValue().isEmpty()) return;
        if (appliedE$newEMC.isEmpty()) return;
        CraftingPlan plan = cir.getReturnValue().get();
        KeyCounter inputs = plan.usedItems();
        KeyCounter toRemove = new KeyCounter();
        inputs.forEach((entry) -> {
            if (entry.getKey() instanceof EMCKey) {
                toRemove.add(entry.getKey(), entry.getLongValue());
            }
        });

        inputs.removeAll(toRemove);
        appliedE$newEMC.removeZeros();
        inputs.addAll(appliedE$newEMC);
        appliedE$newEMC.clear();
    }
}