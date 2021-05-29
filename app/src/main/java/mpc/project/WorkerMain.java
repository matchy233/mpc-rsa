package mpc.project;

import io.grpc.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Random;

import mpc.project.util.Key;
import mpc.project.util.MathUtility;
import mpc.project.util.RSA;

public class WorkerMain {
    private Server server;
    private WorkerRPCSender rpcSender;

    public WorkerRPCSender getRpcSender() {
        return rpcSender;
    }

    private WorkerDataReceiver dataReceiver;

    public WorkerDataReceiver getDataReceiver() {
        return dataReceiver;
    }

    private final int portNum;
    private int id;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    private final Random rnd;

    private int clusterSize;

    public void setClusterSize(int clusterSize) {
        this.clusterSize = clusterSize;
        dataBucketInit();
    }

    public int getClusterSize() {
        return clusterSize;
    }

    /* Variables for distributed RSA keypair generation */
    private BigInteger p;
    private BigInteger q;
    private BigInteger[] pArr;          // An array holding p_i ( i \in [1, clusterNum])
    private BigInteger[] qArr;          // An array holding q_i ( i \in [1, clusterNum])
    private BigInteger[] hArr;          // An array holding h_i ( i \in [1, clusterNum])
    private BigInteger[] nPieceArr;

    /* Rsa Key
     *    Stores exponent e, modulus N and private d
     *    public key:  <e, N>
     *    private key: <d, N>
     */
    private Key key = new Key();

    public Key getKey() {
        return key;
    }

    public WorkerMain(int portNum) {
        this.portNum = portNum;
        this.rnd = new Random();
    }

    public void run() {
        this.rpcSender = new WorkerRPCSender(this);
        this.dataReceiver = new WorkerDataReceiver(this);
        try {
            this.server = ServerBuilder.forPort(portNum)
                    .addService(new WorkerRPCReceiverService(this))
                    .build().start();
            System.out.println("Waiting for manager to connect");
            this.server.awaitTermination();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.exit(-2);
        } finally {
            if (server != null) {
                server.shutdownNow();
            }
        }
    }

    private void dataBucketInit() {
        pArr = new BigInteger[clusterSize];
        qArr = new BigInteger[clusterSize];
        hArr = new BigInteger[clusterSize];
        nPieceArr = new BigInteger[clusterSize];
        gammaArr = new BigInteger[clusterSize];
        gammaSumArr = new BigInteger[clusterSize];

        dataReceiver.pArr = this.pArr;
        dataReceiver.qArr = this.qArr;
        dataReceiver.hArr = this.hArr;
        dataReceiver.nPieceArr = this.nPieceArr;
        dataReceiver.gammaArr = this.gammaArr;
        dataReceiver.gammaSumArr = this.gammaSumArr;
    }

    private final Object modulusGenerationLock = new Object();

    public void generateModulusPiece(int bitNum, BigInteger randomPrime) {
        synchronized (modulusGenerationLock) {
            p = BigInteger.probablePrime(bitNum, rnd);
            q = BigInteger.probablePrime(bitNum, rnd);
            generateFGH(randomPrime);
            generateNPiece(randomPrime);
            generateN(randomPrime);
        }
    }

    private void generateFGH(BigInteger randomPrime) {
        int l = (clusterSize - 1) / 2;

        BigInteger[] polyF = new BigInteger[l];
        polyF[0] = p;
        for (int i = 1; i < polyF.length; i++) {
            polyF[i] = MathUtility.genRandBig(randomPrime, rnd);
        }

        BigInteger[] polyG = new BigInteger[l];
        polyG[0] = q;
        for (int i = 1; i < polyG.length; i++) {
            polyG[i] = MathUtility.genRandBig(randomPrime, rnd);
        }

        BigInteger[] polyH = new BigInteger[2 * l];
        polyH[0] = BigInteger.valueOf(0);
        for (int i = 1; i < polyH.length; i++) {
            polyH[i] = MathUtility.genRandBig(randomPrime, rnd);
        }

        BigInteger[] pArr_tmp = new BigInteger[clusterSize];
        for (int i = 0; i < clusterSize; i++) {
            pArr_tmp[i] = MathUtility.polynomialResult(polyF, BigInteger.valueOf(i + 1));
        }

        BigInteger[] qArr_tmp = new BigInteger[clusterSize];
        for (int i = 0; i < clusterSize; i++) {
            qArr_tmp[i] = MathUtility.polynomialResult(polyG, BigInteger.valueOf(i + 1));
        }

        BigInteger[] hArr_tmp = new BigInteger[clusterSize];
        for (int i = 0; i < clusterSize; i++) {
            hArr_tmp[i] = MathUtility.polynomialResult(polyH, BigInteger.valueOf(i + 1));
        }

        // Wait to receive all p q h
        for (int i = 1; i <= clusterSize; i++) {
            rpcSender.sendPQH(i, pArr_tmp[i - 1], qArr_tmp[i - 1], hArr_tmp[i - 1]);
        }
    }

