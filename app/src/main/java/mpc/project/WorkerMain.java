package mpc.project;

import io.grpc.*;
import io.grpc.stub.StreamObserver;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Random;

import mpc.project.util.Key;
import mpc.project.util.RpcUtility;
import mpc.project.util.MathUtility;
import mpc.project.util.RSA;

public class WorkerMain {
    private Server server;
    private final int portNum;
    private int id;
    private final Random rnd;
    private int bitNum;
    private String[] addressBook;
    private int clusterSize;
    private WorkerServiceGrpc.WorkerServiceStub[] stubs;
    private ManagerServiceGrpc.ManagerServiceBlockingStub managerStub;

    /* Variables for distributed RSA keypair generation */
    private BigInteger randomPrime;
    private BigInteger p;
    private BigInteger q;
    BigInteger[] pArr;          // An array holding p_i ( i \in [1, clusterNum])
    BigInteger[] qArr;          // An array holding q_i ( i \in [1, clusterNum])
    BigInteger[] hArr;          // An array holding h_i ( i \in [1, clusterNum])
    private BigInteger[] nPieceArr;

    /* Rsa Key
     *    Stores exponent e, modulus N and private d
     *    public key:  <e, N>
     *    private key: <d, N>
     */
    private Key key = new Key();

    /* Locks for synchronization between workers */
    // Todo: find a more elegant way to implement synchronization
    private final Object exchangePrimesLock = new Object();
    private int exchangePrimesWorkersCounter = 0;

    private final Object exchangeNPiecesLock = new Object();
    private int exchangeNPiecesWorkersCounter = 0;

    private final Object modulusGenerationLock = new Object();

    class WorkerServiceImpl extends WorkerServiceGrpc.WorkerServiceImplBase {
        @Override
        public void formCluster(StdRequest request, StreamObserver<StdResponse> responseObserver) {
//            id = request.getId();
//            randomPrime = new BigInteger(request.getContents().toByteArray());
            StdResponse res = RpcUtility.Response.newStdResponse(id);
            responseObserver.onNext(res);
            responseObserver.onCompleted();
            System.out.println("connected to Manager");
        }

        @Override
        public void formNetwork(StdRequest request, StreamObserver<StdResponse> responseObserver) {
            String midString = new String(request.getContents().toByteArray());
            addressBook = midString.split(";");
            StdResponse response = RpcUtility.Response.newStdResponse(id);
            stubs = new WorkerServiceGrpc.WorkerServiceStub[addressBook.length];
            System.out.println("received and parsed addressBook: ");
            for (int i = 0; i < addressBook.length; i++) {
                System.out.println(addressBook[i]);
                Channel channel = ManagedChannelBuilder.forTarget(addressBook[i]).usePlaintext().build();
                stubs[i] = WorkerServiceGrpc.newStub(channel);
            }
            clusterSize = addressBook.length;
            keyGenerationArrayInit();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }

        @Override
        public void registerManager(StdRequest request, StreamObserver<StdResponse> responseObserver) {
            id = request.getId();
            String managerUri = new String(request.getContents().toByteArray());
            Channel channel = ManagedChannelBuilder.forTarget(managerUri).usePlaintext().build();
            managerStub = ManagerServiceGrpc.newBlockingStub(channel);
            StdRequest greetingReq = RpcUtility.Request.newStdRequest(id);
            managerStub.greeting(greetingReq);
            responseObserver.onNext(RpcUtility.Response.newStdResponse(id));
            responseObserver.onCompleted();
            System.out.println("registered manager at " + managerUri);
        }

        @Override
        public void generateModulusPiece(StdRequest request, StreamObserver<StdResponse> responseObserver) {
            synchronized (modulusGenerationLock) {
                // id is used for bitNum now, not id
                bitNum = request.getId();
                randomPrime = new BigInteger(request.getContents().toByteArray());
//                p = genRandPrimeBig(bitNum, randomPrime, rnd);
                p = BigInteger.probablePrime(bitNum, rnd);
//                q = genRandPrimeBig(bitNum, randomPrime, rnd);
                q = BigInteger.probablePrime(bitNum, rnd);
                WorkerMain.this.generateModulusPiece();
                responseObserver.onNext(RpcUtility.Response.newStdResponse(id));
                responseObserver.onCompleted();
            }
        }

