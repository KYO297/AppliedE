package gripe._90.appliede.me.service;

import appeng.api.stacks.KeyCounter;
import gripe._90.appliede.AppliedE;
import gripe._90.appliede.me.key.EMCKey;

import java.math.BigInteger;
import java.util.Arrays;

public abstract class Utils {
    public static long[] splitIntoTiers(BigInteger value) {
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

    public static KeyCounter splitIntoKeys(BigInteger value) {
        return tiersToKeys(splitIntoTiers(value));
    }

    public static KeyCounter tiersToKeys(long[] tiers) {
        KeyCounter out = new KeyCounter();
        for (int i = 0; i < tiers.length; i++) {
            long val = tiers[i];
            if (val > 0) {
                out.add(EMCKey.tier(i + 1), val);
            }
        }
        return out;
    }
}
