package mpc.project.util;

import com.google.protobuf.ByteString;
import mpc.project.PrimalityTestResponse;
import mpc.project.StdRequest;
import mpc.project.StdResponse;
import mpc.project.SendPrimespqhRequest;

import java.math.BigInteger;

public class RpcUtility {
    static public StdRequest newStdRequest(int id, BigInteger bigInt) {
        StdRequest result = StdRequest.newBuilder()
                .setId(id)
                .setContents(ByteString.copyFrom(bigInt.toByteArray()))
                .build();
        return result;
    }

    static public StdRequest newStdRequest(int id, String s) {
        StdRequest result = StdRequest.newBuilder()
                .setId(id).setContents(ByteString.copyFrom(
                        s.getBytes()
                )).build();
        return result;
    }

    static public StdRequest newStdRequest(int id) {
        return StdRequest.newBuilder().setId(id).build();
    }

    static public SendPrimespqhRequest newSendPrimesRequest(int id, BigInteger p, BigInteger q, BigInteger h) {
        SendPrimespqhRequest result = SendPrimespqhRequest.newBuilder()
                .setId(id)
                .setP(ByteString.copyFrom(p.toByteArray()))
                .setQ(ByteString.copyFrom(q.toByteArray()))
                .setH(ByteString.copyFrom(h.toByteArray()))
                .build();
        return result;
    }

    static public StdResponse newStdResponse(int id) {
        return StdResponse.newBuilder().setId(id).build();
    }

    static public PrimalityTestResponse newPrimalityTestResponse(int id, BigInteger v) {
        PrimalityTestResponse result= PrimalityTestResponse.newBuilder()
                .setId(id)
                .setV(ByteString.copyFrom(v.toByteArray()))
                .build();
        return result;
    }

    static public PrimalityTestResponse newPrimalityTestResponse(int id) {
        return PrimalityTestResponse.newBuilder().setId(id).build();
    }
}
