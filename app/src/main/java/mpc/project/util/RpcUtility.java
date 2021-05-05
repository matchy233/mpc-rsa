package mpc.project.util;

import com.google.protobuf.ByteString;
import mpc.project.StdRequest;
import mpc.project.StdResponse;

import java.math.BigInteger;

public class RpcUtility {
    static public StdRequest newRequest(int id, BigInteger bigInt) {
        StdRequest result = StdRequest.newBuilder()
                .setId(id).setContents(ByteString.copyFrom(
                        bigInt.toByteArray()
                )).build();
        return result;
    }

    static public StdRequest newRequest(int id, String s) {
        StdRequest result = StdRequest.newBuilder()
                .setId(id).setContents(ByteString.copyFrom(
                        s.getBytes()
                )).build();
        return result;
    }

    static public StdRequest newRequest(int id) {
        return StdRequest.newBuilder().setId(id).build();
    }

    static public StdResponse newResponse(int id) {
        StdResponse result = StdResponse.newBuilder().setId(id).build();
        return result;
    }
}
