package mpc.project.util;

import com.google.protobuf.ByteString;
import mpc.project.PrimalityTestResponse;
import mpc.project.StdRequest;
import mpc.project.StdResponse;
import mpc.project.ExchangePrimespqhRequest;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

public class RpcUtility {

    static public class Request {
        /*
         * General purpose request
         *      Can send int, String or BigInteger
         */
        static public StdRequest newStdRequest(int id, BigInteger bigInt) {
            return StdRequest.newBuilder()
                    .setId(id)
                    .setContents(ByteString.copyFrom(bigInt.toByteArray()))
                    .build();
        }

        static public StdRequest newStdRequest(int id, BigInteger bigInt, long workflowID) {
            return StdRequest.newBuilder()
                    .setId(id)
                    .setContents(ByteString.copyFrom(bigInt.toByteArray()))
                    .setWorkflowID(workflowID)
                    .build();
        }

        static public StdRequest newStdRequest(int id, String s) {
            return StdRequest.newBuilder()
                    .setId(id)
                    .setContents(ByteString.copyFrom(s.getBytes()))
                    .build();
        }

        static public StdRequest newStdRequest(int id, String s, long workflowID) {
            return StdRequest.newBuilder()
                    .setId(id)
                    .setContents(ByteString.copyFrom(s.getBytes()))
                    .setWorkflowID(workflowID)
                    .build();
        }

        static public StdRequest newStdRequest(int id) {
            return StdRequest.newBuilder().setId(id).build();
        }

        static public StdRequest newStdRequest(int id, long workflowID) {
            return StdRequest.newBuilder()
                    .setId(id)
                    .setWorkflowID(workflowID)
                    .build();
        }

        /*
         * Request for generating modulus N
         *    Exchange prime number p, q and h (all BigInteger)
         */
        static public ExchangePrimespqhRequest newExchangePrimesRequest(int id, BigInteger p, BigInteger q, BigInteger h) {
            return ExchangePrimespqhRequest.newBuilder()
                    .setId(id)
                    .setP(ByteString.copyFrom(p.toByteArray()))
                    .setQ(ByteString.copyFrom(q.toByteArray()))
                    .setH(ByteString.copyFrom(h.toByteArray()))
                    .build();
        }

        static public ExchangePrimespqhRequest newExchangePrimesRequest(int id, BigInteger p, BigInteger q, BigInteger h, long workflowID) {
            return ExchangePrimespqhRequest.newBuilder()
                    .setId(id)
                    .setP(ByteString.copyFrom(p.toByteArray()))
                    .setQ(ByteString.copyFrom(q.toByteArray()))
                    .setH(ByteString.copyFrom(h.toByteArray()))
                    .setWorkflowID(workflowID)
                    .build();
        }
    }


    static public class Response {
        /*
         * General purpose response
         */
        static public StdResponse newStdResponse(int id) {
            return StdResponse.newBuilder().setId(id).build();
        }

        static public StdResponse newStdResponse(int id, String s) {
            return StdResponse.newBuilder()
                    .setId(id)
                    .setContents(ByteString.copyFrom(s.getBytes()))
                    .build();
        }

        static public StdResponse newStdResponse(int id, BigInteger n){
            return StdResponse.newBuilder()
                    .setId(id)
                    .setContents(ByteString.copyFrom(n.toByteArray()))
                    .build();
        }

        /*
         * Response for primality test
         *     Send verification numbers
         */
        static public PrimalityTestResponse newPrimalityTestResponse(int id) {
            return PrimalityTestResponse.newBuilder().setId(id).build();
        }

        static public PrimalityTestResponse newPrimalityTestResponse(int id, BigInteger v) {
            PrimalityTestResponse result = PrimalityTestResponse.newBuilder()
                    .setId(id)
                    .setV(ByteString.copyFrom(v.toByteArray()))
                    .build();
            return result;
        }

    }

}