        @Override
        public void exchangePrimesPQH(ExchangePrimespqhRequest request, StreamObserver<StdResponse> responseObserver) {
            synchronized (exchangePrimesLock) {
                int i = request.getId() - 1;
//                System.out.println("receiving " + i + " prime p q h");
                pArr[i] = new BigInteger(request.getP().toByteArray());
                qArr[i] = new BigInteger(request.getQ().toByteArray());
                hArr[i] = new BigInteger(request.getH().toByteArray());
                responseObserver.onNext(RpcUtility.Response.newStdResponse(id));
                responseObserver.onCompleted();
                exchangePrimesWorkersCounter++;
                if (exchangePrimesWorkersCounter == 2 * clusterSize) {
                    exchangePrimesLock.notify();
                }
            }
        }

        @Override
        public void exchangeNPiece(StdRequest request, StreamObserver<StdResponse> responseObserver) {
            synchronized (exchangeNPiecesLock) {
                int i = request.getId() - 1;
//                System.out.println("receiving " + i + " N piece");
                nPieceArr[i] = new BigInteger(request.getContents().toByteArray());
                responseObserver.onNext(RpcUtility.Response.newStdResponse(id));
                responseObserver.onCompleted();
                exchangeNPiecesWorkersCounter++;
                if (exchangeNPiecesWorkersCounter == 2 * clusterSize) {
                    exchangeNPiecesLock.notify();
                }
            }
        }

        @Override
        public void primalityTest(StdRequest request, StreamObserver<PrimalityTestResponse> responseObserver) {
//            System.out.println("receive primalityTest RPC");
            if (id == 1 && !primalityTestWaiting) {
                boolean passPrimalityTest = primalityTestHost();
                PrimalityTestResponse response;
                if (passPrimalityTest) {
                    response = RpcUtility.Response.newPrimalityTestResponse(1);
                } else {
                    response = RpcUtility.Response.newPrimalityTestResponse(0);
                }
                responseObserver.onNext(response);
            } else {
                BigInteger result = primalityTestGuest(new BigInteger(request.getContents().toByteArray()));
                responseObserver.onNext(RpcUtility.Response.newPrimalityTestResponse(id, result));
            }
            responseObserver.onCompleted();
        }

        @Override
        public void exchangeGamma(StdRequest request, StreamObserver<StdResponse> responseObserver) {
            synchronized (exchangeGammaLock) {
                int i = request.getId() - 1;
                BigInteger gamma = new BigInteger(request.getContents().toByteArray());
                gammaArr[i] = gamma;
                responseObserver.onNext(RpcUtility.Response.newStdResponse(id));
                responseObserver.onCompleted();
                exchangeGammaCounter++;
                if (exchangeGammaCounter == 2 * clusterSize) {
                    exchangeGammaLock.notify();
                }
            }
        }

        @Override
        public void exchangeGammaSum(StdRequest request, StreamObserver<StdResponse> responseObserver) {
            synchronized (exchangeGammaSumLock) {
                int i = request.getId() - 1;
                BigInteger gammaSum = new BigInteger(request.getContents().toByteArray());
                gammaSumArr[i] = gammaSum;
                responseObserver.onNext(RpcUtility.Response.newStdResponse(id));
                responseObserver.onCompleted();
                exchangeGammaSumCounter++;
                if (exchangeGammaSumCounter == 2 * clusterSize) {
                    exchangeGammaSumLock.notify();
                }
            }
        }

        @Override
        public void generatePrivateKey(StdRequest request, StreamObserver<StdResponse> responseObserver) {
            WorkerMain.this.generatePrivateKey();
            responseObserver.onNext(RpcUtility.Response.newStdResponse(id));
            responseObserver.onCompleted();
        }

        @Override
        public void decrypt(StdRequest request, StreamObserver<StdResponse> responseObserver) {
            String encryptedString = new String(request.getContents().toByteArray());
            String shadow = RSA.localDecrypt(encryptedString, key);
            responseObserver.onNext(RpcUtility.Response.newStdResponse(id, shadow));
            responseObserver.onCompleted();
        }
    }

    public WorkerMain(int portNum) {
        this.portNum = portNum;
        this.rnd = new Random();
    }

