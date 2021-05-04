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
    final int keyBitLength = 1024;
    private Random rnd;
    private Server server;
    private int portNum;
    private int id;
    private BigInteger P;
    ArrayList<WorkerServiceGrpc.WorkerServiceStub> stubs;
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
        System.out.println(target + " is registered successfully");
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
        for(int i = 1; i < clusterMaxSize; i++){
            String inLine = input.nextLine();
            if(inLine.equals("end")){
                break;
            }
            if(!formCluster(inLine, i)){
                i--;
            }
        }
    }
    public void run(){

    }
}
