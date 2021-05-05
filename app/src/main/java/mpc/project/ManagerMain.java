package mpc.project;

import com.google.protobuf.ByteString;
import io.grpc.*;
import io.grpc.stub.StreamObserver;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Random;
import java.util.Scanner;

public class ManagerMain {
    final int clusterMaxSize = 48;
    final int clusterMinSize = 3;
    final int keyBitLength = 1024;
    private int clusterSize;
    private Random rnd;
    private Server server;
    private int portNum;
    private int id;
    private BigInteger P;
    private ArrayList<WorkerServiceGrpc.WorkerServiceStub> stubs;
    private ArrayList<String> addressBook;
    private String selfAddress;

    class ManagerServiceImpl extends ManagerServiceGrpc.ManagerServiceImplBase {
        @Override
        public void greeting(StdRequest req, StreamObserver<StdResponse> resObserver) {
            int id = req.getId();
            System.out.println("receive greeting from worker " + id);
            StdResponse res = newRes();
            resObserver.onNext(res);
            resObserver.onCompleted();
        }
    }

    private StdRequest newReq(int id, BigInteger bigInt) {
        StdRequest result = StdRequest.newBuilder()
                .setId(id).setContents(ByteString.copyFrom(
                        bigInt.toByteArray()
                )).build();
        return result;
    }

    private StdRequest newReq(int id, String s) {
        StdRequest result = StdRequest.newBuilder()
                .setId(id).setContents(ByteString.copyFrom(
                        s.getBytes()
                )).build();
        return result;
    }

    private StdRequest newReq(int id) {
        return StdRequest.newBuilder().setId(id).build();
    }

    private StdResponse newRes() {
        StdResponse result = StdResponse.newBuilder().setId(id).build();
        return result;
    }

    private boolean addClusterNode(String target, int workerId) {
        System.out.println("verifying validity of " + target);
        Channel channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
        WorkerServiceGrpc.WorkerServiceBlockingStub testStub = WorkerServiceGrpc.newBlockingStub(channel);
        StdRequest formClusterReq = newReq(workerId, P);
        StdRequest registerManagerReq = newReq(workerId, selfAddress);
        try {
            testStub.registerManager(registerManagerReq);
            testStub.formCluster(formClusterReq);
        } catch (StatusRuntimeException e) {
            System.out.println("Failed to add into cluster: " + e.getMessage());
            return false;
        }
        stubs.add(WorkerServiceGrpc.newStub(channel));
        addressBook.add(target);
        System.out.println(target + " is registered successfully");
        return true;
    }

    private void formCluster() {
        Scanner input = new Scanner(System.in);
        System.out.println("please enter the address you want workers to use to connect to you");
        // will not check validity, be careful
        selfAddress = input.nextLine();
        System.out.println("please enter the address:port of all workers");
        System.out.println("one in each line, \"end\" marks the end");
        stubs = new ArrayList<WorkerServiceGrpc.WorkerServiceStub>();
        addressBook = new ArrayList<String>();
        for (int i = 1; i < clusterMaxSize; i++) {
            String inLine = input.nextLine();
            if (inLine.equals("end")) {
                if (stubs.size() >= clusterMinSize) {
                    break;
                } else {
                    System.out.println("Too few workers to form a cluster");
                    System.out.println("Current number of workers: " + stubs.size());
                    System.out.println("Minimum number of workers: " + clusterMinSize);
                    i--;
                    continue;
                }
            }
            if (!addClusterNode(inLine, i)) {
                i--;
            }
        }
        clusterSize = addressBook.size();
    }

    Integer formNetworkCounter = 0;
    final Object formNetworkLock = new Object();
    boolean formNetworkCounterWaiting;

    private boolean formNetwork() {
        StringBuilder midStringBuilder = new StringBuilder();
        for (String target : addressBook) {
            midStringBuilder.append(target).append(";");
        }
        String midString = midStringBuilder.toString();
        synchronized (formNetworkLock) {
            formNetworkCounterWaiting = true;
            formNetworkCounter = 0;
            for (int i = 0; i < addressBook.size(); i++) {
                StdRequest req = newReq(i + 1, midString);
                stubs.get(i).formNetwork(req, new StreamObserver<StdResponse>() {
                    @Override
                    public void onNext(StdResponse res) {
                        System.out.println("received by " + res.getId());
                    }

                    @Override
                    public void onError(Throwable t) {
                        System.out.println("RPC error: " + t.getMessage());
                        System.exit(-1);
                    }

                    @Override
                    public void onCompleted() {
                        if (formNetworkCounterWaiting) {
                            synchronized (formNetworkLock) {
                                formNetworkCounter++;
                                if (formNetworkCounter == clusterSize) {
                                    formNetworkLock.notify();
                                }
                            }
                        }
                    }
                });
            }
            System.out.println("Waiting for network forming to complete");
            try {
                formNetworkLock.wait();

            } catch (InterruptedException e) {
                System.out.println("Waiting interrupted: " + e.getMessage());
                System.exit(-3);
            }
            formNetworkCounterWaiting = false;
        }
        System.out.println("Network formation successfully");
        return true;
    }

    public ManagerMain(int portNum) {
        this.portNum = portNum;
        this.rnd = new Random();
        this.P = BigInteger.probablePrime(keyBitLength, rnd);
        try {
            this.server = ServerBuilder.forPort(portNum)
                    .addService(new ManagerServiceImpl())
                    .build().start();
            System.out.println("Manager server started");
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.exit(-2);
        }
    }

    public void run() {
        formCluster();
        formNetwork();
        Scanner s = new Scanner(System.in);
        s.nextLine();
    }
}
