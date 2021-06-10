package mpc.project.util;

import org.junit.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.*;

public class MathTest {

    private final Random rnd = new Random(42);

    @Test
    public void testGenRandBig() {
        int bitNum = 32;
        Random rnd = new Random();
        BigInteger n = MathUtility.genRandBig(bitNum, rnd);
        System.out.println(n);
    }

    @Test
    public void testGeneratePrimeTable() {
        BigInteger[] primeTable = MathUtility.generatePrimeNumberTable(BigInteger.valueOf(100));
        for(BigInteger prime : primeTable){
            System.out.println(prime);
        }
    }

    @Test
    public void testArraySumToN() {
        BigInteger[] arr = MathUtility.generateRandomArraySumToN(10, BigInteger.valueOf(1000), new Random());
        System.out.println(Arrays.toString(arr));
        for (BigInteger bigInteger : arr) {
            assert bigInteger.signum() >= 0;
        }
        BigInteger sum = BigInteger.ZERO;
        for (BigInteger i : arr) {
            sum = sum.add(i);
        }
        assertEquals(sum, BigInteger.valueOf(1000));
    }

}
