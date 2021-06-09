package mpc.project.Worker;

import io.grpc.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

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

    public void setAbortModulusGeneration(boolean abortModulusGeneration) {
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
                    .executor(Executors.newCachedThreadPool())
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
            if (result.gcd(BigInteger.valueOf(30)).equals(BigInteger.ONE)) {
                passPrimalityTest = primalityTestHost(workflowID);
            }
            System.out.println("modulus is " + result);
        } while (!abortModulusGeneration && !passPrimalityTest);
        return result;
    }

    public BigInteger generateModulus(int hostID, int bitNum, BigInteger randomPrime, long workflowID) {
        BigInteger p = generateSievedProbablePrime(hostID, bitNum, workflowID);
        BigInteger q = generateSievedProbablePrime(hostID, bitNum, workflowID);
        generateFGH(p, q, randomPrime, workflowID);
        generateNPiece(randomPrime, workflowID);
        BigInteger modulus = generateN(randomPrime, workflowID);
        modulusMap.put(workflowID, modulus);
        pqMap.put(workflowID, new Pair<>(p, q));
        return modulus;
    }

    private long uniqueBPieceShareWorkflowID(int round, long currentWorkflowID) {
        return (long) round * (clusterSize + 1) + currentWorkflowID;
    }

    private BigInteger generateSievedProbablePrime(int hostID, int bitNum, long workflowID) {
        BigInteger a = sieve.generateSievedNumber(clusterSize, bitNum, rnd);
        BigInteger b;
        if (id == hostID) {
            BigInteger[] bArr = MathUtility.generateRandomArraySumToN(clusterSize, a, rnd);
            rpcSender.broadcastBPieceArr(bArr, workflowID);
        }
        b = dataReceiver.waitBPiece(workflowID);
        int shareRound = id - hostID;
        if (shareRound < 0) {
            shareRound += clusterSize;
        }
        for (int i = 1; i < clusterSize; i++) {
            long subWorkflowID = uniqueBPieceShareWorkflowID(i, workflowID);
            BigInteger u = (i == shareRound) ? a : BigInteger.ZERO;
            generateFGH(b, u, sieve.getM(), subWorkflowID);
            b = updateBPiece(subWorkflowID, sieve.getM());
        }
        // to prevent even number
        BigInteger randomFactor = sieve.getRandomFactor(rnd);
        if (b.mod(BigInteger.TWO).equals(randomFactor.mod(BigInteger.TWO))) {
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

        rpcSender.broadcastPQHArr(pArr_tmp, qArr_tmp, hArr_tmp, workflowID);
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
        return N.toBigInteger().mod(randomPrime);
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
        for (int i = 0; i < clusterSize; i++) {
            if(i == id-1){
                continue;
            }
            v = v.multiply(verificationArray[i]);
        }

        return verificationArray[id-1].equals(v.mod(modulus));
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

    private boolean keyReady = false;
    private void waitForKey(){
        while(!keyReady){
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    private void setKeyReady(boolean keyStatus){
        keyReady = keyStatus;
    }

    public void generatePrivateKey(long workflowID) {
        setKeyReady(false);

        System.out.println("generate Private key: " + "start");
        Pair<BigInteger, BigInteger> pair = pqMap.get(workflowID);
        BigInteger p = pair.first;
        BigInteger q = pair.second;
        System.out.println("p is " + p);
        System.out.println("q is " + q);
        BigInteger modulus = modulusMap.get(workflowID);
        cleanupModulusGenerationMap();

        key.setN(modulus);

        System.out.println("generate Private key: " + "compute Phi");

        BigInteger phi = (id == 1) ?
                key.getN().subtract(p).subtract(q).add(BigInteger.ONE) :
                BigInteger.ZERO.subtract(p).subtract((q));
        System.out.println("phi is " + phi);

        BigInteger darioRandomizer = BigInteger.valueOf(rnd.nextLong());
        BigInteger darioGammaPiece = phi.add(key.getE().multiply(darioRandomizer));

        System.out.println("generate Private key: " + "gamma sum array");
        BigInteger[] darioGammaPieceArr = new BigInteger[clusterSize];
        rpcSender.broadcastDarioGamma(darioGammaPiece, workflowID);
        dataReceiver.waitDarioGamma(workflowID, darioGammaPieceArr);

        BigInteger darioGamma = MathUtility.arraySum(darioGammaPieceArr);

        BigInteger a = darioGamma.modInverse(key.getE());
        BigInteger b = BigInteger.ONE.subtract(a.multiply(darioGamma)).divide(key.getE());
        System.out.println("verify: " + a.multiply(darioGamma).add(b.multiply(key.getE())));

        BigInteger d = (id == 1)?
                a.multiply(darioRandomizer).add(b) :
                a.multiply(darioRandomizer);

        System.out.println("d is " + d);

        System.out.println("generate Private key: " + "finished trial generate private key!");
        key.setD(d);
        setKeyReady(true);

        // Start a trial division
        if (id == 1) {
            String testMsg = "Lorem ipsum dolor sit amet\n";
            String encryptedTestMessage = RSA.encrypt(testMsg, key);
            String[] decryptionResults = trialDecryption(encryptedTestMessage, workflowID);

            boolean foundR = false;
            for (int r = 0; r <= clusterSize; r++) {
                key.setD(d.add(BigInteger.valueOf(r)));
                decryptionResults[0] = RSA.localDecrypt(encryptedTestMessage, key);
                String tryD = RSA.combineDecryptionResult(decryptionResults, key);

                System.out.println("r is " + r + " and decryption is " + tryD);

                foundR = tryD.equals(testMsg);

                if (foundR) {
                    break;
                }
            }
            if (!foundR) {
                System.out.println("Cannot find r!! Something is wrong with our implementation!");
                System.exit(-6);
            }
        }
    }

    public String decrypt(String encryptedMessage){
        waitForKey();
        return RSA.localDecrypt(encryptedMessage, key);
    }

    private String[] trialDecryption(String encryptedString, long workflowID) {
        String[] result = new String[clusterSize];
        for (int i = 1; i <= clusterSize; i++) {
            rpcSender.sendDecryptRequest(i, encryptedString, workflowID);
        }
        System.out.println("Waiting for trial decryption to complete");
        dataReceiver.waitShadow(workflowID, result);
        return result;
    }
}
