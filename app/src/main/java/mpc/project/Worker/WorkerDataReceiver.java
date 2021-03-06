package mpc.project.Worker;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

public class WorkerDataReceiver {
    WorkerMain worker;

    public WorkerDataReceiver(WorkerMain worker) {
        this.worker = worker;
    }

    public void cleanupModulusGenerationBucket() {
        modulusReadyFlagMap.clear();
        modulusMap.clear();
        primesReadyFlagMap.clear();
        pArrMap.clear();
        qArrMap.clear();
        hArrMap.clear();
        nPieceReadyFlagMap.clear();
        nPieceArrMap.clear();
    }

    private final Object bPieceMapLock = new Object();
    private final Map<Long, Semaphore> bPieceReadyFlagMap = new ConcurrentHashMap<>();
    private final Map<Long, BigInteger> bPieceMap = new ConcurrentHashMap<>();

    private void emptyCheckBPiece(long workflowID) {
        synchronized (bPieceMapLock) {
            if (!bPieceReadyFlagMap.containsKey(workflowID)) {
                bPieceReadyFlagMap.put(workflowID, new Semaphore(0));
            }
        }
    }

    public void receiveBPiece(int id, BigInteger b, long workflowID) {
        emptyCheckBPiece(workflowID);
        bPieceMap.putIfAbsent(workflowID, b);
        bPieceReadyFlagMap.get(workflowID).release();
    }

