package mpc.project;

import java.math.BigInteger;
import java.util.concurrent.Semaphore;

public class WorkerDataReceiver {
    WorkerMain worker;

    public WorkerDataReceiver(WorkerMain worker) {
        this.worker = worker;
        try {
            this.primesReadyFlag.acquire();
            this.nPiecesReadyFlag.acquire();
            this.gammaReadyFlag.acquire();
            this.gammaSumReadyFlag.acquire();
            this.verificationFactorsReadyFlag.acquire();
            this.shadowsReadyFlag.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public BigInteger[] pArr;          // An array holding p_i ( i \in [1, clusterNum])
    public BigInteger[] qArr;          // An array holding q_i ( i \in [1, clusterNum])
    public BigInteger[] hArr;          // An array holding h_i ( i \in [1, clusterNum])

    public BigInteger[] nPieceArr;
    public BigInteger[] gammaArr;
    public BigInteger[] gammaSumArr;

    public final Object exchangeGammaLock = new Object();
    //    private Lock gammaReadyFlag = new ReentrantLock();
    private final Semaphore gammaReadyFlag = new Semaphore(1);
    private int exchangeGammaCounter = 0;

    public final Object exchangeGammaSumLock = new Object();
    private final Semaphore gammaSumReadyFlag = new Semaphore(1);
    private int exchangeGammaSumCounter = 0;


    public final Object exchangePrimesLock = new Object();
    private final Semaphore primesReadyFlag = new Semaphore(1);
    private int exchangePrimesCounter = 0;

    public final Object exchangeNPiecesLock = new Object();
    private final Semaphore nPiecesReadyFlag = new Semaphore(1);
    private int exchangeNPiecesCounter = 0;

    private final Object shadowReceivingLock = new Object();
    private final Semaphore shadowsReadyFlag = new Semaphore(1);
    private int shadowReceivingCounter = 0;

    private final Object verificationFactorsLock = new Object();
    private final Semaphore verificationFactorsReadyFlag = new Semaphore(1);
    private int verificationFactorsCounter = 0;

    public void receivePHQ(int id, BigInteger p, BigInteger q, BigInteger h) {
        int i = id - 1;
        pArr[i] = p;
        qArr[i] = q;
        hArr[i] = h;
        synchronized (exchangePrimesLock) {
            exchangePrimesCounter++;
            if (exchangePrimesCounter == worker.getClusterSize()) {
                primesReadyFlag.release();
                exchangePrimesCounter = 0;
            }
        }
    }

    public void waitPHQ() {
        try {
            primesReadyFlag.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void receiveNPiece(int id, BigInteger nPiece) {
        int i = id - 1;
        nPieceArr[i] = nPiece;
        synchronized (exchangeNPiecesLock) {
            exchangeNPiecesCounter++;
            if (exchangeNPiecesCounter == worker.getClusterSize()) {
                nPiecesReadyFlag.release();
                exchangeNPiecesCounter = 0;
            }
        }
    }

    public void waitNPieces() {
        try {
            nPiecesReadyFlag.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void receiveGamma(int id, BigInteger gamma) {
        int i = id - 1;
        gammaArr[i] = gamma;
        synchronized (exchangeGammaLock) {
            exchangeGammaCounter++;
            if (exchangeGammaCounter == worker.getClusterSize()) {
                gammaReadyFlag.release();
                exchangeGammaCounter = 0;
            }
        }
    }

    public void waitGamma() {
        try {
            gammaReadyFlag.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void receiveGammaSum(int id, BigInteger gammaSum) {
        int i = id - 1;
        gammaSumArr[i] = gammaSum;
        synchronized (exchangeGammaSumLock) {
            exchangeGammaSumCounter++;
            if (exchangeGammaSumCounter == worker.getClusterSize()) {
                gammaSumReadyFlag.release();
                exchangeGammaSumCounter = 0;
            }
        }
    }

    public void waitGammaSum() {
        try {
            gammaSumReadyFlag.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void receiveShadow(int id, String factor, String[] resultBucket) {
        int j = id - 1;
        resultBucket[j] = factor;
        synchronized (shadowReceivingLock) {
            shadowReceivingCounter++;
            if (shadowReceivingCounter == worker.getClusterSize()) {
                shadowsReadyFlag.release();
                shadowReceivingCounter = 0;
            }
        }
    }

    public void waitShadows() {
        try {
            shadowsReadyFlag.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void receiveVerificationFactor(int id, BigInteger factor, BigInteger[] resultBucket) {
        int j = id - 1;
        resultBucket[j] = factor;
        synchronized (verificationFactorsLock) {
            verificationFactorsCounter++;
            if (verificationFactorsCounter == worker.getClusterSize()) {
                verificationFactorsReadyFlag.release();
                verificationFactorsCounter = 0;
            }
        }
    }

    public void waitVerificationFactors() {
        try {
            verificationFactorsReadyFlag.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
