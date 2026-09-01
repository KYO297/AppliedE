package gripe._90.appliede.me.misc;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import gripe._90.appliede.AppliedE;
import gripe._90.appliede.me.key.EMCKey;
import moze_intel.projecte.api.proxy.IEMCProxy;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;

public final class TransmutationPattern implements IPatternDetails {
    private static final String NBT_ITEM = "item";
    private static final String NBT_AMOUNT = "amount";
    private static final String NBT_TIER = "tier";

    private final AEItemKey item;
    private final long amount;
    private final int tier;

    private final AEItemKey definition;

    public TransmutationPattern(AEItemKey item, long amount) {
        tier = 1;

        var tag = new CompoundTag();
        tag.put(NBT_ITEM, (this.item = item).toTag());
        tag.putLong(NBT_AMOUNT, this.amount = amount);
        definition = AEItemKey.of(AppliedE.DUMMY_EMC_ITEM.get(), tag);
    }

    public TransmutationPattern(int tier) {
        item = null;
        amount = 1;

        var tag = new CompoundTag();
        tag.putInt(NBT_TIER, this.tier = tier);
        definition = AEItemKey.of(AppliedE.DUMMY_EMC_ITEM.get(), tag);
    }

    private static long[] splitIntoTiers(BigInteger value) {
        final BigInteger base = AppliedE.TIER_LIMIT;

        if (value.compareTo(base) < 0) {
            return new long[]{value.longValue()};
        }

        BigInteger[] qr = value.divideAndRemainder(base);

        if (qr[0].compareTo(base) < 0) {
            return new long[]{qr[1].longValue(), qr[0].longValue()};
        }

        long[] result = new long[8];
        result[0] = qr[1].longValue();
        int size = 1;

        BigInteger current = qr[0];

        while (current.compareTo(base) >= 0) {
            qr = current.divideAndRemainder(base);

            if (size == result.length) {
                result = Arrays.copyOf(result, size * 2);
            }

            result[size++] = qr[1].longValue();
            current = qr[0];
        }

        if (size == result.length) {
            result = Arrays.copyOf(result, size + 1);
        }

        result[size++] = current.longValue();

        return size == result.length ? result : Arrays.copyOf(result, size);
    }

    @Override
    public AEItemKey getDefinition() {
        return definition;
    }

    @Override
    public IInput[] getInputs() {
        if (item == null) {
            return new IInput[]{new Input(1, tier)};
        }

        final long itemEmc = IEMCProxy.INSTANCE.getValue(item.toStack());
        final BigInteger totalEmc = BigInteger.valueOf(itemEmc).multiply(BigInteger.valueOf(amount));

        final long[] tierCounts = splitIntoTiers(totalEmc);
        final ArrayList<IInput> inputs = new ArrayList<>(tierCounts.length - 1);

        int tier = 1;
        for (long tierEMC : tierCounts) {
            if (tierEMC != 0) {
                inputs.add(new Input(tierEMC, tier));
            }
            tier++;
        }

        return inputs.toArray(new IInput[0]);
    }

    @Override
    public GenericStack[] getOutputs() {
        return new GenericStack[]{
                item != null
                        ? new GenericStack(item, amount)
                        : new GenericStack(EMCKey.tier(tier - 1), AppliedE.TIER_LIMIT.longValue())
        };
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof TransmutationPattern pattern && pattern.definition.equals(definition);
    }

    @Override
    public int hashCode() {
        return definition.hashCode();
    }

    private record Input(long amount, int tier) implements IInput {
        @Override
        public GenericStack[] getPossibleInputs() {
            return new GenericStack[]{new GenericStack(EMCKey.tier(tier), amount)};
        }

        @Override
        public long getMultiplier() {
            return 1;
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            return input.matches(getPossibleInputs()[0]);
        }

        @Override
        public AEKey getRemainingKey(AEKey template) {
            return null;
        }
    }
}
