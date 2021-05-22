package mpc.project.util;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Base64;

public class RSA {
    /**
     * blockSize : encrypt message block by block which sizes blockSize
     * blockSize * 8 should be under log_2(N). If not, can't restore original value by modular operation.
     * paddingBlockSize : block by encryption result should pad by this
     * paddingBlockSize * 8 should be over log_2(N). If not, some bits can be loss.
     */
    static private int blockSize = 1;
    /**
     * blockSize * 8 should be under log_2(N)
     **/
    static private int paddingBlockSize = 2;

    /**
     * endBlockSize * 8 should be over log_2(N)
     **/


    static public void init(BigInteger N) {
        int n = N.bitCount();
        RSA.blockSize = 1 + (n - 1) / 8;
        RSA.paddingBlockSize = n / 8 + 1;
    }

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

        int numOfBlock = (int) Math.ceil((double) byteString.length / (double) blockSize);
        int resultSize = paddingBlockSize * numOfBlock;
        byte[] result = new byte[resultSize];

        // block-by encoding
        for (int k = 0; k < numOfBlock; ++k) {
            int blockPos = k * blockSize;
            byte[] block = Arrays.copyOfRange(byteString, blockPos, Math.min(byteString.length, blockPos + blockSize));
            BigInteger b = new BigInteger(block);

            // RSA calculation
            b = b.modPow(key.getE(), key.getN());
            byte[] bByte = b.toByteArray();
            int pos = (k + 1) * paddingBlockSize - bByte.length;
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

        // block-by decoding
        for (int i = 0; i < byteString.length; i += paddingBlockSize) {
            byte[] block = Arrays.copyOfRange(byteString, i, i + paddingBlockSize);
            BigInteger b = new BigInteger(block);

            // RSA calculation
            b = b.modPow(key.getD(), key.getN());
            byte[] bByte = b.toByteArray();
            int offset = paddingBlockSize - bByte.length;
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
    static public String distributedDecrypt(String[] strings, Key key) {
        // Decoder for Base64 decoding
        Base64.Decoder decoder = Base64.getDecoder();

        byte[][] byteStrings = new byte[strings.length][];
        for (int i = 0; i < strings.length; ++i) {
            byteStrings[i] = decoder.decode(strings[i].getBytes());
        }

        StringBuilder result = new StringBuilder();
        int len = byteStrings[0].length;
        for (int i = 0; i < len; i += paddingBlockSize) {
            BigInteger b = BigInteger.valueOf(1);

            for (int j = 0; j < strings.length; ++j) {
                byte[] block = Arrays.copyOfRange(byteStrings[j], i, Math.min(len, i + paddingBlockSize));
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
