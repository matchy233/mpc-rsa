package mpc.project;

import io.grpc.*;
import io.grpc.stub.StreamObserver;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Random;

import mpc.project.util.RpcUtility;

public class WorkerMain {
    private Server server;
    private int portNum;
    private int id;
    private Random rnd;
    private int bitNum;
    private String[] addressBook;
    private int clusterSize;
    private WorkerServiceGrpc.WorkerServiceStub[] stubs;
    private ManagerServiceGrpc.ManagerServiceBlockingStub managerStub;

    // Variables necessary for distributed RSA keypair generation
    private BigInteger randomPrime;
    private BigInteger p;
    private BigInteger q;
    BigInteger[] pArr;          // An array holding p_i ( i \in [1, clusterNum])
    BigInteger[] qArr;          // An array holding q_i ( i \in [1, clusterNum])
    BigInteger[] hArr;          // An array holding h_i ( i \in [1, clusterNum])
    private BigInteger[] polyF;
    private BigInteger[] polyG;
    private BigInteger[] polyH;
    private BigInteger N;       // modular
    private BigInteger[] nPieceArr;

    // For synchronization between workers
    // Todo: find a more elegant way to implement synchronization
    private final Object exchangePrimesLock = new Object();
    private int exchangePrimesWorkersCounter = 0;
    private boolean exchangePrimesWaiting = false;

    private final Object exchangeNPiecesLock = new Object();
    private int exchangeNPiecesWorkersCounter = 0;
    private boolean exchangeNPiecesWaiting = false;

    private final Object moduloGenerationLock = new Object();

    class WorkerServiceImpl extends WorkerServiceGrpc.WorkerServiceImplBase {
        @Override
        public void formCluster(StdRequest request, StreamObserver<StdResponse> responseObserver) {
//            id = request.getId();
//            randomPrime = new BigInteger(request.getContents().toByteArray());
            StdResponse res = RpcUtility.newStdResponse(id);
            responseObserver.onNext(res);
            responseObserver.onCompleted();
            System.out.println("connected to Manager");
        }

        @Override
        public void formNetwork(StdRequest request, StreamObserver<StdResponse> responseObserver) {
            String midString = new String(request.getContents().toByteArray());
            addressBook = midString.split(";");
            StdResponse response = RpcUtility.newStdResponse(id);
            stubs = new WorkerServiceGrpc.WorkerServiceStub[addressBook.length];
            System.out.println("received and parsed addressBook: ");
            for (int i = 0; i < addressBook.length; i++) {
                System.out.println(addressBook[i]);
                Channel channel = ManagedChannelBuilder.forTarget(addressBook[i]).usePlaintext().build();
                stubs[i] = WorkerServiceGrpc.newStub(channel);
            }
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            clusterSize = addressBook.length;
            pArr = new BigInteger[clusterSize];
            qArr = new BigInteger[clusterSize];
            hArr = new BigInteger[clusterSize];
            nPieceArr = new BigInteger[clusterSize];
        }

        @Override
        public void registerManager(StdRequest request, StreamObserver<StdResponse> responseObserver) {
            id = request.getId();
            String managerUri = new String(request.getContents().toByteArray());
            Channel channel = ManagedChannelBuilder.forTarget(managerUri).usePlaintext().build();
            managerStub = ManagerServiceGrpc.newBlockingStub(channel);
            StdRequest greetingReq = RpcUtility.newStdRequest(id);
            managerStub.greeting(greetingReq);
            responseObserver.onNext(RpcUtility.newStdResponse(id));
            responseObserver.onCompleted();
            System.out.println("registered manager at " + managerUri);
        }

        @Override
        public void generateKeyPiece(StdRequest request, StreamObserver<StdResponse> responseObserver) {
            synchronized (moduloGenerationLock) {
                // id is used for bitNum now, not id
                bitNum = request.getId();
                randomPrime = new BigInteger(request.getContents().toByteArray());
                p = genRandBig(bitNum, randomPrime, rnd);
                q = genRandBig(bitNum, randomPrime, rnd);
                WorkerMain.this.generateKeyPiece();
                responseObserver.onNext(RpcUtility.newStdResponse(id));
                responseObserver.onCompleted();
            }
        }

        @Override
        public void sendPrimesPQH(SendPrimespqhRequest request, StreamObserver<StdResponse> responseObserver) {
            synchronized (exchangePrimesLock) {
                int i = request.getId() - 1;
                System.out.println("receiving " + i + " prime p q h");
                pArr[i] = new BigInteger(request.getP().toByteArray());
                qArr[i] = new BigInteger(request.getQ().toByteArray());
                hArr[i] = new BigInteger(request.getH().toByteArray());
                responseObserver.onNext(RpcUtility.newStdResponse(id));
                responseObserver.onCompleted();
                exchangePrimesWorkersCounter++;
                if (exchangePrimesWorkersCounter == 2 * clusterSize) {
                    exchangePrimesLock.notify();
                }
            }
        }

