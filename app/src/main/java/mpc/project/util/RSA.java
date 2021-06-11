package mpc.project.util;

import java.math.BigInteger;
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

        byte[] byteString = string.getBytes();
        int bitLen = key.getN().bitLength();
        int blockSize = ((int) Math.ceil(bitLen / 8)) - 11; // 11 bytes for PKCS1-v1_5 Padding

        int numOfBlock = (int) Math.ceil((double) byteString.length / (double) blockSize);
        int resultSize = bitLen * numOfBlock;
        byte[] result = new byte[resultSize];

        int psLen = 8;
        byte[] ps = new byte[psLen];

        // block-by encoding
        for (int k = 0; k < numOfBlock; ++k) {
            int blockPos = k * blockSize;
            byte[] block = Arrays.copyOfRange(byteString, blockPos, Math.min(byteString.length, blockPos + blockSize));
            BigInteger b = new BigInteger(block);

            // RSA calculation
            b = b.modPow(key.getE(), key.getN());
            byte[] bByte = b.toByteArray();

            int start = k * bitLen;
            new Random().nextBytes(ps);

            // PKCS1-v1_5 Padding
            result[start] = 0;
            result[start + 1] = 2;
            System.arraycopy(ps, 0, result, start + 2, psLen);
            result[start + psLen + 2] = 0;

            int pos = (k + 1) * bitLen - bByte.length;
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

        int psLen = 8;
        byte[] ps = new byte[psLen];

        // block-by decoding
        for (int i = 0; i < byteString.length; i += bitLen) {
            byte[] block = Arrays.copyOfRange(byteString, i + 11, i + bitLen);
            BigInteger b = new BigInteger(block);

            // RSA calculation
            b = b.modPow(key.getD(), key.getN());
            byte[] bByte = b.toByteArray();

            // PKCS1-v1_5 Padding
            new Random().nextBytes(ps);
            result[i] = 0;
            result[i + 1] = 2;
            System.arraycopy(ps, 0, result, i + 2, psLen);
            result[i + psLen + 2] = 0;

            int offset = bitLen - bByte.length;
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

        for (int i = 0; i < strings.length; ++i) {
            byteStrings[i] = decoder.decode(strings[i].getBytes());
        }

        StringBuilder result = new StringBuilder();
        int len = byteStrings[0].length;
        for (int i = 0; i < len; i += bitLen) {
            BigInteger b = BigInteger.valueOf(1);

            for (int j = 0; j < strings.length; ++j) {
                byte[] block = Arrays.copyOfRange(byteStrings[j], i + 11, i + bitLen);
                BigInteger b_j = new BigInteger(block);

                // Distributed decryption
                b = b.multiply(b_j).mod(key.getN());
            }

            byte[] bByte = b.toByteArray();
            result.append(new String(bByte));
        }
        return result.toString();
    }
}
