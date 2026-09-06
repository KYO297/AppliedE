package gripe._90.appliede.mixin.crafting.NeoEco;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ae2.ECOAE2PatternVariant;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ae2.ECOAE2PlanAssembler;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanCandidate;
import com.mojang.logging.LogUtils;
import gripe._90.appliede.me.key.EMCKey;
import gripe._90.appliede.me.misc.TransmutationPattern;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Mixin(value = ECOAE2PlanAssembler.class, remap = false)
public class ECOAE2PlanAssemblerMixin {

    @Unique
    private static final Logger appliedE$LOGGER = LogUtils.getLogger();

    @Inject(method = "aggregatePatternExecutions",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;copyOf(Ljava/util/Map;)Ljava/util/Map;"),
            locals = LocalCapture.CAPTURE_FAILHARD)
    private static void aggregatePatternExecutions(ECOPlanCandidate<ECOAE2PatternVariant> candidate, CallbackInfoReturnable<Map<IPatternDetails, Long>> cir, Map<IPatternDetails, Long> result) {
        try {
            final List<IPatternDetails> transmutationPatterns = result.keySet().stream()
                    .filter(pattern -> pattern instanceof TransmutationPattern && pattern.getPrimaryOutput().what() instanceof AEItemKey)
                    .toList();


            final Map<EMCKey, Long> newEMC = new LinkedHashMap<>();

            for (IPatternDetails pattern : transmutationPatterns) {
                final long count = result.remove(pattern);
                final AEItemKey item = (AEItemKey) pattern.getPrimaryOutput().what();
                final var newPattern = new TransmutationPattern(item, count);
                result.put(newPattern, 1L);

                Arrays.stream(newPattern.getInputs())
                        .flatMap(input -> Arrays.stream(input.getPossibleInputs()))
                        .forEach(EMCStack -> newEMC.merge((EMCKey) EMCStack.what(), EMCStack.amount() * count, Long::sum));

            }


            final List<IPatternDetails> EMCPatterns = result.keySet().stream()
                    .filter(pattern -> pattern instanceof TransmutationPattern && pattern.getPrimaryOutput().what() instanceof EMCKey)
                    .toList();

            EMCPatterns.forEach(result::remove);
        } catch (Exception e) {
            appliedE$LOGGER.error("Error batching Transmutation Patterns", e);
        }
    }
}