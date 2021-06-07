package mpc.project.util;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Random;

public class Sieve {
    private BigInteger fixValue = BigInteger.ONE;
    private BigInteger M = BigInteger.ONE;
    private BigInteger[] primeTable;
    private int clusterSize = -1;
    private int bitNum = -1;

    public BigInteger getM() {
        return M;
    }

    private void updateSieveConfig(int clusterSize, int bitNum){
        if(this.clusterSize == clusterSize && this.bitNum == bitNum){
            return;
        }
        this.clusterSize = clusterSize;
        this.bitNum = bitNum;
        BigInteger sievingBound = BigInteger.TWO.pow(bitNum - 1);
        int index = Arrays.binarySearch(primeTable, BigInteger.valueOf(clusterSize));

        if (index < 0) { // clusterSize is in the prime table
            index = -(index + 1);
        } else {
            index++; // clusterSize is in the prime table
        }

        this.fixValue = MathUtility.arrayProduct(Arrays.copyOfRange(primeTable, 0, index));

        boolean needToCreateNewPrimeTable = true;

        while (true) {
            for (BigInteger i : Arrays.copyOfRange(primeTable, index, primeTable.length)) {
                BigInteger temp = this.M.multiply(i);
                if (temp.compareTo(sievingBound) > 0) {
                    needToCreateNewPrimeTable = false;
                    break;
                } else {
                    this.M = temp;
                }
            }
            if (!needToCreateNewPrimeTable) {
                break;
            }
            primeTable = MathUtility.generatePrimeNumberTable(
                    primeTable[primeTable.length - 1].add(BigInteger.valueOf(100)),
                    primeTable);
        }
    }

    public BigInteger getRandomFactor(Random rnd) {
        return MathUtility.genRandBig(BigInteger.TWO.pow(bitNum).divide(M), rnd);
    }

    public Sieve() {
        // Todo: Implement distributed sieving
        this.primeTable = MathUtility.generatePrimeNumberTable(BigInteger.valueOf(150000));

    }

    public BigInteger generateSievedNumber(int clusterSize, int bitNum, Random rnd) {
        updateSieveConfig(clusterSize, bitNum);
        BigInteger a = null;
        boolean foundGoodCandidate = false;
        do {
            BigInteger r = MathUtility.genRandBig(M, rnd);
            for (int i = 0; i < 31; i++) {
                BigInteger target = r.add(BigInteger.valueOf(i));
                if (target.gcd(M).equals(BigInteger.ONE) &&
                        target.gcd(fixValue).equals(BigInteger.ONE)) {
                    foundGoodCandidate = true;
                    a = target;
                    break;
                }
            }
        } while (!foundGoodCandidate);

        return a;
    }
}
