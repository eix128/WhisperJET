package utils;



import javax.sound.sampled.*;
import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class SoundPlayer {

    private byte[] audioData;

    private AudioFormat format;

    private static final AtomicBoolean working = new AtomicBoolean( false );
    private static final AtomicBoolean playingByForce = new AtomicBoolean( false );

    public SoundPlayer() {
    }

    /**
     * @param audioFormat AudioFormat requied to play
     */
    public SoundPlayer(AudioFormat audioFormat) {
        this.format = audioFormat;
    }

    public final void prepareWav(String fileName) throws UnsupportedAudioFileException, IOException {
        RandomAccessFile randomAccessFile = new RandomAccessFile(Paths.get(fileName).toFile(), "r");

        // Get the file channel from the random access file
        FileChannel fileChannel = randomAccessFile.getChannel();

        // Create an input stream from the file channel
        InputStream inputStream = Channels.newInputStream(fileChannel);
        BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);

//        InputStream resourceAsStream = SoundPlayer.class.getResourceAsStream(fileName);
        AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(bufferedInputStream);
        format = audioInputStream.getFormat();

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        int bytesRead;
        byte[] buffer = new byte[4096];
        while ((bytesRead = audioInputStream.read(buffer)) != -1) {
            byteArrayOutputStream.write(buffer, 0, bytesRead);
        }
        audioInputStream.close();
        audioData = byteArrayOutputStream.toByteArray();
    }

    /**
     *
     * @return returns AudioFormat
     */
    public AudioFormat getFormat() {
        return format;
    }

    public final SourceDataLine prepareLine(Mixer selectedMixer) throws LineUnavailableException {

        // Get the supported line info for the mixer
        Line.Info[] supportedLineInfos = selectedMixer.getSourceLineInfo();
        SourceDataLine sourceDataLineStart = null;
        // Check if any of the supported line info is a SourceDataLine
        for (Line.Info lineInfo : supportedLineInfos) {
            if (lineInfo.getLineClass().equals(SourceDataLine.class)) {
                // Open a source data line on the selected mixer
                SourceDataLine sourceDataLine = (SourceDataLine) selectedMixer.getLine(lineInfo);

                // Get the audio format for the source data line
//                AudioFormat audioFormat = sourceDataLine.getFormat();

                // Open the source data line
                sourceDataLine.open(format);

                // Start playing the audio
                sourceDataLine.start();
                sourceDataLineStart = sourceDataLine;
                break;
            }
        }


        return sourceDataLineStart;
    }


    /**
     *
     * @param line closes the sourcedataline connection
     * @throws IOException
     */
    public final void closeAll(SourceDataLine line) throws IOException {
        // Close the SourceDataLine and AudioInputStream
        line.close();
    }

    private final AtomicBoolean atomicBoolean = new AtomicBoolean( );
    private final AtomicInteger atomicInteger = new AtomicInteger( );


    public void playWithFilter(SourceDataLine line) {
        try {
            // Create a buffer for reading the audio data

            // Read from the AudioInputStream and write to the SourceDataLine
            int lastRead = 0;
            int remaining = audioData.length;
            line.drain();
            while (lastRead < remaining) {


                // Adjust the volume by multiplying the samples by a gain factor
//                for (int i = 0; i < bytesRead; i += format.getFrameSize()) {
//                    // Convert the bytes to little-endian signed PCM samples
//                    int sample = buffer[i] | (buffer[i + 1] << 8);
//
//                    // Apply the gain factor to the sample (maximum volume)
//                    sample = Math.min(sample * Short.MAX_VALUE / Math.abs(Short.MIN_VALUE), Short.MAX_VALUE);
//
//                    // Convert the sample back to bytes
//                    buffer[i] = (byte) sample;
//                    buffer[i + 1] = (byte) (sample >> 8);
//                }

                // Write the processed audio data to the SourceDataLine


                float volumeLevel = AudioUtils.computeVolumeLevel(audioData , 0 , audioData.length);
                if (volumeLevel < 0) {
                    if( !atomicBoolean.get( ) ) {
                        atomicInteger.incrementAndGet();
                        atomicBoolean.set(true);
                    }
                    atomicBoolean.set(true);
                    continue;
                } else {
                    atomicBoolean.set( false );
                    atomicInteger.set( 0 );
                }

                line.write(audioData, lastRead, Math.min(256, remaining - lastRead));


                lastRead += 256;
            }

            // Wait for the sound to finish playing
            line.drain();


        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    public static void cancelTransaction( ) {
        working.set(false);
    }

    public static void beginTransaction( ) {
        working.set(true);
    }

    public void endTransaction( ) {
        working.set(false);
    }

    public static final boolean isPlaying( ) {
        return working.get();
    }

    public static final boolean isPlayingForce( ) {
        return playingByForce.get();
    }


    public boolean playForce(SourceDataLine line) {
        try {
            // Create a buffer for reading the audio data
            playingByForce.set(true);
            // Read from the AudioInputStream and write to the SourceDataLine
            int lastRead = 0;
            int remaining = audioData.length;
            line.drain();
            while (lastRead < remaining) {

                // Write the processed audio data to the SourceDataLine
                line.write(audioData, lastRead, Math.min(256, remaining - lastRead));


                lastRead += 256;
            }

            // Wait for the sound to finish playing
            line.drain();


        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            playingByForce.set(false);
        }
        return false;
    }

    public boolean play(SourceDataLine line) {
        boolean interrupted = false;
        try {
            // Create a buffer for reading the audio data

            // Read from the AudioInputStream and write to the SourceDataLine
            int lastRead = 0;
            int remaining = audioData.length;
//            line.drain();
            while (lastRead < remaining) {

                if (!working.get()) {
                    interrupted = true;
                    break;
                }

                // Adjust the volume by multiplying the samples by a gain factor
//                for (int i = 0; i < bytesRead; i += format.getFrameSize()) {
//                    // Convert the bytes to little-endian signed PCM samples
//                    int sample = buffer[i] | (buffer[i + 1] << 8);
//
//                    // Apply the gain factor to the sample (maximum volume)
//                    sample = Math.min(sample * Short.MAX_VALUE / Math.abs(Short.MIN_VALUE), Short.MAX_VALUE);
//
//                    // Convert the sample back to bytes
//                    buffer[i] = (byte) sample;
//                    buffer[i + 1] = (byte) (sample >> 8);
//                }

                // Write the processed audio data to the SourceDataLine
                line.write(audioData, lastRead, Math.min(256, remaining - lastRead));


                lastRead += 256;
            }

            // Wait for the sound to finish playing
//            line.drain();


        } catch (Exception e) {
            e.printStackTrace();
        }
        return interrupted;
    }

    public final byte[] getAllData() {
        return audioData;
    }



    public static void sleep(long waitTime) {
        try {
            Thread.sleep(waitTime);
        } catch (InterruptedException ignored) {
        }
    }


    public void setAudioData(byte[] audioData) {
        this.audioData = audioData;
    }

    public static void main(String[] args) throws LineUnavailableException, UnsupportedAudioFileException, IOException {

        Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
        Mixer.Info mainMixer = null;
        for (Mixer.Info info : mixerInfos) {
            System.out.println("Mixer: " + info.getName());
            if (info.getName().contains("Headphones")) {
                mainMixer = info;
            }
        }


        // Get the selected mixer
        Mixer selectedMixer = AudioSystem.getMixer(mixerInfos[0]);
        if(mainMixer != null) {
            System.out.println("Selected mixer: " + mainMixer.getName());
        }


        SoundPlayer soundPlayer = new SoundPlayer();
        soundPlayer.prepareWav("deneme.wav");

        // Open a SourceDataLine using the selected mixer
//        AudioFormat audioFormat = new AudioFormat(44100, 16, 1, true, true);
//        DataLine.Info dataLineInfo = new DataLine.Info(SourceDataLine.class, audioFormat);
//        SourceDataLine line = (SourceDataLine) selectedMixer.getLine(dataLineInfo);

        // Obtain an audio input stream from the audio file
//        AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(new File("deneme.wav"));
//
//        // Get the audio format of the input stream
//        AudioFormat audioFormat = audioInputStream.getFormat();

        // Open a source data line with the same audio format on the selected mixer
//        DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
//        SourceDataLine sourceDataLine = (SourceDataLine) selectedMixer.getLine(info);
//        sourceDataLine.open(audioFormat);


        SourceDataLine sourceDataLine = soundPlayer.prepareLine(selectedMixer);
        soundPlayer.play(sourceDataLine);
        sleep(2000);
        soundPlayer.play(sourceDataLine);
        sleep(2000);
        soundPlayer.play(sourceDataLine);
        sleep(2000);


    }
}
