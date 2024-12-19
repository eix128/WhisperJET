package com.bts.whisper;

import java.nio.FloatBuffer;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ai.onnxruntime.*;
import ai.onnxruntime.extensions.OrtxPackage;
import utils.BTSUtils;
/**
 * WhisperJET developed by
 * Kadir BASOL
 * Mail : kadir.bayner@gmail.com
 */
public class WhisperJET {

    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("<\\|[^>]*\\|> ");


    private static final Logger LOGGER = Logger.getLogger(WhisperJET.class.getName());
    private static final int MAX_TOKENS_PER_SECOND = 30;
    private static final int MAX_TOKENS = 445;
    public static final String UNKNOWN_VALUE = "[(null)]";

    private static final String[] LANGUAGES = {
            "en", "zh", "de", "es", "ru", "ko", "fr", "ja", "pt", "tr", "pl", "ca", "nl", "ar", "sv", "it", "id", "hi", "fi", "vi", "he", "uk", "el", "ms", "cs", "ro", "da", "hu", "ta", "no", "th", "ur", "hr", "bg", "lt", "la", "mi", "ml", "cy", "sk", "te", "fa", "lv", "bn", "sr", "az", "sl", "kn", "et", "mk", "br", "eu", "is", "hy", "ne", "mn", "bs", "kk", "sq", "sw", "gl", "mr", "pa", "si", "km", "sn", "yo", "so", "af", "oc", "ka", "be", "tg", "sd", "gu", "am", "yi", "lo", "uz", "fo", "ht", "ps", "tk", "nn", "mt", "sa", "lb", "my", "bo", "tl", "mg", "as", "tt", "haw", "ln", "ha", "ba", "jw", "su", "yue"
    };

    private static final int START_TOKEN_ID = 50258;
    private static final int TRANSCRIBE_TOKEN_ID = 50359;
    private static final int NO_TIMESTAMPS_TOKEN_ID = 50363;

    private final OrtSession initSession;
    private final OrtSession encoderSession;
    private final OrtSession cacheInitSession;
    private final OrtSession decoderSession;
    private final OrtSession detokenizerSession;
    private final OrtEnvironment onnxEnv;



    private static final int EOS = 50257;
    private static final int SAMPLE_RATE_CANDIDATES = 16000;
    private static final float out = 1.0f / (float) SAMPLE_RATE_CANDIDATES * (float) MAX_TOKENS_PER_SECOND;

    private final Map<String, OnnxTensor> inputsMap;
    private final ArrayList<Integer> completeOutput;


