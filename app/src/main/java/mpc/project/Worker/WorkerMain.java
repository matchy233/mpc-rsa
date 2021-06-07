package mpc.project.Worker;

import io.grpc.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import mpc.project.util.*;

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

    private final Sieve sieve = new Sieve();

    private volatile boolean abortModulusGeneration;
    public void setAbortModulusGeneration(boolean abortModulusGeneration){
        this.abortModulusGeneration = abortModulusGeneration;
    }

    private final Random rnd;

    private int clusterSize;

    public void setClusterSize(int clusterSize) {
        this.clusterSize = clusterSize;
    }

    public int getClusterSize() {
        return clusterSize;
    }

    /* Variables for distributed RSA keypair generation */
    private final Map<Long, BigInteger> modulusMap = new ConcurrentHashMap<>();
    private final Map<Long, Pair<BigInteger, BigInteger>> pqMap = new ConcurrentHashMap<>();

    /* Rsa Key
     *    Stores exponent e, modulus N and private d
     *    public key:  <e, N>
     *    private key: <d, N>
     */
    private final Key key = new Key();

    public Key getKey() {
        return key;
    }

    private void cleanupModulusGenerationMap() {
        modulusMap.clear();
        pqMap.clear();
        dataReceiver.cleanupModulusGenerationBucket();
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
                    .build()
                    .start();
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

    public BigInteger hostModulusGeneration(int bitNum, BigInteger randomPrime, long workflowID) {
        boolean passPrimalityTest = false;
        BigInteger result;
        setAbortModulusGeneration(false);
        do {
            rpcSender.broadcastModulusGenerationRequest(bitNum, randomPrime, workflowID);
            System.out.println("host waiting for modulus generation");
            result = dataReceiver.waitModulus(workflowID);
            // Todo: implement more elegant trail division
            if(result.gcd(BigInteger.valueOf(30)).equals(BigInteger.ONE)){
                passPrimalityTest = primalityTestHost(workflowID);
            }
            System.out.println("modulus is " + result);
        } while (!abortModulusGeneration && !passPrimalityTest);
        return result;
    }

    public BigInteger generateModulus(int bitNum, BigInteger randomPrime, long workflowID) {
        // Todo: distributed sieving of p and q
        BigInteger p = generateSievedProbablePrime(bitNum, workflowID);
        BigInteger q = generateSievedProbablePrime(bitNum, workflowID);
        generateFGH(p, q, randomPrime, workflowID);
        generateNPiece(randomPrime, workflowID);
        BigInteger modulus = generateN(randomPrime, workflowID);
        modulusMap.put(workflowID, modulus);
        pqMap.put(workflowID, new Pair<>(p, q));
        return modulus;
    }

    private BigInteger generateSievedProbablePrime(int bitNum, long workflowID) {
        BigInteger a = sieve.generateSievedNumber(clusterSize, bitNum, rnd);
        BigInteger b;
        int round = 1;
        if (id == 1) {
            BigInteger[] bArr = MathUtility.generateRandomArraySumToN(clusterSize, a, rnd);
            b = bArr[0];
            for (int i = 2; i <= clusterSize; i++) {
                rpcSender.sendBPiece(i, bArr[i - 1], workflowID);
            }
            round++;
        } else {
            b = dataReceiver.waitBPiece(workflowID);
            round++;
        }
        while (round <= clusterSize) {
            if (id == round) {
                generateFGH(b, a, sieve.getM(), (long) round *clusterSize + workflowID);
            } else {
                generateFGH(b, BigInteger.ZERO, sieve.getM(), (long) round *clusterSize + workflowID);
            }
            b = updateBPiece((long) round *clusterSize + workflowID, sieve.getM());
            round++;
        }
        // to prevent even number
        BigInteger randomFactor = sieve.getRandomFactor(rnd);
        if(b.mod(BigInteger.TWO).equals(randomFactor.mod(BigInteger.TWO))) {
            randomFactor = randomFactor.add(BigInteger.ONE);
        }
        return randomFactor.multiply(sieve.getM()).add(b);
    }

    private void generateFGH(BigInteger p, BigInteger q, BigInteger randomPrime, long workflowID) {
        int l = (clusterSize - 1) / 2;

        BigInteger[] polyF = MathUtility.genRandBigPolynomial(l, randomPrime, rnd);
        polyF[0] = p;

        BigInteger[] polyG = MathUtility.genRandBigPolynomial(l, randomPrime, rnd);
        polyG[0] = q;

        BigInteger[] polyH = MathUtility.genRandBigPolynomial(2 * l, randomPrime, rnd);
        polyH[0] = BigInteger.valueOf(0);

        BigInteger[] pArr_tmp = new BigInteger[clusterSize];
        for (int i = 0; i < clusterSize; i++) {
            pArr_tmp[i] = MathUtility.polynomialResult(polyF, BigInteger.valueOf(i + 1), randomPrime);
        }

        BigInteger[] qArr_tmp = new BigInteger[clusterSize];
        for (int i = 0; i < clusterSize; i++) {
            qArr_tmp[i] = MathUtility.polynomialResult(polyG, BigInteger.valueOf(i + 1), randomPrime);
        }

        BigInteger[] hArr_tmp = new BigInteger[clusterSize];
        for (int i = 0; i < clusterSize; i++) {
            hArr_tmp[i] = MathUtility.polynomialResult(polyH, BigInteger.valueOf(i + 1), randomPrime);
        }

        for (int i = 1; i <= clusterSize; i++) {
            rpcSender.sendPQH(i, pArr_tmp[i - 1], qArr_tmp[i - 1], hArr_tmp[i - 1], workflowID);
        }
    }

    private BigInteger updateBPiece(long workflowID, BigInteger M) {
        BigInteger[] pArr = new BigInteger[clusterSize];
        BigInteger[] qArr = new BigInteger[clusterSize];
        BigInteger[] hArr = new BigInteger[clusterSize];
        dataReceiver.waitPHQ(workflowID, pArr, qArr, hArr);

        BigDecimal intermediateB = new BigDecimal(
                MathUtility.computeSharingResult(pArr, qArr, hArr, M));

        double l = MathUtility.computeTermOfLagrangianPolynomialAtZero(id, clusterSize);
        return intermediateB.multiply(BigDecimal.valueOf(l)).toBigInteger().mod(M);
    }

    private void generateNPiece(BigInteger randomPrime, long workflowID) {
        BigInteger[] pArr = new BigInteger[clusterSize];
        BigInteger[] qArr = new BigInteger[clusterSize];
        BigInteger[] hArr = new BigInteger[clusterSize];
        dataReceiver.waitPHQ(workflowID, pArr, qArr, hArr);
        // [ \sum(p_arr).mod(P) * \sum(q_arr).mod(P) + \sum(h_arr).mod(P) ].mod(P)
        BigInteger nPiece = MathUtility.computeSharingResult(pArr, qArr, hArr, randomPrime);
        rpcSender.broadcastNPiece(nPiece, workflowID);
    }

    private BigInteger generateN(BigInteger randomPrime, long workflowID) {
        BigInteger[] nPieceArr = new BigInteger[clusterSize];
        dataReceiver.waitNPieces(workflowID, nPieceArr);
        double[] values = MathUtility.computeAllValuesOfLagrangianPolynomialAtZero(clusterSize);
        BigDecimal N = new BigDecimal(0);
        for (int i = 0; i < nPieceArr.length; i++) {
            BigDecimal Ni = new BigDecimal(nPieceArr[i]);
            N = N.add(Ni.multiply(BigDecimal.valueOf(values[i])));
        }
        BigInteger modulus = N.toBigInteger().mod(randomPrime);
        return modulus;
    }

    public boolean primalityTestHost(long workflowID) {
        if (!modulusMap.containsKey(workflowID)) {
            System.out.println("workflowID not found! id: " + workflowID);
            return false;
        }
        BigInteger modulus = modulusMap.get(workflowID);
        BigInteger g = MathUtility.genRandBig(modulus, rnd);


        BigInteger[] verificationArray = new BigInteger[this.clusterSize];

        rpcSender.broadcastPrimalityTestRequest(g, workflowID);

        dataReceiver.waitVerificationFactor(workflowID, verificationArray);

        BigInteger v = BigInteger.valueOf(1);
        for (int i = 1; i < clusterSize; i++) {
            v = v.multiply(verificationArray[i]);
        }

        return verificationArray[0].equals(v.mod(modulus));
    }

    public BigInteger primalityTestGuest(int hostID, BigInteger g, long workflowID) {
        Pair<BigInteger, BigInteger> pair = pqMap.get(workflowID);
        BigInteger p = pair.first;
        BigInteger q = pair.second;
        BigInteger modulus = modulusMap.get(workflowID);
        if (id == hostID) {
            BigInteger exponent = modulus.subtract(p).subtract(q).add(BigInteger.valueOf(1));
            return g.modPow(exponent, modulus);
        }
        return g.modPow(p.add(q), modulus);
    }

    public void generatePrivateKey(long workflowID) {
        // Todo: change server 1 every time to do load balancing
        Pair<BigInteger, BigInteger> pair = pqMap.get(workflowID);
        BigInteger p = pair.first;
        BigInteger q = pair.second;
        BigInteger modulus = modulusMap.get(workflowID);
        cleanupModulusGenerationMap();
        key.setN(modulus);
        RSA.init(modulus);
        BigInteger phi = (id == 1) ?
                key.getN().subtract(p).subtract(q).add(BigInteger.ONE) :
                BigInteger.ZERO.subtract(p).subtract((q));
        BigInteger[] gammaArrLocal = MathUtility.generateRandomSumArray(phi, clusterSize, rnd);
        for (int i = 1; i <= clusterSize; i++) {
            rpcSender.sendGamma(i, gammaArrLocal[i - 1], workflowID);
        }
        BigInteger[] gammaArr = new BigInteger[clusterSize];
        dataReceiver.waitGamma(workflowID, gammaArr);
        BigInteger gammaSum = MathUtility.arraySum(gammaArr);
        BigInteger[] gammaSumArr = new BigInteger[clusterSize];
        rpcSender.broadcastGammaSum(gammaSum, workflowID);
        dataReceiver.waitGammaSum(clusterSize, gammaSumArr);
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
            String[] decryptionResults = trialDecryption(encryptedTestMessage, workflowID);
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

    private String[] trialDecryption(String encryptedMessage, long workflowID) {
        String[] result = new String[clusterSize];
        for (int i = 1; i <= clusterSize; i++) {
            rpcSender.sendDecryptRequest(i, encryptedMessage, workflowID);
        }
        System.out.println("Waiting for trial decryption to complete");
        dataReceiver.waitShadow(workflowID, result);
        return result;
    }
}
