import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Random;

public class TempWorkerMain {
    private final Random rnd;
    private final int bitNum;
    private final int clusterSize;
    private final BigInteger P;
    private final int id;
    private final int[] workerUris;
    private BigInteger p;
    private BigInteger q;
    private BigInteger[] polyF;
    private BigInteger[] polyG;
    private BigInteger[] polyH;
    // modular
    private BigInteger N;

    public TempWorkerMain(int clusterSize, int id, BigInteger P, int bitNum, int[] workerUris) {
        this.clusterSize = clusterSize;
        this.id = id;
        this.P = P;
        this.bitNum = bitNum;
        this.rnd = new Random();
        this.workerUris = workerUris;
    }

    private BigInteger genRandBig(int bitNum, BigInteger lessThanThis, Random rnd) {
        BigInteger result;
        do {
            result = BigInteger.probablePrime(bitNum, rnd);
        } while (result.compareTo(lessThanThis) >= 0);
        return result;
    }

    private void generatePQ() {
        p = genRandBig(bitNum, P, rnd);
        q = genRandBig(bitNum, P, rnd);
//        generateFGH();
    }

    private BigInteger polynomialResult(BigInteger[] poly, BigInteger input) {
        BigInteger result = BigInteger.valueOf(0);
        for (int i = 0; i < poly.length; i++) {
            // Computes a_i \times x^i
            result = result.add(poly[i].multiply(input.pow(i)));
        }
        return result;
    }

    BigInteger[] pArr;
    BigInteger[] qArr;
    BigInteger[] hArr;

    private void generateFGH() {
        int l = (clusterSize - 1) / 2;
        polyF = new BigInteger[l];
        polyF[0] = p;
        for (int i = 1; i < polyF.length; i++) {
            polyF[i] = genRandBig(bitNum, P, rnd);
        }
        polyG = new BigInteger[l];
        polyG[0] = q;
        for (int i = 1; i < polyG.length; i++) {
            polyG[i] = genRandBig(bitNum, P, rnd);
        }
        polyH = new BigInteger[2 * l];
        polyH[0] = BigInteger.valueOf(0);
        for (int i = 1; i < polyH.length; i++) {
            polyH[i] = genRandBig(bitNum, P, rnd);
        }
        pArr = new BigInteger[clusterSize];
        BigInteger[] pArr_tmp = new BigInteger[clusterSize];
        for (int i = 0; i < clusterSize; i++) {
            pArr_tmp[i] = polynomialResult(polyF, BigInteger.valueOf(i + 1));
        }
        qArr = new BigInteger[clusterSize];
        BigInteger[] qArr_tmp = new BigInteger[clusterSize];
        for (int i = 0; i < clusterSize; i++) {
            qArr_tmp[i] = polynomialResult(polyG, BigInteger.valueOf(i + 1));
        }
        hArr = new BigInteger[clusterSize];
        BigInteger[] hArr_tmp = new BigInteger[clusterSize];
        for (int i = 0; i < clusterSize; i++) {
            hArr_tmp[i] = polynomialResult(polyH, BigInteger.valueOf(i + 1));
        }

        for (int i = 1; i < id; i++) {
            sendpqh(pArr_tmp[i - 1], qArr_tmp[i - 1], hArr_tmp[i - 1], workerUris[i - 1], id);
        }
        pArr[id - 1] = pArr_tmp[id - 1];
        qArr[id - 1] = qArr_tmp[id - 1];
        hArr[id - 1] = hArr_tmp[id - 1];
        for (int i = id + 1; i < clusterSize; i++) {
            sendpqh(pArr_tmp[i - 1], qArr_tmp[i - 1], hArr_tmp[i - 1], workerUris[i - 1], id);
        }
        // wait to receive all p q h
        // genNPiece();
    }

    private void sendpqh(BigInteger p, BigInteger q, BigInteger h, int tgtUri, int senderId) {
    }

    public void receivepqh(BigInteger p, BigInteger q, BigInteger h, int senderId) {
        pArr[senderId - 1] = p;
        qArr[senderId - 1] = q;
        hArr[senderId - 1] = h;
        // if arrays are all filled
        // genNPiece();
    }

    private BigInteger arraySum(BigInteger[] array) {
        BigInteger result = BigInteger.valueOf(0);
        for (BigInteger element : array) {
            result = result.add(element);
        }
        return result;
    }

    private BigInteger[] nPieceArr;

    private void genNPiece() {
        nPieceArr = new BigInteger[clusterSize];
        BigInteger nPiece = arraySum(pArr).multiply(arraySum(qArr)).add(arraySum(hArr)).mod(P);
        for (int i = 1; i < id; i++) {
            sendNPiece(nPiece, workerUris[i - 1], id);
        }
        nPieceArr[id - 1] = nPiece;
        for (int i = id + 1; i < clusterSize; i++) {
            sendNPiece(nPiece, workerUris[i - 1], id);
        }
        // wait for nPieceArr to fill
        // genN();
    }

    private void sendNPiece(BigInteger nPiece, int tgtUri, int senderId) {
    }

    public void receiveNPiece(BigInteger nPiece, int senderId) {
        nPieceArr[senderId - 1] = nPiece;
        // if array is filled
        // genN();
    }

    private void genN() {
    }
}
