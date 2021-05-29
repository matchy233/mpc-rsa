package mpc.project.util;

import java.math.BigInteger;
import java.util.Random;

public class MathUtility {
    static public BigInteger arraySum(BigInteger[] array) {
        BigInteger result = BigInteger.valueOf(0);
        for (BigInteger element : array) {
            result = result.add(element);
        }
        return result;
    }

    static public double[] computeValuesOfLagrangianPolynomialsAtZero(int len) {
        double[] results = new double[len];

        for (int i = 0; i < len; i++) {
            int xi = i + 1;
            int numerator = 1;
            int denominator = 1;
            for (int j = 0; j < i; j++) {
                numerator *= -(j + 1);
                denominator *= (xi - (j + 1));
            }
            for (int j = i + 1; j < len; j++) {
                numerator *= -(j + 1);
                denominator *= (xi - (j + 1));
            }
            results[i] = (double) numerator / (double) denominator;
        }

        return results;
    }

    static public BigInteger genRandPrimeBig(int bitNum, BigInteger lessThanThis, Random rnd) {
        BigInteger result;
        do {
            result = BigInteger.probablePrime(bitNum, rnd);
        } while (result.compareTo(lessThanThis) >= 0);
        return result;
    }

    static public BigInteger genRandBig(int bitLength, Random rnd) {
        return new BigInteger(bitLength, rnd);
    }

    static public BigInteger genRandBig(BigInteger lessThanThis, Random rnd) {
        int len = lessThanThis.bitLength();
        BigInteger result = new BigInteger(len, rnd);
        if (result.compareTo(BigInteger.ONE) < 0) {
            result = result.add(BigInteger.ONE);
        }
        if (result.compareTo(lessThanThis.subtract(BigInteger.ONE)) >= 0) {
            result = result.mod(lessThanThis).add(BigInteger.ONE);
        }
        return result;
    }

    static public BigInteger polynomialResult(BigInteger[] poly, BigInteger input) {
        BigInteger result = BigInteger.valueOf(0);
        for (int i = 0; i < poly.length; i++) {
            // Computes a_i \times x^i
            result = result.add(poly[i].multiply(input.pow(i)));
        }
        return result;
    }

    static public BigInteger[] generateRandomSumArray(BigInteger sum, int size, Random rnd) {
        BigInteger[] result = new BigInteger[size];
        int bigLength = sum.bitLength();
        for (int i = 0; i < size - 1; i++) {
            BigInteger num = genRandBig(bigLength, rnd);
            if (rnd.nextBoolean()) {
                num = num.negate();
            }
            result[i] = num;
        }
        result[size - 1] = BigInteger.ZERO;
        result[size - 1] = sum.subtract(arraySum(result));
        return result;
    }

    static public BigInteger arrayProduct(BigInteger[] array) {
        BigInteger result = BigInteger.ONE;
        for (BigInteger element : array) {
            result = result.multiply(element);
        }
        return result;
    }

    static public BigInteger[] toBigIntegerArray(long[] array) {
        BigInteger[] result = new BigInteger[array.length];
        for (int i = 0; i < array.length; i++) {
            result[i] = BigInteger.valueOf(array[i]);
        }
        return result;
    }
}
