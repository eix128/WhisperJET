package utils;

import java.util.ArrayList;

public class BTSUtils {
    public static short[] byteArrayToShortArrayLittleToBigEndian(byte[] byteArray) {
        short[] shortArray = new short[byteArray.length >> 1];
        int k = 0;
        for (int i = 0; i < byteArray.length; i += 2) {
            shortArray[k++] = (short) ((byteArray[i + 1] << 8) | (byteArray[i] & 0xFF));
        }

        return shortArray;
    }

    public static float[] convertToWhisperFormat(byte[] bytes) {
        short[] shorts = BTSUtils.byteArrayToShortArrayLittleToBigEndian(bytes);
        int shortsLen = shorts.length;
        float[] samples = new float[(shortsLen)];

        float v = 1.0f / (float) Short.MAX_VALUE;
        for (int j = 0; j < shortsLen; j++) {
            float sample = ((float) shorts[j]) * v;
            samples[j] = (sample > 1.0f) ? 1.0f : Math.max(sample, -1.0f);
        }

        return samples;
    }


    public static int getIndexOfLargest(float[] array) {
        int largestIndex = 0;
        float largest = -Float.MAX_VALUE;
        for (int i = 0; i < array.length; i++) {
            if (array[i] > largest) {
                largestIndex = i;
                largest = array[largestIndex];
            }
        }
        return largestIndex;
    }

}