    public void run() {
        try {
            this.server = ServerBuilder.forPort(portNum)
                    .addService(new WorkerServiceImpl())
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

    private void keyGenerationArrayInit() {
        pArr = new BigInteger[clusterSize];
        qArr = new BigInteger[clusterSize];
        hArr = new BigInteger[clusterSize];
        nPieceArr = new BigInteger[clusterSize];
        gammaArr = new BigInteger[clusterSize];
        gammaSumArr = new BigInteger[clusterSize];
    }

    private void generateModulusPiece() {
        generateFGH();
    }

    private void generateFGH() {
        synchronized (exchangePrimesLock) {
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
                sendPQH(i, pArr_tmp[i - 1], qArr_tmp[i - 1], hArr_tmp[i - 1]);
            }
            if (exchangePrimesWorkersCounter < 2 * clusterSize) {
                try {
                    exchangePrimesLock.wait();
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    System.exit(-1);
                }
            }
        }
        exchangePrimesWorkersCounter = 0;
        generateNPiece();
    }

    private void sendPQH(int i, BigInteger p, BigInteger q, BigInteger h) {
        ExchangePrimespqhRequest request = RpcUtility.Request.newExchangePrimesRequest(this.id, p, q, h);
        stubs[i - 1].exchangePrimesPQH(request, new StreamObserver<>() {
            @Override
            public void onNext(StdResponse response) {
            }

            @Override
            public void onError(Throwable t) {
                System.out.println("exchangePQH RPC error for " + i + " : " + t.getMessage());
                System.exit(-1);
            }

            @Override
            public void onCompleted() {
//                System.out.println("sent!");
                synchronized (exchangePrimesLock) {
                    exchangePrimesWorkersCounter++;
                    if (exchangePrimesWorkersCounter == 2 * clusterSize) {
                        exchangePrimesLock.notify();
                    }
                }
            }
        });
    }

    private void sendNPiece(int i, BigInteger nPiece) {
        StdRequest request = RpcUtility.Request.newStdRequest(this.id, nPiece);
        stubs[i - 1].exchangeNPiece(request, new StreamObserver<StdResponse>() {
            @Override
            public void onNext(StdResponse response) {
            }

            @Override
            public void onError(Throwable t) {
                System.out.println("sendNPiece RPC error for " + i + " : " + t.getMessage());
                System.exit(-1);
            }

            @Override
            public void onCompleted() {
//                System.out.println("sent!");
                synchronized (exchangeNPiecesLock) {
                    exchangeNPiecesWorkersCounter++;
                    if (exchangeNPiecesWorkersCounter == 2 * clusterSize) {
                        exchangeNPiecesLock.notify();
                    }
                }
            }
        });
    }

    private void generateNPiece() {
        synchronized (exchangeNPiecesLock) {
            BigInteger nPiece = (MathUtility.arraySum(pArr)
                    .multiply(MathUtility.arraySum(qArr)))
                    .add(MathUtility.arraySum(hArr))
                    .mod(randomPrime);
            for (int i = 1; i <= clusterSize; i++) {
                sendNPiece(i, nPiece);
            }
            if (exchangeNPiecesWorkersCounter < 2 * clusterSize) {
                try {
                    exchangeNPiecesLock.wait();
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    System.exit(-1);
                }
            }
        }
        exchangeNPiecesWorkersCounter = 0;
        generateN();
    }

    private void generateN() {
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

    final Object primalityTestLock = new Object();
    int primalityTestCounter = 0;
    boolean primalityTestWaiting = false;

    private boolean primalityTestHost() {
        BigInteger g = MathUtility.genRandBig(key.getN(), rnd);

        BigInteger[] verificationArray = new BigInteger[this.clusterSize];

        synchronized (primalityTestLock) {
            primalityTestWaiting = true;
            primalityTestCounter = 0;
            for (int i = 0; i < addressBook.length; i++) {
                StdRequest request = RpcUtility.Request.newStdRequest(id, g);
                stubs[i].primalityTest(request, new StreamObserver<PrimalityTestResponse>() {
                    @Override
                    public void onNext(PrimalityTestResponse value) {
                        int j = value.getId() - 1;
                        BigInteger v = new BigInteger(value.getV().toByteArray());
                        verificationArray[j] = v;
                    }

                    @Override
                    public void onError(Throwable t) {
                        System.out.println("primalityTest to Guests RPC Error: " + t.getMessage());
                        System.exit(-1);
                    }

                    @Override
                    public void onCompleted() {
                        synchronized (primalityTestLock) {
                            primalityTestCounter++;
                            if (primalityTestCounter == addressBook.length) {
                                primalityTestLock.notify();
                            }
                        }
                    }
                });
            }
            System.out.println("Waiting for primality test complete");
            try {
                primalityTestLock.wait();
            } catch (InterruptedException e) {
                System.out.println("Waiting interrupted: " + e.getMessage());
                System.exit(-3);
            }
            primalityTestWaiting = false;
        }

        BigInteger v = BigInteger.valueOf(1);
        for (int i = 1; i < clusterSize; i++) {
            v = v.multiply(verificationArray[i]);
        }

        return verificationArray[0].equals(v.mod(key.getN()));
    }

    private BigInteger primalityTestGuest(BigInteger g) {
        // Todo: change server 1 every time to do load balancing
        if (id == 1) {
            BigInteger exponent = key.getN().subtract(p).subtract(q).add(BigInteger.valueOf(1));
            return g.modPow(exponent, key.getN());
        }
        return g.modPow(p.add(q), key.getN());
    }

    final Object exchangeGammaLock = new Object();
    int exchangeGammaCounter = 0;
    BigInteger[] gammaArr;
    final Object exchangeGammaSumLock = new Object();
    int exchangeGammaSumCounter = 0;
    BigInteger[] gammaSumArr;

    private void generatePrivateKey() {
        // Todo: change server 1 every time to do load balancing
        BigInteger phi = (id == 1) ?
                key.getN().subtract(p).subtract(q).add(BigInteger.ONE) :
                BigInteger.ZERO.subtract(p).subtract((q));
        BigInteger[] gammaArrLocal = MathUtility.generateRandomSumArray(phi, clusterSize, rnd);
        synchronized (exchangeGammaLock) {
            for (int i = 0; i < clusterSize; i++) {
                sendGamma(i + 1, gammaArrLocal[i]);
            }
            if (exchangeGammaCounter < 2 * clusterSize) {
                try {
                    exchangeGammaLock.wait();
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    System.exit(-1);
                }
            }
            exchangeGammaCounter = 0;
        }
        BigInteger gammaSum = MathUtility.arraySum(gammaArr);
        synchronized (exchangeGammaSumLock) {
            for (int i = 0; i < clusterSize; i++) {
                sendGammaSum(i + 1, gammaSum);
            }
            if (exchangeGammaSumCounter < 2 * clusterSize) {
                try {
                    exchangeGammaSumLock.wait();
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    System.exit(-1);
                }
            }
            exchangeGammaSumCounter = 0;
        }
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

    private void sendGamma(int i, BigInteger gamma) {
        StdRequest request = RpcUtility.Request.newStdRequest(this.id, gamma);
        stubs[i - 1].exchangeGamma(request, new StreamObserver<StdResponse>() {
            @Override
            public void onNext(StdResponse response) {
//                System.out.println("received by " + response.getId());
            }

            @Override
            public void onError(Throwable t) {
                System.out.println("sendGamma RPC error for " + i + " : " + t.getMessage());
                System.exit(-1);
            }

            @Override
            public void onCompleted() {
//                System.out.println("sent!");
                synchronized (exchangeGammaLock) {
                    exchangeGammaCounter++;
                    if (exchangeGammaCounter == 2 * clusterSize) {
                        exchangeGammaLock.notify();
                    }
                }
            }
        });
    }

    private void sendGammaSum(int i, BigInteger gammaSum) {
        StdRequest request = RpcUtility.Request.newStdRequest(this.id, gammaSum);
        stubs[i - 1].exchangeGammaSum(request, new StreamObserver<StdResponse>() {
            @Override
            public void onNext(StdResponse response) {
//                System.out.println("received by " + response.getId());
            }

            @Override
            public void onError(Throwable t) {
                System.out.println("sendGamma RPC error for " + i + " : " + t.getMessage());
                System.exit(-1);
            }

            @Override
            public void onCompleted() {
//                System.out.println("sent!");
                synchronized (exchangeGammaSumLock) {
                    exchangeGammaSumCounter++;
                    if (exchangeGammaSumCounter == 2 * clusterSize) {
                        exchangeGammaSumLock.notify();
                    }
                }
            }
        });
    }

    final Object trialDecryptionLock = new Object();
    boolean trialDecryptionWaiting = false;
    int trialDecryptionCounter = 0;

    private String[] trialDecryption(String encryptedMessage) {
        String[] result = new String[clusterSize];
        synchronized (trialDecryptionLock) {
            trialDecryptionWaiting = true;
            trialDecryptionCounter = 0;
            for (int i = 1; i < clusterSize; i++) {
                stubs[i].decrypt(RpcUtility.Request.newStdRequest(id, encryptedMessage),
                        new StreamObserver<StdResponse>() {
                            @Override
                            public void onNext(StdResponse response) {
                                int j = response.getId() - 1;
                                result[j] = new String(response.getContents().toByteArray());
                            }

                            @Override
                            public void onError(Throwable t) {
                                System.out.println("trial decryption error: " + t.getMessage());
                                System.exit(-1);
                            }

                            @Override
                            public void onCompleted() {
                                synchronized (trialDecryptionLock) {
                                    trialDecryptionCounter++;
                                    if (trialDecryptionCounter == clusterSize) {
                                        trialDecryptionLock.notify();
                                    }
                                }
                            }
                        });
            }
            System.out.println("Waiting for trial decryption to complete");
            try {
                trialDecryptionLock.wait();
            } catch (InterruptedException e) {
                System.out.println("Waiting interrupted: " + e.getMessage());
                System.exit(-4);
            }
            trialDecryptionWaiting = false;
        }
        return result;
    }

}
