package mpc.project;

import com.google.protobuf.ByteString;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import org.checkerframework.checker.units.qual.A;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Random;
import java.util.Scanner;

public class ManagerMain {
    final int clusterMaxSize = 48;
    final int clusterMinSize = 3;
    final int keyBitLength = 1024;
    private Random rnd;
    private Server server;
    private int portNum;
    private int id;
    private BigInteger P;
    private ArrayList<WorkerServiceGrpc.WorkerServiceStub> stubs;
    private ArrayList<String> addressBook;
    class ManagerServiceImpl extends ManagerServiceGrpc.ManagerServiceImplBase{
        @Override
        public void greeting(StdRequest req, StreamObserver<StdResponse> resObserver){
            int id = req.getId();
            System.out.println("receive greeting from worker "+id);
            StdResponse res = StdResponse.newBuilder().setId(0).build();
            resObserver.onNext(res);
            resObserver.onCompleted();
        }
    }
    private boolean formCluster(String target, int workerId){
        System.out.println("verifying validity of " + target);
        Channel channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
        WorkerServiceGrpc.WorkerServiceBlockingStub testStub = WorkerServiceGrpc.newBlockingStub(channel);
        StdRequest req = StdRequest.newBuilder()
                .setId(workerId)
                .setContents(ByteString.copyFrom(P.toByteArray()))
                .build();
        StdResponse res;
        try{
            res = testStub.formCluster(req);
        }catch(StatusRuntimeException e){
            System.out.println("Failed to add into cluster: "+e.getMessage());
            return false;
        }
        stubs.add(WorkerServiceGrpc.newStub(channel));
        addressBook.add(target);
        System.out.println(target + " is registered successfully");
        return true;
    }
    private boolean formNetwork(){
        StringBuilder midStringBuilder = new StringBuilder();
        for(String target : addressBook){
            midStringBuilder.append(target).append(";");
        }
        String midString = midStringBuilder.toString();
        for(int i = 0; i < addressBook.size(); i++){
            StdRequest req = StdRequest.newBuilder()
                    .setId(i+1).setContents(ByteString.copyFrom(midString.getBytes())).build();
            stubs.get(i).formNetwork(req, new StreamObserver<StdResponse>(){
                @Override
                public void onNext(StdResponse res){
                    System.out.println("received by "+res.getId());
                }
                @Override
                public void onError(Throwable t){
                    System.out.println("RPC error: "+t.getMessage());
                    System.exit(-1);
                }
                @Override
                public void onCompleted(){}
            });
        }
        return true;
    }
    public ManagerMain(int portNum){
        this.portNum = portNum;
        this.rnd = new Random();
        this.P = BigInteger.probablePrime(keyBitLength, rnd);
        System.out.println("please enter the address:port of all workers");
        System.out.println("one in each line, \"end\" marks the end");
        Scanner input = new Scanner(System.in);
        stubs = new ArrayList<WorkerServiceGrpc.WorkerServiceStub>();
        addressBook = new ArrayList<String>();
        for(int i = 1; i < clusterMaxSize; i++){
            String inLine = input.nextLine();
            if(inLine.equals("end")){
                if(stubs.size() >= clusterMinSize){
                    break;
                }else{
                    System.out.println("Too few workers to form a cluster");
                    System.out.println("Current number of workers: "+stubs.size());
                    System.out.println("Minimum number of workers: "+clusterMinSize);
                    i--; continue;
                }
            }
            if(!formCluster(inLine, i)){
                i--;
            }
        }
        formNetwork();
        input.nextLine();
    }
    public void run(){

    }
}