    public WhisperJET() throws Exception {
        // Initialize OrtEnvironment
        onnxEnv = OrtEnvironment.getEnvironment();


        inputsMap = new HashMap<>();
        completeOutput = new ArrayList<>();

        // Define model paths
        String encoderPath = "models/WhisperJET_encoder.onnx";
        String decoderPath = "models/WhisperJET_decoder.onnx";
        String modelInitPath = "models/WhisperJET_init.onnx";
        String cacheInitPath = "models/WhisperJET_cache_initializer.onnx";
        String detokenizerPath = "models/WhisperJET_detokenizer.onnx";

        try {
            OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();
            sessionOptions.registerCustomOpLibrary(OrtxPackage.getLibraryPath());
            sessionOptions.setCPUArenaAllocator(true);
            sessionOptions.setMemoryPatternOptimization(true);
            sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.EXTENDED_OPT);


            sessionOptions.disableProfiling();
            sessionOptions.setSessionLogLevel(OrtLoggingLevel.ORT_LOGGING_LEVEL_FATAL);


            initSession = onnxEnv.createSession(modelInitPath, sessionOptions);
            encoderSession = onnxEnv.createSession(encoderPath, sessionOptions);
            decoderSession = onnxEnv.createSession(decoderPath, sessionOptions);
            cacheInitSession = onnxEnv.createSession(cacheInitPath, sessionOptions);
            detokenizerSession = onnxEnv.createSession(detokenizerPath, sessionOptions);

        } catch (OrtException e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize ONNX files , check onnx files on path models/", e);
            throw new ModelLoadException(e);
        }

    }


    public String recognize(final float[] data, final String languageCode) {
        return recognize(new DataPacked(data, languageCode));
    }

    public String recognize(final byte[] data , final String languageCode) {
        final float[] samples = BTSUtils.convertToWhisperFormat(data);
        return recognize(new DataPacked(samples, languageCode));
    }


    private String recognize(final DataPacked data) {
        String finalText = UNKNOWN_VALUE;
        if (data != null) {
            //we convert data in un audioTensor and start the transcription
            try {
                FloatBuffer floatAudioDataBuffer = FloatBuffer.wrap(data.data);
                OnnxTensor audioTensor = OnnxTensor.createTensor(onnxEnv, floatAudioDataBuffer, TensorUtils.tensorShape(1L, data.data.length));

                // if we generate more than this number of tokens it means that we have an infinite loop due to the fact that the sound cannot be transcribed with the language selected
                float kout = (float) (data.data.length) * out;
                int maxTokens = Math.min((int) kout, MAX_TOKENS);
                boolean execution1HitMaxLength = false;

                //pass to local
                final Map<String,OnnxTensor> inputsMap = this.inputsMap;
                final List<Integer> completeOutput = this.completeOutput;

                inputsMap.clear();
                inputsMap.put("fast_pcm", audioTensor);
                OrtSession.Result outputsInit = initSession.run(inputsMap);
                inputsMap.clear();
                OnnxTensor outputInit = (OnnxTensor) outputsInit.get(0);

                //PREPARE ENCODER
                inputsMap.clear();
                inputsMap.put("input_features", outputInit);
                //154.032ms
                OrtSession.Result outputs = encoderSession.run(inputsMap);
                inputsMap.clear();
                OnnxTensor outputEncoder = (OnnxTensor) outputs.get(0);

                //execution of decoder
                OnnxTensor inputIDsTensor = null;
                OnnxTensor decoderOutput;
                float[][][] value;
                float[] outputValues;


                //PREPARE CACHE
                OrtSession.Result initResult;
                inputsMap.clear();
                inputsMap.put("encoder_hidden_states", outputEncoder);
                initResult = cacheInitSession.run(inputsMap);



                //DECODER ITERATION
                OrtSession.Result result = null;
                OrtSession.Result oldResult;
                int max = -1;
                boolean isFirstIteration = true;
                int j = 1;

                int languageID = getLanguageID(data.languageCode);
                final int[] decoderInitialInputIDs = {START_TOKEN_ID, languageID, TRANSCRIBE_TOKEN_ID, NO_TIMESTAMPS_TOKEN_ID};

                OnnxTensor decoderPastTensor = null;
                FloatBuffer floatBuffer = null;
                while (max != EOS) {
                    if (j <= 4) {
                        inputIDsTensor = TensorUtils.convertIntArrayToTensor(onnxEnv, new int[]{decoderInitialInputIDs[j - 1]});
                    }
                    //We prepare the decoder input
                    inputsMap.clear();
                    inputsMap.put("input_ids", inputIDsTensor);

                    if (isFirstIteration) {
                        long[] shape = {1, 12, 0, 64};
                        Pair<OnnxTensor, FloatBuffer> floatTensorWithSingleValue = TensorUtils.createFloatTensorWithSingleValue(onnxEnv, shape);
                        decoderPastTensor = floatTensorWithSingleValue.getFirst();
                        floatBuffer = floatTensorWithSingleValue.getSecond();
                        for (int i = 0; i < 12; i++) {
                            inputsMap.put("idk" + i, decoderPastTensor);
                            inputsMap.put("idv" + i, decoderPastTensor);
                            inputsMap.put("iek" + i, (OnnxTensor) initResult.get("opek" + i).get());
                            inputsMap.put("iev" + i, (OnnxTensor) initResult.get("opev" + i).get());
                        }
                        isFirstIteration = false;
                    } else {
                        for (int i = 0; i < 12; i++) {
                            inputsMap.put("idk" + i, (OnnxTensor) result.get("opdk" + i).get());
                            inputsMap.put("idv" + i, (OnnxTensor) result.get("opdv" + i).get());
                            inputsMap.put("iek" + i, (OnnxTensor) initResult.get("opek" + i).get());
                            inputsMap.put("iev" + i, (OnnxTensor) initResult.get("opev" + i).get());
                        }
                    }
                    floatBuffer.clear();
                    oldResult = result;
                    //use cache of whisper jet
                    //178.955ms
                    result = decoderSession.run(inputsMap);
                    inputIDsTensor.close();


                    inputsMap.clear();
                    if (oldResult != null) {
                        oldResult.close();
                    }
                    decoderOutput = (OnnxTensor) result.get("logits").get();
                    value = (float[][][]) decoderOutput.getValue();
                    outputValues = value[0][0];
                    max = BTSUtils.getIndexOfLargest(outputValues);
                    completeOutput.add(max);

                    //We prepare the inputs for the next iteration
                    inputIDsTensor = TensorUtils.convertIntArrayToTensor(onnxEnv, new int[]{max});


                    if (j >= maxTokens) {
                        execution1HitMaxLength = true;
                        max = EOS;
                    }

                    j++;
                }


                //execution of the detokenizer

                if (!execution1HitMaxLength) {
                    final int[] tokenSequences = completeOutput.stream().mapToInt(i -> i).toArray();
                    inputsMap.clear();
                    inputsMap.put("sequences", TensorUtils.createInt32Tensor(onnxEnv, tokenSequences, new long[]{1, 1, tokenSequences.length}));
                    final OrtSession.Result detokenizerOutputs = this.detokenizerSession.run(inputsMap);
                    inputsMap.clear();
                    Object finalTextResult = detokenizerOutputs.get(0).getValue();
                    finalText = ((String[][]) finalTextResult)[0][0];
                    detokenizerOutputs.close();
                }
                completeOutput.clear();
                outputs.close();
                outputInit.close();
                initResult.close();
                inputIDsTensor.close();
                result.close();
                audioTensor.close();
                if(decoderPastTensor != null) {
                    decoderPastTensor.close();
                }

                return modifyText(finalText);


            } catch (OrtException e) {
                e.printStackTrace();
            }
        }
        return null;
    }






    public static String removeTimestamps(String text) {
        Matcher matcher = TIMESTAMP_PATTERN.matcher(text);
        return matcher.replaceAll("");
    }

    private final Pattern pattern = Pattern.compile("\\.{3}");

    private String modifyText(final String text) {

        //sometimes, even if timestamps are deactivated, Whisper insert those anyway (es. <|0.00|>), so we remove eventual timestamps
        String correctedText = removeTimestamps(text);

        //we remove eventual white space from both ends of the text
        correctedText = correctedText.trim();

        if (correctedText.length() >= 2) {
            //if the correctedText start with a lower case letter we make it upper case
            char firstChar = correctedText.charAt(0);
            if (Character.isLowerCase(firstChar)) {
                StringBuilder sb = new StringBuilder(correctedText);
                sb.setCharAt(0, Character.toUpperCase(firstChar));
                correctedText = sb.toString();
            }
            Matcher matcher = pattern.matcher(correctedText);
            correctedText = matcher.replaceAll("");
        }
        return correctedText;
    }

    public int getLanguageID(String language) {
        final int length = LANGUAGES.length;
        for (int i = 0; i < length; i++) {
            if (LANGUAGES[i].equals(language)) {
                return START_TOKEN_ID + i + 1;
            }
        }
        return -1;
    }


    private static class DataPacked {
        private final float[] data;
        private final String languageCode;

        private DataPacked(float[] data , String languageCode) {
            this.data = data;
            this.languageCode = languageCode;
        }
    }
}
