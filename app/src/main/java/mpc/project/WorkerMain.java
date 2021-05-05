package mpc.project;

import com.google.protobuf.ByteString;
import io.grpc.*;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Random;

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
        public void formCluster(StdRequest req, StreamObserver<StdResponse> resObserver) {
            id = req.getId();
            P = new BigInteger(req.getContents().toByteArray());
            StdResponse res = newRes();
            resObserver.onNext(res);
            resObserver.onCompleted();
            System.out.println("connected to Manager");
        }

        @Override
        public void formNetwork(StdRequest req, StreamObserver<StdResponse> resObserver) {
            String midString = new String(req.getContents().toByteArray());
            addressBook = midString.split(";");
            StdResponse res = newRes();
            stubs = new WorkerServiceGrpc.WorkerServiceStub[addressBook.length];
            System.out.println("received and parsed addressBook: ");
            for (int i = 0; i < addressBook.length; i++) {
                System.out.println(addressBook[i]);
                Channel channel = ManagedChannelBuilder.forTarget(addressBook[i]).usePlaintext().build();
                stubs[i] = WorkerServiceGrpc.newStub(channel);
            }
            resObserver.onNext(res);
            resObserver.onCompleted();
        }

        @Override
        public void registerManager(StdRequest req, StreamObserver<StdResponse> resObserver) {
            String managerUri = new String(req.getContents().toByteArray());
            Channel channel = ManagedChannelBuilder.forTarget(managerUri).usePlaintext().build();
            managerStub = ManagerServiceGrpc.newBlockingStub(channel);
            StdRequest greetingReq = newReq();
            managerStub.greeting(greetingReq);
        }
    }

    private StdRequest newReq(BigInteger bigInt) {
        StdRequest result = StdRequest.newBuilder()
                .setId(id).setContents(ByteString.copyFrom(
                        bigInt.toByteArray()
                )).build();
        return result;
    }

    private StdRequest newReq(String s) {
        StdRequest result = StdRequest.newBuilder()
                .setId(id).setContents(ByteString.copyFrom(
                        s.getBytes()
                )).build();
        return result;
    }

    private StdRequest newReq() {
        return StdRequest.newBuilder().setId(id).build();
    }

    private StdResponse newRes() {
        StdResponse result = StdResponse.newBuilder().setId(id).build();
        return result;
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
