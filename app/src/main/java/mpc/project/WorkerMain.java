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
            // id is used for bitNum now, not id
            bitNum = request.getId();
            randomPrime = new BigInteger(request.getContents().toByteArray());
            p = genRandBig(bitNum, randomPrime, rnd);
            q = genRandBig(bitNum, randomPrime, rnd);
            WorkerMain.this.generateKeyPiece();
            responseObserver.onNext(RpcUtility.newStdResponse(id));
            responseObserver.onCompleted();
        }

        @Override
        public void sendPrimesPQH(SendPrimespqhRequest request, StreamObserver<StdResponse> responseObserver) {
            synchronized (exchangePrimesLock) {
                int i = request.getId() - 1;
                pArr[i] = new BigInteger(request.getP().toByteArray());
                qArr[i] = new BigInteger(request.getQ().toByteArray());
                hArr[i] = new BigInteger(request.getH().toByteArray());
                responseObserver.onNext(RpcUtility.newStdResponse(id));
                responseObserver.onCompleted();
                exchangePrimesWorkersCounter++;
                if (exchangePrimesWorkersCounter == clusterSize) {
                    exchangePrimesLock.notify();
                }
            }
        }

        @Override
        synchronized public void sendNPiece(StdRequest request, StreamObserver<StdResponse> responseObserver) {
            synchronized (exchangeNPiecesLock) {
                int i = request.getId() - 1;
                nPieceArr[i] = new BigInteger(request.getContents().toByteArray());
                responseObserver.onNext(RpcUtility.newStdResponse(id));
                responseObserver.onCompleted();
                exchangeNPiecesWorkersCounter++;
                if (exchangeNPiecesWorkersCounter == clusterSize) {
                    exchangeNPiecesLock.notify();
                }
            }
        }

        @Override
        public void primalityTest(StdRequest req, StreamObserver<StdResponse> responseObserver) {
            if (id == 1) {
                boolean passPrimalityTest = primalityTestHost();
                StdResponse response;
                if (passPrimalityTest) {
                    response = RpcUtility.newStdResponse(1);
                } else {
                    response = RpcUtility.newStdResponse(0);
                }
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            } else {
                responseObserver.onNext(RpcUtility.newStdResponse(id));
                responseObserver.onCompleted();
                primalityTestGuest();
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
                System.out.println("RPC error: " + t.getMessage());
                System.exit(-1);
            }

            @Override
            public void onCompleted() {
                System.out.println("sent!");
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
                System.out.println("RPC error: " + t.getMessage());
                System.exit(-1);
            }

            @Override
            public void onCompleted() {
                System.out.println("sent!");
            }
        });
    }

    private void genNPiece() {
        synchronized (exchangeNPiecesLock) {
            BigInteger nPiece = arraySum(pArr).multiply(arraySum(qArr)).add(arraySum(hArr)).mod(randomPrime);
            for (int i = 1; i <= clusterSize; i++) {
                sendNPiece(i, nPiece);
            }
            exchangeNPiecesWorkersCounter++;
            if(exchangeNPiecesWorkersCounter < clusterSize){
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
        this.N = N.toBigInteger();
        System.out.println("The modular is :" + N);
    }


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

    private boolean primalityTestHost() {
        return false;
    }

    private void primalityTestGuest() {

    }
}