    public BigInteger waitBPiece(long workflowID) {
        emptyCheckBPiece(workflowID);
        try {
            bPieceReadyFlagMap.get(workflowID).acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        BigInteger b = bPieceMap.get(workflowID);
        bPieceMap.remove(workflowID);
        return b;
    }

    private final Object modulusMapLock = new Object();
    private final Map<Long, Semaphore> modulusReadyFlagMap = new ConcurrentHashMap<>();
    private final Map<Long, BigInteger> modulusMap = new ConcurrentHashMap<>();

    private void emptyCheckModulus(long workflowID) {
        synchronized (modulusMapLock) {
            if (!modulusReadyFlagMap.containsKey(workflowID)) {
                modulusReadyFlagMap.put(workflowID, new Semaphore(0));
            }
        }
    }

    public void receiveModulus(BigInteger modulus, long workflowID) {
        emptyCheckModulus(workflowID);
        modulusMap.putIfAbsent(workflowID, modulus);
    }

    public void countModulus(long workflowID) {
        emptyCheckModulus(workflowID);
        modulusReadyFlagMap.get(workflowID).release();
    }

    public BigInteger waitModulus(long workflowID) {
        emptyCheckModulus(workflowID);
        try {
            modulusReadyFlagMap.get(workflowID).acquire(worker.getClusterSize());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        BigInteger result = modulusMap.get(workflowID);
        modulusMap.remove(workflowID);
        return result;
    }

    private final Object primesLock = new Object();
    private final Map<Long, Semaphore> primesReadyFlagMap = new ConcurrentHashMap<>();
    private final Map<Long, BigInteger[]> pArrMap = new ConcurrentHashMap<>();
    private final Map<Long, BigInteger[]> qArrMap = new ConcurrentHashMap<>();
    private final Map<Long, BigInteger[]> hArrMap = new ConcurrentHashMap<>();

    private void emptyCheckPrimes(long workflowID) {
        synchronized (primesLock) {
            if (!primesReadyFlagMap.containsKey(workflowID)) {
                primesReadyFlagMap.put(workflowID, new Semaphore(0));
                pArrMap.put(workflowID, new BigInteger[worker.getClusterSize()]);
                qArrMap.put(workflowID, new BigInteger[worker.getClusterSize()]);
                hArrMap.put(workflowID, new BigInteger[worker.getClusterSize()]);
            }
        }
    }

    public void receivePHQ(int id, BigInteger p, BigInteger q, BigInteger h, long workflowID) {
        emptyCheckPrimes(workflowID);
        pArrMap.get(workflowID)[id - 1] = p;
        qArrMap.get(workflowID)[id - 1] = q;
        hArrMap.get(workflowID)[id - 1] = h;
        primesReadyFlagMap.get(workflowID).release();
    }

    public void waitPHQ(long workflowID, BigInteger[] pArr, BigInteger[] qArr, BigInteger[] hArr) {
        emptyCheckPrimes(workflowID);
        try {
            primesReadyFlagMap.get(workflowID).acquire(worker.getClusterSize());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Arrays.setAll(pArr, i -> pArrMap.get(workflowID)[i]);
        Arrays.setAll(qArr, i -> qArrMap.get(workflowID)[i]);
        Arrays.setAll(hArr, i -> hArrMap.get(workflowID)[i]);
    }

    private final Object nPieceLock = new Object();
    private final Map<Long, Semaphore> nPieceReadyFlagMap = new ConcurrentHashMap<>();
    private final Map<Long, BigInteger[]> nPieceArrMap = new ConcurrentHashMap<>();

    private void emptyCheckNPiece(long workflowID) {
        synchronized (nPieceLock) {
            if (!nPieceReadyFlagMap.containsKey(workflowID)) {
                nPieceReadyFlagMap.put(workflowID, new Semaphore(0));
                nPieceArrMap.put(workflowID, new BigInteger[worker.getClusterSize()]);
            }
        }
    }

    public void receiveNPiece(int id, BigInteger nPiece, long workflowID) {
        emptyCheckNPiece(workflowID);
        nPieceArrMap.get(workflowID)[id - 1] = nPiece;
        nPieceReadyFlagMap.get(workflowID).release();
    }

    public void waitNPieces(long workflowID, BigInteger[] nPieceArr) {
        emptyCheckNPiece(workflowID);
        try {
            nPieceReadyFlagMap.get(workflowID).acquire(worker.getClusterSize());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Arrays.setAll(nPieceArr, i -> nPieceArrMap.get(workflowID)[i]);
    }

    private final Object darioGammaLock = new Object();
    private final Map<Long, Semaphore> darioGammaReadyFlagMap = new ConcurrentHashMap<>();
    private final Map<Long, BigInteger[]> darioGammaArrMap = new ConcurrentHashMap<>();

    private void emptyCheckDarioGamma(long workflowID) {
        synchronized (darioGammaLock) {
            if (!darioGammaReadyFlagMap.containsKey(workflowID)) {
                darioGammaReadyFlagMap.put(workflowID, new Semaphore(0));
                darioGammaArrMap.put(workflowID, new BigInteger[worker.getClusterSize()]);
            }
        }
    }

    private void cleanDarioGammaBucket(long workflowID) {
        synchronized (darioGammaLock) {
            darioGammaReadyFlagMap.remove(workflowID);
            darioGammaArrMap.remove(workflowID);
        }
    }

    public void receiveDarioGamma(int id, BigInteger gammaSum, long workflowID) {
        worker.dummyLog("generate Private key: " + "receive gammaSum from " + id);
        emptyCheckDarioGamma(workflowID);
        darioGammaArrMap.get(workflowID)[id - 1] = gammaSum;
        darioGammaReadyFlagMap.get(workflowID).release();
    }

    public void waitDarioGamma(long workflowID, BigInteger[] darioGammaArr) {
        emptyCheckDarioGamma(workflowID);
        try {
            darioGammaReadyFlagMap.get(workflowID).acquire(worker.getClusterSize());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Arrays.setAll(darioGammaArr, i -> darioGammaArrMap.get(workflowID)[i]);
        cleanDarioGammaBucket(workflowID);
    }

    private final Object shadowLock = new Object();
    private final Map<Long, Semaphore> shadowReadyFlagMap = new ConcurrentHashMap<>();
    private final Map<Long, String[]> shadowArrMap = new ConcurrentHashMap<>();

    private void emptyCheckShadow(long workflowID) {
        synchronized (shadowLock) {
            if (!shadowReadyFlagMap.containsKey(workflowID)) {
                shadowReadyFlagMap.put(workflowID, new Semaphore(0));
                shadowArrMap.put(workflowID, new String[worker.getClusterSize()]);
            }
        }
    }

    private void cleanShadowBucket(long workflowID) {
        synchronized (shadowLock) {
            shadowReadyFlagMap.remove(workflowID);
            shadowArrMap.remove(workflowID);
        }
    }

    public void receiveShadow(int id, String shadow, long workflowID) {
        emptyCheckShadow(workflowID);
        shadowArrMap.get(workflowID)[id - 1] = shadow;
    }

    public void countShadow(long workflowID) {
        emptyCheckVerificationFactor(workflowID);
        shadowReadyFlagMap.get(workflowID).release();
    }

    public void waitShadow(long workflowID, String[] shadowArr) {
        emptyCheckShadow(workflowID);
        try {
            shadowReadyFlagMap.get(workflowID).acquire(worker.getClusterSize());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Arrays.setAll(shadowArr, i -> shadowArrMap.get(workflowID)[i]);
        cleanShadowBucket(workflowID);
    }

    private final Object verificationFactorLock = new Object();
    private final Map<Long, Semaphore> verificationFactorReadyFlagMap = new ConcurrentHashMap<>();
    private final Map<Long, BigInteger[]> verificationFactorArrMap = new ConcurrentHashMap<>();

    private void emptyCheckVerificationFactor(long workflowID) {
        synchronized (verificationFactorLock) {
            if (!verificationFactorReadyFlagMap.containsKey(workflowID)) {
                verificationFactorReadyFlagMap.put(workflowID, new Semaphore(0));
                verificationFactorArrMap.put(workflowID, new BigInteger[worker.getClusterSize()]);
            }
        }
    }

    private void cleanVerificationFactorBucket(long workflowID) {
        synchronized (verificationFactorLock) {
            verificationFactorReadyFlagMap.remove(workflowID);
            verificationFactorArrMap.remove(workflowID);
        }
    }

    public void receiveVerificationFactor(int id, BigInteger verificationFactor, long workflowID) {
        emptyCheckVerificationFactor(workflowID);
        verificationFactorArrMap.get(workflowID)[id - 1] = verificationFactor;
    }

    public void countVerificationFactor(long workflowID) {
        emptyCheckVerificationFactor(workflowID);
        verificationFactorReadyFlagMap.get(workflowID).release();
    }

    public void waitVerificationFactor(long workflowID, BigInteger[] verificationFactorArr) {
        emptyCheckVerificationFactor(workflowID);
        try {
            verificationFactorReadyFlagMap.get(workflowID).acquire(worker.getClusterSize());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Arrays.setAll(verificationFactorArr, i -> verificationFactorArrMap.get(workflowID)[i]);
        cleanVerificationFactorBucket(workflowID);
    }
}
