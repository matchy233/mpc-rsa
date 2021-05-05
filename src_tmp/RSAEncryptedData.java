package com.raas;

import java.util.Arrays;
import java.math.BigInteger;
import java.util.Base64;

public class RSAEncryptedData {
    /** Todo:
     *    Implement encryption and decryption methods
     **/
//    private final int blockSize = 8

    public RSAEncryptedData() {}
    public RSAEncryptedData(String string, Key key){
    }

    public String encrypt(String string, Key key) {
        byte[] byteString = string.getBytes();
        BigInteger b = new BigInteger(byteString);
        b = b.modPow(key.e, key.N);
        byte[] decrypted = b.toByteArray();
        // Base64 encoding
        Base64.Encoder encoder = Base64.getEncoder();
        return new String(encoder.encode(decrypted));
    }

    public String decrypt(String string, Key key) {
        // Base64 decoding
        Base64.Decoder decoder = Base64.getDecoder();
        byte[] byteString = decoder.decode(string.getBytes());

        BigInteger b = new BigInteger(byteString);
        b = b.modPow(key.d, key.N);
        byte[] decrypted = b.toByteArray();
        return new String(decrypted);

//        for(int i = 0; i < string.length(); i += blockSize) {
//            byte[] block = Arrays.copyOfRange(byteString, i, i + blockSize);
//            BigInteger b = new BigInteger(block);
//            b = BigInteger.ModPow(b, key.d, key.N);
//
//            byte[] decrypted = b.toByteArray(isUnsigned=true);
//            System.arraycopy(decrypted, 0, byteString, blockSize - decrypted.length(), decrypted.length());
//            Arrays.fill(byteString, i, blockSize - decrypted.length(), 0);
//
//            return new String(byteString)
//        }
    }
}