    private void generateNPiece(BigInteger randomPrime) {
        dataReceiver.waitPHQ();
        BigInteger nPiece = (MathUtility.arraySum(pArr)
                .multiply(MathUtility.arraySum(qArr)))
                .add(MathUtility.arraySum(hArr))
                .mod(randomPrime);
        for (int i = 1; i <= clusterSize; i++) {
            rpcSender.sendNPiece(i, nPiece);
        }
    }

    private void generateN(BigInteger randomPrime) {
        dataReceiver.waitNPieces();
        double[] values = MathUtility.computeValuesOfLagrangianPolynomialsAtZero(clusterSize);
        BigDecimal N = new BigDecimal(0);
        for (int i = 0; i < nPieceArr.length; i++) {
            BigDecimal Ni = new BigDecimal(nPieceArr[i]);
            N = N.add(Ni.multiply(BigDecimal.valueOf(values[i])));
        }
        key.setN(N.toBigInteger().mod(randomPrime));
        RSA.init(key.getN());
        System.out.println("The modulus is :" + key.getN());
    }

    public boolean primalityTestWaiting = false;

    public boolean primalityTestHost() {
        BigInteger g = MathUtility.genRandBig(key.getN(), rnd);

        BigInteger[] verificationArray = new BigInteger[this.clusterSize];

        primalityTestWaiting = true;
        for (int i = 1; i <= clusterSize; i++) {
            rpcSender.sendPrimalityTestRequest(i, g, verificationArray);
        }
        dataReceiver.waitVerificationFactors();
        primalityTestWaiting = false;

        BigInteger v = BigInteger.valueOf(1);
        for (int i = 1; i < clusterSize; i++) {
            v = v.multiply(verificationArray[i]);
        }

        return verificationArray[0].equals(v.mod(key.getN()));
    }

    public BigInteger primalityTestGuest(BigInteger g) {
        // Todo: change server 1 every time to do load balancing
        if (id == 1) {
            BigInteger exponent = key.getN().subtract(p).subtract(q).add(BigInteger.valueOf(1));
            return g.modPow(exponent, key.getN());
        }
        return g.modPow(p.add(q), key.getN());
    }

    BigInteger[] gammaArr;
    BigInteger[] gammaSumArr;

    public void generatePrivateKey() {
        // Todo: change server 1 every time to do load balancing
        BigInteger phi = (id == 1) ?
                key.getN().subtract(p).subtract(q).add(BigInteger.ONE) :
                BigInteger.ZERO.subtract(p).subtract((q));
        BigInteger[] gammaArrLocal = MathUtility.generateRandomSumArray(phi, clusterSize, rnd);
        for (int i = 1; i <= clusterSize; i++) {
            rpcSender.sendGamma(i, gammaArrLocal[i - 1]);
        }
        dataReceiver.waitGamma();
        BigInteger gammaSum = MathUtility.arraySum(gammaArr);
        for (int i = 1; i <= clusterSize; i++) {
            rpcSender.sendGammaSum(i, gammaSum);
        }
        dataReceiver.waitGammaSum();
        BigInteger l = MathUtility.arraySum(gammaSumArr).mod(key.getE());

        BigDecimal zeta = BigDecimal.ONE.divide(new BigDecimal(l), RoundingMode.HALF_UP)
                .remainder(new BigDecimal(key.getE()));

        BigInteger d = zeta.negate()
                .multiply(new BigDecimal(phi))
                .divide(new BigDecimal(key.getE()), RoundingMode.HALF_UP)
                .toBigInteger();

        // Start a trial division
        if (id == 1) {
            String testMessage = "test";
            String encryptedTestMessage = RSA.encrypt(testMessage, key);
            String[] decryptionResults = trialDecryption(encryptedTestMessage);
            boolean foundR = false;
            for (int r = 0; r < clusterSize; r++) {
                // Fixme: I'm not sure if this is implemented correctly
                key.setD(d.subtract(BigInteger.valueOf(r)));
                decryptionResults[0] = RSA.localDecrypt(encryptedTestMessage, key);
                foundR = RSA.combineDecryptionResult(decryptionResults, key).equals(testMessage);
                if (foundR) {
                    break;
                }
            }
            if (!foundR) {
                System.out.println("Cannot find r!! Something is wrong with our implementation!");
                System.exit(-6);
            }
        } else {
            key.setD(d);
        }
    }

    private String[] trialDecryption(String encryptedMessage) {
        String[] result = new String[clusterSize];
        for (int i = 1; i <= clusterSize; i++) {
            rpcSender.sendDecryptRequest(i, encryptedMessage, result);
        }
        System.out.println("Waiting for trial decryption to complete");
        dataReceiver.waitShadows();
        return result;
    }
}
