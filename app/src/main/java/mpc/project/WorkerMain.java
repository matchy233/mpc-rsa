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
    private String[] addressBook;
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
}
