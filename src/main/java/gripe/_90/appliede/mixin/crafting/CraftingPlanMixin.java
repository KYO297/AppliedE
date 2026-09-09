package gripe._90.appliede.mixin.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import gripe._90.appliede.me.key.EMCKey;
import gripe._90.appliede.me.misc.TransmutationPattern;
import gripe._90.appliede.me.service.Utils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static gripe._90.appliede.AppliedE.TIER_LIMIT;

@Mixin(value = CraftingPlan.class, remap = false)
public class CraftingPlanMixin {

    @Unique
    private static BigInteger appliedE$totalEMC(KeyCounter counter) {
        return counter
                .keySet()
                .stream()
                .filter(key -> key instanceof EMCKey)
                .map(key -> BigInteger.valueOf(counter.get(key)).multiply(TIER_LIMIT.pow(((EMCKey) key).getTier() - 1)))
                .reduce(BigInteger.ZERO, BigInteger::add);

    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void atPlanCreation(
            GenericStack finalOutput,
            long bytes,
            boolean simulation,
            boolean multiplePaths,
            KeyCounter usedItems,
            KeyCounter emittedItems,
            KeyCounter missingItems,
            Map<IPatternDetails, Long> patternTimes,
            CallbackInfo ci) {

        // Collect item transmutation patterns
        final List<IPatternDetails> oldTransmutationPatterns = patternTimes
                .keySet()
                .stream()
                .filter((IPatternDetails pattern) -> pattern instanceof TransmutationPattern
                        && pattern.getPrimaryOutput().what() instanceof AEItemKey)
                .toList();

        // No transmutation patterns - no work to do
        if (oldTransmutationPatterns.isEmpty()) return;

        // Sum total EMC that was attempted to be extracted
        BigInteger totalOldEMC = appliedE$totalEMC(usedItems);

        // Remove single item transmutation patterns and replace them with batched patterns
        final List<TransmutationPattern> newTransmutationPatterns = new ArrayList<>(oldTransmutationPatterns.size());
        oldTransmutationPatterns.forEach(pattern -> {
            final long count = patternTimes.remove(pattern);
            final var newPattern = new TransmutationPattern((AEItemKey) pattern.getPrimaryOutput().what(), count);
            patternTimes.put(newPattern, 1L);
            newTransmutationPatterns.add(newPattern);
        });

        // Calculate EMC costs of batched patterns
        final KeyCounter newEMC = new KeyCounter();
        newTransmutationPatterns.stream()
                .map(TransmutationPattern::getInputs)
                .flatMap(Arrays::stream)
                .map(IPatternDetails.IInput::getPossibleInputs)
                .flatMap(Arrays::stream)
                .forEach((GenericStack EMCStack) -> newEMC.add(EMCStack.what(), EMCStack.amount()));

        // Sum total EMC required
        BigInteger totalNewEMC = appliedE$totalEMC(newEMC);

        // Remove EMC tier downcrafting patterns
        patternTimes
                .keySet()
                .stream()
                .filter(pattern -> pattern instanceof TransmutationPattern
                        && pattern.getPrimaryOutput().what() instanceof EMCKey)
                .forEach(patternTimes::remove);

        // Remove unbatched EMC from used items
        usedItems
                .keySet()
                .stream()
                .filter(key -> key instanceof EMCKey)
                .forEach(key -> usedItems.remove(key, usedItems.get(key)));

        // Add batched EMC to be extracted
        usedItems.addAll(newEMC);
        usedItems.removeZeros();

        // total missing EMC
        BigInteger missingEMC = totalNewEMC.subtract(totalOldEMC);

        // none missing - nothing to change in missing items
        if (missingEMC.signum() <= 0) return;

        // remove unbatched EMC from missing items
        missingItems
                .keySet()
                .stream()
                .filter(key -> key instanceof EMCKey)
                .forEach(key -> missingItems.remove(key, missingItems.get(key)));

        // add batched EMC to missing items
        missingItems.addAll(Utils.splitIntoKeys(missingEMC));
        missingItems.removeZeros();
    }
}

