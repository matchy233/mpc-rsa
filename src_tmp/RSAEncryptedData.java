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
    private final int blockSize = 2; /** blockSize * 8 should be under log_2(N) **/
    private final int encBlockSize = 3; /** endBlockSize * 8 should be over log_2(N) **/
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

    public String decrypt(String string, Key key) {
        // Decoder for Base64 decoding
        Base64.Decoder decoder = Base64.getDecoder();

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

            /**
             * Todo: padding result with another block size
             * If this method is used for distributed decryption, another block size is needed.
             */
            result += new String(bByte);
        }

        return result;
    }
}
