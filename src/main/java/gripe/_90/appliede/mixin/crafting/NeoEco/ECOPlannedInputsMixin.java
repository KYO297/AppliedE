//package gripe._90.appliede.mixin.crafting.NeoEco;
//
//
//import appeng.api.crafting.IPatternDetails;
//import appeng.api.stacks.AEItemKey;
//import appeng.crafting.CraftingPlan;
//import cn.dancingsnow.neoecoae.impl.crafting.planner.ae2.ECOAE2InputSelection;
//import cn.dancingsnow.neoecoae.impl.crafting.planner.ae2.ECOAE2PatternVariant;
//import cn.dancingsnow.neoecoae.impl.crafting.planner.ae2.ECOPlannedInputs;
//import cn.dancingsnow.neoecoae.impl.crafting.planner.schedule.ECOScheduledStep;
//import com.mojang.logging.LogUtils;
//import gripe._90.appliede.me.misc.TransmutationPattern;
//import org.slf4j.Logger;
//import org.spongepowered.asm.mixin.Mixin;
//import org.spongepowered.asm.mixin.Unique;
//import org.spongepowered.asm.mixin.injection.At;
//import org.spongepowered.asm.mixin.injection.Inject;
//import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
//
//import java.util.ArrayDeque;
//import java.util.Arrays;
//import java.util.List;
//import java.util.Map;
//
//import static org.spongepowered.asm.mixin.injection.At.Shift.AFTER;
//
//
//@Mixin(value = ECOPlannedInputs.class, remap = false)
//public class ECOPlannedInputsMixin {
//    @Unique
//    private static final Logger appliedE$LOGGER = LogUtils.getLogger();
//
//
//    @Inject(
//            method = "register",
//            at = @At(
//                    value = "INVOKE",
//                    target = "Lcn/dancingsnow/neoecoae/impl/crafting/planner/ae2/ECOPlannedInputs;removeStalePlans()V",
//                    remap = false,
//                    shift = AFTER
//            ),
//            locals = LocalCapture.CAPTURE_FAILHARD
//    )
//    private static void consolidateTransmutationBatches(
//            CraftingPlan plan,
//            List<ECOScheduledStep<ECOAE2PatternVariant>> steps,
//            CallbackInfo ci,
//            Map<IPatternDetails, ArrayDeque<ECOPlannedInputs.PlannedInputBatch>> selections) {
//        plan.patternTimes().forEach((p, n) -> appliedE$LOGGER.info("patternTimes: {} -> {}", p, n));
//
//        List<IPatternDetails> transmutationPatterns = selections.keySet().stream()
//                .filter(p -> p instanceof TransmutationPattern && p.getPrimaryOutput().what() instanceof AEItemKey)
//                .toList();
//
//        for (IPatternDetails pattern : transmutationPatterns) {
//
//            var item = (AEItemKey) pattern.getPrimaryOutput().what();
//            var batches = selections.remove(pattern);
//
//            long total = batches.stream().mapToLong(ECOPlannedInputs.PlannedInputBatch::remaining).sum();
//
//            IPatternDetails newPattern = new TransmutationPattern(item, total);
//
//            List<ECOAE2InputSelection> newSelectedInputs = Arrays.stream(newPattern.getInputs())
//                    .flatMap(input -> Arrays.stream(input.getPossibleInputs()))
//                    .map(stack -> ECOAE2InputSelection.single(stack, 1))
//                    .toList();
//
//            selections.put(newPattern, new ArrayDeque<>(List.of(
//                    new ECOPlannedInputs.PlannedInputBatch(newSelectedInputs, 1))));
//        }
//    }
//}
