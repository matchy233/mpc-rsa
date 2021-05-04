package mpc.project;

import com.google.protobuf.ByteString;
import io.grpc.*;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.math.BigInteger;

public class WorkerMain {
    private Server server;
    private int portNum;
    private int id;
    private BigInteger P;
    private String[] addressBook;
    private WorkerServiceGrpc.WorkerServiceStub[] stubs;
    private ManagerServiceGrpc.ManagerServiceBlockingStub managerStub;
    class WorkerServiceImpl extends WorkerServiceGrpc.WorkerServiceImplBase{
        @Override
        public void formCluster(StdRequest req, StreamObserver<StdResponse> resObserver){
            id = req.getId();
            P = new BigInteger(req.getContents().toByteArray());
            StdResponse res = StdResponse.newBuilder().setId(id).build();
            resObserver.onNext(res);
            resObserver.onCompleted();
            System.out.println("connected to Manager");
        }
        @Override
        public void formNetwork(StdRequest req, StreamObserver<StdResponse> resObserver){
            String midString = new String(req.getContents().toByteArray());
            addressBook = midString.split(";");
            StdResponse res = StdResponse.newBuilder().setId(id).build();
            resObserver.onNext(res);
            resObserver.onCompleted();
            stubs = new WorkerServiceGrpc.WorkerServiceStub[addressBook.length];
            System.out.println("received and parsed addressBook: ");
            for(int i = 0; i < addressBook.length; i++){
                System.out.println(addressBook[i]);
                Channel channel = ManagedChannelBuilder.forTarget(addressBook[i]).usePlaintext().build();
                stubs[i] = WorkerServiceGrpc.newStub(channel);
            }
        }
        @Override
        public void registerManager(StdRequest req, StreamObserver<StdResponse> resObserver){
            String managerUri = new String(req.getContents().toByteArray());
            Channel channel = ManagedChannelBuilder.forTarget(managerUri).usePlaintext().build();
            managerStub = ManagerServiceGrpc.newBlockingStub(channel);
            StdRequest greetingReq = StdRequest.newBuilder().setId(id).build();
            managerStub.greeting(greetingReq);
        }
    }
    public WorkerMain(int portNum){
        this.portNum = portNum;
    }
    public void run(){
        try{
            this.server = ServerBuilder.forPort(portNum)
                    .addService(new WorkerServiceImpl())
                    .build().start();
            System.out.println("Waiting for manager to connect");
            this.server.awaitTermination();
        }catch(Exception e){
            System.out.println(e.getMessage());
            System.exit(-2);
        }finally {
            if(server!=null){
                server.shutdownNow();
            }
        }
    }
}
