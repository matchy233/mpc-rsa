package mpc.project;

import java.util.concurrent.Semaphore;

public class ManagerDataReceiver {
    final private ManagerMain manager;

    public ManagerDataReceiver(ManagerMain manager) {
        this.manager = manager;
        try {
            networkFormedFlag.acquire();
            modulusGenerationFlag.acquire();
            primalityTestCompleteFlag.acquire();
            privateKeyGenerationFlag.acquire();
            shadowCollectedFlag.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    final private Object formNetworkLock = new Object();
    final private Semaphore networkFormedFlag = new Semaphore(1);
    volatile private int formNetworkCounter = 0;

    public void receiveNetworkFormingResponse() {
        synchronized (formNetworkLock) {
            formNetworkCounter++;
            if (formNetworkCounter == manager.getClusterSize()) {
                formNetworkCounter = 0;
                networkFormedFlag.release();
            }
        }
    }

    public void waitNetworkForming() {
        try {
            networkFormedFlag.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    final Object modulusGenerationLock = new Object();
    final private Semaphore modulusGenerationFlag = new Semaphore(1);
    volatile private int modulusGenerationCounter = 0;

    public void receiveModulusGenerationResponse() {
        synchronized (modulusGenerationLock) {
            modulusGenerationCounter++;
            if (modulusGenerationCounter == manager.getClusterSize()) {
                modulusGenerationCounter = 0;
                modulusGenerationFlag.release();
            }
        }
    }

    public void waitModulusGeneration() {
        try {
            modulusGenerationFlag.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    final private Semaphore primalityTestCompleteFlag = new Semaphore(1);
    volatile private boolean primalityTestResult;

    public void receivePrimalityTestResult(boolean primalityTestResult) {
        this.primalityTestResult = primalityTestResult;
        primalityTestCompleteFlag.release();
    }

    public boolean waitPrimalityTestResult() {
        try {
            primalityTestCompleteFlag.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return primalityTestResult;
    }

    final Object privateKeyGenerationLock = new Object();
    final private Semaphore privateKeyGenerationFlag = new Semaphore(1);
    volatile private int privateKeyGenerationCounter = 0;

    public void receivePrivateKeyGenerationResponse() {
        synchronized (privateKeyGenerationLock) {
            privateKeyGenerationCounter++;
            if (privateKeyGenerationCounter == manager.getClusterSize()) {
                privateKeyGenerationCounter = 0;
                privateKeyGenerationFlag.release();
            }
        }
    }

    public void waitPrivateKeyGeneration() {
        try {
            privateKeyGenerationFlag.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    final private Object decryptionLock = new Object();
    final private Semaphore shadowCollectedFlag = new Semaphore(1);
    volatile private int decryptionCounter = 0;

    public void receiveDecryptionResult(int id, String shadow, String[] resultBucket) {
        resultBucket[id - 1] = shadow;
        synchronized (decryptionLock) {
            decryptionCounter++;
            if (decryptionCounter == manager.getClusterSize()) {
                decryptionCounter = 0;
                shadowCollectedFlag.release();
            }
        }
    }

    public void waitDecryptionShadow() {
        try {
            shadowCollectedFlag.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
