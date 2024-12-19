/*
 * Copyright 2016 Luca Martino.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copyFile of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bts.whisper;


import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;


public final class TensorUtils {

    public static OnnxTensor createInt32Tensor(OrtEnvironment env, int[] data, long[] shape) throws OrtException {
        return OnnxTensor.createTensor(env, IntBuffer.wrap(data), shape);
    }


    public static long[] tensorShape(long... dims) {
        return Arrays.copyOf(dims, dims.length);
    }


    public static OnnxTensor convertIntArrayToTensor(OrtEnvironment env, int[] intArray) throws OrtException {
        //convert int Array to an array of longs so as to make the inputIDs compatible with the encoder (which uses 64bit ints, i.e. longs)
        long[] longArray = Arrays.stream(intArray).mapToLong(i -> i).toArray();
        //convert inputIDsLong and attentionMaskLong into tensors
        long[] shape = {1, intArray.length};
        LongBuffer longBuffer = LongBuffer.wrap(longArray);
        return OnnxTensor.createTensor(env, longBuffer, shape);
    }



    public static Pair<OnnxTensor,FloatBuffer> createFloatTensorWithSingleValue(OrtEnvironment env, long[] shape) throws OrtException {
        long flat_length = shape[0];
        for (int i = 1; i < shape.length; i++) {
            flat_length = flat_length * shape[i];
        }
        FloatBuffer buffer = ByteBuffer.allocateDirect((int) (flat_length * 4)).asFloatBuffer();

        return new Pair<>(OnnxTensor.createTensor(env, buffer, shape),buffer);
    }
}