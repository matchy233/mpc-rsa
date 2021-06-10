package mpc.project.util;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

public class MathUtility {
    static public BigInteger arraySum(BigInteger[] array){
        BigInteger result = BigInteger.valueOf(0);
        for(BigInteger element : array){
            result = result.add(element);
        }
        return result;
    }

    static public BigInteger arrayProduct(BigInteger[] array) {
        BigInteger result = BigInteger.ONE;
        for (BigInteger element : array) {
            result = result.multiply(element);
        }
        return result;
    }

    static public double computeTermOfLagrangianPolynomialAtZero(int xi, int len) {
        int i = xi - 1;
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
        return (double) numerator / (double) denominator;
    }

    static public double[] computeAllValuesOfLagrangianPolynomialAtZero(int len) {
        double[] results = new double[len];

        for (int i = 0; i < len; i++) {
            int xi = i + 1;
            results[i] = MathUtility.computeTermOfLagrangianPolynomialAtZero(xi, len);
        }

        return results;
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

    static public BigInteger[] genRandBigPolynomial(int size, BigInteger lessThanThis, Random rnd) {
        BigInteger[] result = new BigInteger[size];
        for (int i = 0; i < result.length; i++) {
            result[i] = genRandBig(lessThanThis, rnd);
        }
        return result;
    }

    static public BigInteger polynomialResult(BigInteger[] poly, BigInteger input, BigInteger randomPrime) {
        BigInteger result = BigInteger.valueOf(0);
        for (int i = 0; i < poly.length; i++) {
            // Computes a_i \times x^i
            result = result.add(
                    (poly[i].multiply(input.pow(i))).mod(randomPrime));
        }
        return result.mod(randomPrime);
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

    static public BigInteger computeSharingResult(BigInteger[] pArr, BigInteger[] qArr, BigInteger[] hArr, BigInteger modulo) {
        return (MathUtility.arraySum(pArr).mod(modulo)
                .multiply(MathUtility.arraySum(qArr).mod(modulo))).mod(modulo)
                .add(MathUtility.arraySum(hArr).mod(modulo))
                .mod(modulo);
    }

    static public BigInteger[] generatePrimeNumberTable(BigInteger upperBound) {
        ArrayList<BigInteger> primeTable = new ArrayList<>();
        primeTable.add(BigInteger.TWO);
        BigInteger primeTableProduct = BigInteger.valueOf(2);
        BigInteger nextPrime = BigInteger.valueOf(3);
        boolean reachUpperBound = false;
        while(!reachUpperBound){
            primeTable.add(nextPrime);
            primeTableProduct = primeTableProduct.multiply(nextPrime);
            do{
                nextPrime = nextPrime.add(BigInteger.TWO);
                if(nextPrime.compareTo(upperBound) >= 0){
                    reachUpperBound = true;
                    break;
                }
            }while(!nextPrime.gcd(primeTableProduct).equals(BigInteger.ONE));
        }
        return primeTable.toArray(new BigInteger[0]);
    }

    static public <T extends Number> BigInteger[] generatePrimeNumberTable(T upperBound, T[] primeBefore) {
        ArrayList<BigInteger> primeAfter = new ArrayList<>();
        BigInteger i = BigInteger.valueOf(3);
        while (i.compareTo(new BigInteger(String.valueOf(upperBound))) <= 0) {
            boolean isPrime = true;

            for (T j : primeBefore) {
                if (i.mod(new BigInteger(String.valueOf(j))).equals(BigInteger.ZERO)) {
                    isPrime = false;
                    break;
                }
            }

            if (!primeAfter.isEmpty()) {
                for (BigInteger j : primeAfter) {
                    if (i.mod(j).equals(BigInteger.ZERO)) {
                        isPrime = false;
                        break;
                    }
                }
            }

            if (isPrime) {
                primeAfter.add(i);
            }
            i = i.add(BigInteger.TWO);
        }
        return primeAfter.toArray(new BigInteger[0]);
    }

    static public BigInteger nextPrimeNumber(BigInteger[] primeTable){
        if(primeTable == null || primeTable.length < 2){
            primeTable = new BigInteger[]{BigInteger.TWO, BigInteger.valueOf(3)};
        }
        BigInteger primeTableProduct = arrayProduct(primeTable);
        BigInteger result = primeTable[primeTable.length-1];
        do {
            result = result.add(BigInteger.TWO);
        } while (!result.gcd(primeTableProduct).equals(BigInteger.ONE));
        return result;
    }

    static public BigInteger[] generateRandomArraySumToN(int size, BigInteger N, Random rnd) {
        BigInteger[] arr = new BigInteger[size];
        Arrays.fill(arr, BigInteger.ZERO);
        BigInteger remain = N;
        for(int i = 0; i < size-1; i++){
            // Todo: remove this check after fixing it
            if(remain.divide(BigInteger.TWO).signum() != 1){
                String errorMsg = "remain is not positive!\n";
                errorMsg += "remain: " + remain + "\n";
                errorMsg += "arr: \n{";
                for(BigInteger num : arr){
                    errorMsg += num + "\n";
                }
                errorMsg += "\n";
            }
            arr[i] = genRandBig(N.bitLength(), rnd).mod(remain.divide(BigInteger.TWO));
            remain = remain.subtract(arr[i]);
        }
        arr[size-1] = remain;
        return arr;
    }
}
