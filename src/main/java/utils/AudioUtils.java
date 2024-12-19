package utils;

public class AudioUtils {

    public static float computeVolumeLevel(byte[] audioData , int offset , int length) {
        // Computes the RMS (root mean square) of the audio chunk
        long sum = 0;
        for (int i = offset ; i < length; i += 2) {
            short audioSample = (short) ((audioData[i + 1] << 8) | audioData[i]);
            sum += audioSample * audioSample;
        }
        float rms = (float) Math.sqrt((double) sum / ((double) length / 2));

        // Computes the volume level in decibels (dB)
        float reference = 1.0f;

        return 20.0f * (float) Math.log10(rms / reference);
    }
}