        @Override
        synchronized public void sendNPiece(StdRequest request, StreamObserver<StdResponse> responseObserver) {
            synchronized (exchangeNPiecesLock) {
                int i = request.getId() - 1;
                System.out.println("receiving " + i + " N piece");
                nPieceArr[i] = new BigInteger(request.getContents().toByteArray());
                responseObserver.onNext(RpcUtility.newStdResponse(id));
                responseObserver.onCompleted();
                exchangeNPiecesWorkersCounter++;
                if (exchangeNPiecesWorkersCounter == 2 * clusterSize) {
                    exchangeNPiecesLock.notify();
                }
            }
        }

        @Override
        public void primalityTest(StdRequest request, StreamObserver<PrimalityTestResponse> responseObserver) {
            if (id == 1) {
                boolean passPrimalityTest = primalityTestHost();
                PrimalityTestResponse response;
                if (passPrimalityTest) {
                    response = RpcUtility.newPrimalityTestResponse(1);
                } else {
                    response = RpcUtility.newPrimalityTestResponse(0);
                }
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            } else {
                BigInteger result = primalityTestGuest(new BigInteger(request.getContents().toByteArray()));
                responseObserver.onNext(RpcUtility.newPrimalityTestResponse(id, result));
                responseObserver.onCompleted();
            }
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

    private BigInteger genRandBig(int bitNum, BigInteger lessThanThis, Random rnd) {
        BigInteger result = BigInteger.valueOf(0);
        do {
            result = BigInteger.probablePrime(bitNum, rnd);
        } while (result.compareTo(lessThanThis) >= 0);
        return result;
    }

    private BigInteger polynomialResult(BigInteger[] poly, BigInteger input) {
        BigInteger result = BigInteger.valueOf(0);
        for (int i = 0; i < poly.length; i++) {
            // Computes a_i \times x^i
            result = result.add(poly[i].multiply(input.pow(i)));
        }
        return result;
    }

    private BigInteger generateKeyPiece() {
        generateFGH();
        return null;
    }

    private void generateFGH() {
        synchronized (exchangePrimesLock) {
            int l = (clusterSize - 1) / 2;

            polyF = new BigInteger[l];
            polyF[0] = p;
            for (int i = 1; i < polyF.length; i++) {
                polyF[i] = genRandBig(bitNum, randomPrime, rnd);
            }

            polyG = new BigInteger[l];
            polyG[0] = q;
            for (int i = 1; i < polyG.length; i++) {
                polyG[i] = genRandBig(bitNum, randomPrime, rnd);
            }

            polyH = new BigInteger[2 * l];
            polyH[0] = BigInteger.valueOf(0);
            for (int i = 1; i < polyH.length; i++) {
                polyH[i] = genRandBig(bitNum, randomPrime, rnd);
            }

//        pArr = new BigInteger[clusterSize];
            BigInteger[] pArr_tmp = new BigInteger[clusterSize];
            for (int i = 0; i < clusterSize; i++) {
                pArr_tmp[i] = polynomialResult(polyF, BigInteger.valueOf(i + 1));
            }

//        qArr = new BigInteger[clusterSize];
            BigInteger[] qArr_tmp = new BigInteger[clusterSize];
            for (int i = 0; i < clusterSize; i++) {
                qArr_tmp[i] = polynomialResult(polyG, BigInteger.valueOf(i + 1));
            }

//        hArr = new BigInteger[clusterSize];
            BigInteger[] hArr_tmp = new BigInteger[clusterSize];
            for (int i = 0; i < clusterSize; i++) {
                hArr_tmp[i] = polynomialResult(polyH, BigInteger.valueOf(i + 1));
            }

            // Wait to receive all p q h
            for (int i = 1; i <= clusterSize; i++) {
                exchangePQH(i, pArr_tmp[i - 1], qArr_tmp[i - 1], hArr_tmp[i - 1]);
            }
            if (exchangePrimesWorkersCounter < clusterSize) {
                try {
                    exchangePrimesLock.wait();
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    System.exit(-1);
                }
            }
        }
        exchangePrimesWorkersCounter = 0;
        genNPiece();
    }

    private void exchangePQH(int i, BigInteger p, BigInteger q, BigInteger h) {
        SendPrimespqhRequest request = RpcUtility.newSendPrimesRequest(this.id, p, q, h);
        stubs[i - 1].sendPrimesPQH(request, new StreamObserver<>() {
            @Override
            public void onNext(StdResponse response) {
                System.out.println("received by " + response.getId());
            }

            @Override
            public void onError(Throwable t) {
                System.out.println("exchangePQH RPC error for " + i + " : " + t.getMessage());
                System.exit(-1);
            }

            @Override
            public void onCompleted() {
                System.out.println("sent!");
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
        StdRequest request = RpcUtility.newStdRequest(this.id, nPiece);
        stubs[i - 1].sendNPiece(request, new StreamObserver<StdResponse>() {
            @Override
            public void onNext(StdResponse response) {
                System.out.println("received by " + response.getId());
            }

            @Override
            public void onError(Throwable t) {
                System.out.println("sendNPiece RPC error for " + i + " : " + t.getMessage());
                System.exit(-1);
            }

            @Override
            public void onCompleted() {
                System.out.println("sent!");
                synchronized (exchangeNPiecesLock) {
                    exchangeNPiecesWorkersCounter++;
                    if (exchangeNPiecesWorkersCounter == 2 * clusterSize) {
                        exchangeNPiecesLock.notify();
                    }
                }
            }
        });
    }

    private void genNPiece() {
        synchronized (exchangeNPiecesLock) {
            BigInteger nPiece = (arraySum(pArr).mod(randomPrime)
                            .multiply(arraySum(qArr).mod(randomPrime))).mod(randomPrime)
                            .add(arraySum(hArr).mod(randomPrime))
                            .mod(randomPrime);
            for (int i = 1; i <= clusterSize; i++) {
                sendNPiece(i, nPiece);
            }
            if (exchangeNPiecesWorkersCounter < clusterSize) {
                try {
                    exchangeNPiecesLock.wait();
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    System.exit(-1);
                }
            }
        }
        exchangeNPiecesWorkersCounter = 0;
        genN();
    }


    private void genN() {
        double[] values = computeValuesOfLagrangianPolynomialsAtZero();
        BigDecimal N = new BigDecimal(0);
        for (int i = 0; i < nPieceArr.length; i++) {
            BigDecimal Ni = new BigDecimal(nPieceArr[i]);
            N = N.add(Ni.multiply(BigDecimal.valueOf(values[i])));
        }
        this.N = N.toBigInteger().mod(randomPrime);
        System.out.println("The modular is :" + this.N);
    }
:

    // Util methods

    private BigInteger arraySum(BigInteger[] array) {
        BigInteger result = BigInteger.valueOf(0);
        for (BigInteger element : array) {
            result = result.add(element);
        }
        return result;
    }

    private double[] computeValuesOfLagrangianPolynomialsAtZero() {
        int len = nPieceArr.length;
        double[] results = new double[len];

        for (int i = 0; i < len; i++) {
            int xi = i + 1;
            int numerator = 1;
            int denominator = 1;
            for (int j = 0; j < i; j++) {
                numerator *= -(j + 1);
                denominator *= (xi - (j + 1));
            }
            for (int j = i + 1; j < len; j++) {
                numerator *= -(j + 1);
                denominator *= (xi - (j + 1));
            }
            results[i] = (double) numerator / (double) denominator;
        }

        return results;
    }

    final Object primalityTestLock = new Object();
    int primalityTestCounter = 0;
    boolean primalityTestWaiting = false;

    private boolean primalityTestHost() {
        BigInteger g = genRandBig(bitNum, N, rnd);
        BigInteger[] verificationArray = new BigInteger[this.clusterSize - 1];
        BigInteger exponent = N.subtract(p).subtract(q).add(BigInteger.valueOf(1));
        verificationArray[0] = g.modPow(exponent, N);

        synchronized (primalityTestLock) {
            primalityTestWaiting = true;
            primalityTestCounter = 0;
            for (int i = 1; i < addressBook.length; i++) {
                StdRequest request = RpcUtility.newStdRequest(id, g);
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
                        primalityTestCounter++;
                        if (primalityTestCounter == addressBook.length) {
                            primalityTestLock.notify();
                        }
                    }
                });

                System.out.println("Waiting for key generation complete");
                try {
                    primalityTestLock.wait();
                } catch (InterruptedException e) {
                    System.out.println("Waiting interrupted: " + e.getMessage());
                    System.exit(-3);
                }
                primalityTestWaiting = false;
            }
        }

        BigInteger v = BigInteger.valueOf(1);
        for (int i = 1; i < clusterSize; i++) {
            v = v.mod(N).multiply(verificationArray[i].mod(N)).mod(N);
        }

        return verificationArray[0].equals(v);
    }

    private BigInteger primalityTestGuest(BigInteger g) {
        return g.modPow(p.add(q), N);
    }
}
