package mpc.project;

import io.grpc.*;
import io.grpc.stub.StreamObserver;

import java.math.BigInteger;
import java.util.Random;

import mpc.project.util.RpcUtility;

public class WorkerMain {
    private Server server;
    private int portNum;
    private int id;
    private Random rnd;
    private BigInteger P;
    private int bitNum;
    private String[] addressBook;
    private int clusterSize;
    private WorkerServiceGrpc.WorkerServiceStub[] stubs;
    private ManagerServiceGrpc.ManagerServiceBlockingStub managerStub;

    class WorkerServiceImpl extends WorkerServiceGrpc.WorkerServiceImplBase {
        @Override
        public void formCluster(StdRequest req, StreamObserver<StdResponse> responseObserver) {
            id = req.getId();
            P = new BigInteger(req.getContents().toByteArray());
            StdResponse res = RpcUtility.newResponse(id);
            responseObserver.onNext(res);
            responseObserver.onCompleted();
            System.out.println("connected to Manager");
        }

        @Override
        public void formNetwork(StdRequest req, StreamObserver<StdResponse> responseObserver) {
            String midString = new String(req.getContents().toByteArray());
            addressBook = midString.split(";");
            StdResponse response = RpcUtility.newResponse(id);
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
        }

        @Override
        public void registerManager(StdRequest req, StreamObserver<StdResponse> responseObserver) {
            id = req.getId();
            String managerUri = new String(req.getContents().toByteArray());
            Channel channel = ManagedChannelBuilder.forTarget(managerUri).usePlaintext().build();
            managerStub = ManagerServiceGrpc.newBlockingStub(channel);
            StdRequest greetingReq = RpcUtility.newRequest(id);
            managerStub.greeting(greetingReq);
            responseObserver.onNext(RpcUtility.newResponse(id));
            responseObserver.onCompleted();
            System.out.println("registered manager at " + managerUri);
        }

        @Override
        public void generateKeyPiece(StdRequest req, StreamObserver<StdResponse> responseObserver){
            // id is used for bitNum now, not id
            bitNum = req.getId();
            P = new BigInteger(req.getContents().toByteArray());
            p = genRandBig(bitNum, P, rnd);
            q = genRandBig(bitNum, P, rnd);
            BigInteger keyPiece = WorkerMain.this.generateKeyPiece();
            responseObserver.onNext(RpcUtility.newResponse(id));
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

    BigInteger p;
    BigInteger q;
    BigInteger[] pArr;
    BigInteger[] qArr;
    BigInteger[] hArr;
    private BigInteger[] polyF;
    private BigInteger[] polyG;
    private BigInteger[] polyH;
    private BigInteger generateKeyPiece(){
        generateFGH();
        return null;
    }
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
            exchangepqh(pArr_tmp[i - 1], qArr_tmp[i - 1], hArr_tmp[i - 1], addressBook[i - 1], id);
        }
        pArr[id - 1] = pArr_tmp[id - 1];
        qArr[id - 1] = qArr_tmp[id - 1];
        hArr[id - 1] = hArr_tmp[id - 1];
        for (int i = id + 1; i < clusterSize; i++) {
            exchangepqh(pArr_tmp[i - 1], qArr_tmp[i - 1], hArr_tmp[i - 1], addressBook[i - 1], id);
        }
        // wait to receive all p q h
        // genNPiece();
    }

    private void exchangepqh(BigInteger p, BigInteger q, BigInteger h, String tgtUri, int senderId) {
    }
}
