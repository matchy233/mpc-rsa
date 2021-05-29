package mpc.project;

import io.grpc.stub.StreamObserver;
import mpc.project.util.RpcUtility;

public class ManagerRPCReceiverService extends ManagerServiceGrpc.ManagerServiceImplBase {
    @Override
    public void greeting(StdRequest request, StreamObserver<StdResponse> responseObserver) {
        int id = request.getId();
        System.out.println("receive greeting from worker " + id);
        StdResponse response = RpcUtility.Response.newStdResponse(id);
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
