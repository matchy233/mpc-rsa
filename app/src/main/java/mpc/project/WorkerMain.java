package mpc.project;

import com.google.protobuf.ByteString;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.math.BigInteger;

public class WorkerMain {
    private Server server;
    private int portNum;
    private int id;
    private BigInteger P;
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
