package com.raas;

import java.util.Arrays;
import java.math.BigInteger;
import java.util.Base64;

public class RSAEncryptedData {
    /** Todo:
     *    Implement encryption and decryption methods
     **/

    /** Encryption: Encrypt block by block( blockSize bytes -> encBlockSize bytes )
     *   Decryption: Decrypt block by block( encBlockSize bytes -> blockSize bytes )
     **/
    private final int blockSize = 1; /** blockSize * 8 should be under log_2(N) **/
    private final int encBlockSize = 2; /** endBlockSize * 8 should be over log_2(N) **/
    private final int base64BlockSize = (encBlockSize + 2) / 3 * 4; /** considering Base64 padding **/

    public RSAEncryptedData() {}
    public RSAEncryptedData(String string, Key key){
    }

    public String encrypt(String string, Key key) {
        // Encoder for Base64 encoding
        Base64.Encoder encoder = Base64.getEncoder();

        String result = "";
        byte[] byteString = string.getBytes();
        // block-by encoding
        for(int i = 0; i < byteString.length; i += blockSize) {
            byte[] block = Arrays.copyOfRange(byteString, i, Math.min(byteString.length, i + blockSize));
            BigInteger b = new BigInteger(block);

            // RSA calculation
            b = b.modPow(key.e, key.N);
            byte[] bByte = b.toByteArray();

            byte[] encBlock = new byte[encBlockSize];
            System.arraycopy(b.toByteArray(), 0, encBlock, encBlockSize - bByte.length, bByte.length);
            result += encoder.encodeToString(encBlock);
        }

        return result;
    }

    /**
     * @param dist : using for distributed Decryption
     *             if true, calculate (m^di Mod N) and base64 encoding to send to manager.
     *             if false, calculate (m^d Mod N). The result is original message.
     *             This is for testing
     */
    public String decrypt(String string, Key key, boolean dist) {
        // Encoder and Decoder for Base64 decoding
        Base64.Decoder decoder = Base64.getDecoder();
        Base64.Encoder encoder = Base64.getEncoder();

        String result = "";
        byte[] byteString = string.getBytes();
        // block-by decoding
        for(int i = 0; i < byteString.length; i += base64BlockSize) {
            byte[] block = Arrays.copyOfRange(byteString, i, i + base64BlockSize);
            block = decoder.decode(block);
            BigInteger b = new BigInteger(block);

            // RSA calculation
            b = b.modPow(key.d, key.N);
            byte[] bByte = b.toByteArray();

            if(dist) {
                /** calculate (m^di Mod N) and base64 encoding to send to manager **/
                byte[] encBlock = new byte[encBlockSize];
                System.arraycopy(b.toByteArray(), 0, encBlock, encBlockSize - bByte.length, bByte.length);
                result += encoder.encodeToString(encBlock);
            } else {
                /** simple RSA decryption **/
                result += new String(bByte);
            }
        }

        return result;
    }

    /**
     * @param strings: decrypted messages with each di
     *               strings should have same length
     * @param key: any Key. just used for mod N calculation
     **/
    public String distributedDecrypt(String[] strings, Key key) {
        // Decoder for Base64 decoding
        Base64.Decoder decoder = Base64.getDecoder();

        byte[][] byteStrings = new byte[strings.length][];
        for(int i = 0; i < strings.length; ++i) {
            byteStrings[i] = strings[i].getBytes();
        }

        String result = "";
        int len = byteStrings[0].length;
        for(int i = 0; i < len; i += base64BlockSize) {
            BigInteger b = BigInteger.valueOf(1);

            for(int j = 0; j < strings.length; ++j) {
                byte[] block = Arrays.copyOfRange(byteStrings[j], i, Math.min(len, i + base64BlockSize));
                block = decoder.decode(block);
                BigInteger b_j = new BigInteger(block);

                // Distributed decryption
                b = b.multiply(b_j).mod(key.N);
            }

            byte[] bByte = b.toByteArray();
            result += new String(bByte);
        }
        return result;
    }
}
