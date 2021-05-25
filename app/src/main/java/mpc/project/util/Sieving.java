package mpc.project.util;

import java.math.BigInteger;
import java.util.Random;

public class Sieving {
    final static long[] sievedPrimesLong = {
            2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 43, 47
    };
    final static BigInteger[] sievedPrimes = MathUtility.toBigIntegerArray(sievedPrimesLong);
    final static BigInteger M = MathUtility.arrayProduct(sievedPrimes);
    final static int probeBucketSize = 31;

    static public BigInteger generateSievedA(Random rnd) {
        BigInteger a = null;
        boolean foundGoodCandidate = false;
        do {
            BigInteger r = MathUtility.genRandBig(M, rnd);
            for (int i = 0; i < probeBucketSize; i++) {
                BigInteger target = r.add(BigInteger.valueOf((long) i));
                boolean noKnownFactor = true;
                for (BigInteger prime : sievedPrimes) {
                    if (target.remainder(prime).equals(BigInteger.ZERO)) {
                        noKnownFactor = false;
                        break;
                    }
                }
                if (noKnownFactor) {
                    foundGoodCandidate = true;
                    a = target;
                    break;
                }
            }
        } while (!foundGoodCandidate);

        return a;
    }
}
