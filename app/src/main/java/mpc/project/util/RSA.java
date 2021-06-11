package mpc.project.util;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Random;

public class RSA {
    /**
     * Method to encrypt given message
     *
     * @param string : message string to encrypt
     * @param key    : key to encrypt with
     * @return : encrypted string. This is base64 encoded string.
     */
    static public String encrypt(String string, Key key) {
        // Encoder for Base64 encoding
        Base64.Encoder encoder = Base64.getEncoder();

        byte[] byteString = string.getBytes(StandardCharsets.UTF_8);
        int bitLen = key.getN().bitLength();
        int blockByte = (int) Math.ceil((double) bitLen / (double) 8) + 1;
        int blockSize = blockByte - 11; // 11 bytes for PKCS1-v1_5 Padding
        assert(blockSize > 0);

        int numOfBlock = (int) Math.ceil((double) byteString.length / (double) blockSize);
        int resultSize = blockByte * numOfBlock;
        byte[] result = new byte[resultSize];

        int psLen = 8;
        byte[] ps = new byte[psLen];

        // block-by encoding
        for (int k = 0; k < numOfBlock; ++k) {
            int blockPos = k * blockSize;
            int blockLen = Math.min(byteString.length - blockPos, blockSize);
            byte[] block = new byte[blockLen + 11];
            System.arraycopy(byteString, blockPos, block, 11, blockLen);

            // PKCS1-v1_5 Padding
            block[0] = 0;
            block[1] = 2;
            new Random().nextBytes(ps);
            System.arraycopy(ps, 0, block, 2, psLen);
            block[psLen + 2] = 0;

            BigInteger b = new BigInteger(block);

            // RSA calculation
            b = b.modPow(key.getE(), key.getN());
            byte[] bByte = b.toByteArray();

            int pos = (k + 1) * blockByte - bByte.length;
            System.arraycopy(bByte, 0, result, pos, bByte.length);
        }

        return encoder.encodeToString(result);
    }

    /**
     * Method to decrypt locally. Worker should do a partial decryption with this method.
     *
     * @param string : encrypted data to decrypt
     * @param key    : key to decrypt with
     * @return: partially decrypted string. This should be sent to manager.
     */
    static public String localDecrypt(String string, Key key) {
        // Encoder and Decoder for Base64 decoding
        Base64.Decoder decoder = Base64.getDecoder();
        Base64.Encoder encoder = Base64.getEncoder();

        byte[] byteString = decoder.decode(string.getBytes());
        byte[] result = new byte[byteString.length];
        int bitLen = key.getN().bitLength();
        int blockByte = (int) Math.ceil((double) bitLen / (double) 8) + 1;

        // block-by decoding
        for (int i = 0; i < byteString.length; i += blockByte) {
            byte[] block = Arrays.copyOfRange(byteString, i, i + blockByte);
            BigInteger b = new BigInteger(block);

            // RSA calculation
            b = b.modPow(key.getD(), key.getN());
            byte[] bByte = b.toByteArray();

            int offset = blockByte - bByte.length;
            System.arraycopy(bByte, 0, result, i + offset, bByte.length);
        }

        return encoder.encodeToString(result);
    }

    /**
     * Method to finish decryption. Manager can get original message with partially decrypted messages.
     *
     * @param strings : decrypted messages with each di
     *                strings should have same length
     * @param key     : any Key. just used for mod N calculation
     * @return: original message
     **/
    static public String combineDecryptionResult(String[] strings, Key key) {
        // Decoder for Base64 decoding
        Base64.Decoder decoder = Base64.getDecoder();

        byte[][] byteStrings = new byte[strings.length][];
        int bitLen = key.getN().bitLength();
        int blockByte = (int) Math.ceil((double) bitLen / (double) 8) + 1;

        for (int i = 0; i < strings.length; ++i) {
            byteStrings[i] = decoder.decode(strings[i].getBytes());
        }

        String result = "";
        int len = byteStrings[0].length;
        for (int i = 0; i < len; i += blockByte) {
            BigInteger b = BigInteger.valueOf(1);

            for (int j = 0; j < strings.length; ++j) {
                byte[] block = Arrays.copyOfRange(byteStrings[j], i, i + blockByte);
                BigInteger b_j = new BigInteger(block);

                // Distributed decryption
                b = b.multiply(b_j).mod(key.getN());
            }

            byte[] bByte = b.toByteArray();
            byte[] M = Arrays.copyOfRange(bByte, 10, bByte.length); // first byte is zero, so M is start from 10.
            result += new String(M, StandardCharsets.UTF_8);
        }
        return result;
    }
}
