package com.test;

import com.bts.whisper.WhisperJET;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * WhisperJET developed by
 * Kadir BASOL
 * Mail : kadir.bayner@gmail.com
 */
public class Main {

    private static final WhisperJET recognizerJava;


    //utf-8 system output
    private static final PrintStream out;

    static {
        try {
            out = new PrintStream(System.out, true, "UTF-8");
            recognizerJava = new WhisperJET();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] readWavFile(String filePath) {
        try {
            // Load the audio file
            Path testWavs = Paths.get("testWavs", filePath);
            File wavFile = testWavs.toFile();
            AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(wavFile);

            // Convert the AudioInputStream to a byte array
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int bytesRead;

            while ((bytesRead = audioInputStream.read(buffer)) != -1) {
                byteArrayOutputStream.write(buffer, 0, bytesRead);
            }

            // Close the streams
            audioInputStream.close();
            return byteArrayOutputStream.toByteArray();
        } catch (UnsupportedAudioFileException | IOException e) {
            System.err.println("Error reading the WAV file: " + e.getMessage());
            System.exit(0);
        }

        return null;
    }

    public static byte[] readWavNClipMax30(String wavFile) {
        byte[] bytes = readWavFile(wavFile);

        //16000 hz , 2 bytes sample each , 30 seconds max
        //Is there audio data that is longer than 30 seconds? , clip it
        long data = 16000*2*30;
        if(bytes.length > data) {
            byte[] data2 = new byte[32000*30];
            System.arraycopy(bytes, 0, data2, 0, data2.length);
            bytes = data2;
        }
        return bytes;
    }



    public static void test( String wavFile , String languageCode ) {
        byte[] bytes = readWavNClipMax30(wavFile);
        long preCheckTime = System.currentTimeMillis();
        String translatedText = recognizerJava.recognize(bytes , languageCode );
        long timeDiff = System.currentTimeMillis() - preCheckTime;
        System.out.printf("Time ellapsed %d%n", timeDiff);


        out.println(wavFile);
        out.println(translatedText);
    }


    public static void main(String[] args)  {
        if(args.length > 1) {
            //test with args , 16000hz wav , 2 channel audio
            test(args[0] , args[1]);
        }
        //test audio should be 16000hz
        //test for english
        test("testEN.wav","en");
        test("testTR.wav","tr");
        test("testHeb.wav","he");
        test("testHI.wav","hi");
        test("testAR.wav","ar");
    }
}