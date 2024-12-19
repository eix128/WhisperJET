package com.bts.whisper;

import ai.onnxruntime.OrtException;

public class ModelLoadException extends OrtException {
    public ModelLoadException(OrtException message) {
        super(message.getCode(), message.getMessage());
    }
}
