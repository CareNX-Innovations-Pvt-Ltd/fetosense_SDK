package com.fetosense.dsp;

import com.fetosense.dsp.utils.AudioFloatConverter;

import java.util.Arrays;

public class AudioEvent {
    private final DSPAudioFormat format;
    private final AudioFloatConverter converter;
    private float[] floatBuffer;
    private byte[] byteBuffer;
    private int overlap;
    private long frameLength;
    private long bytesProcessed;

    public AudioEvent(DSPAudioFormat format, long frameLength) {
        this.format = format;
        this.converter = AudioFloatConverter.getConverter(format);
        this.overlap = 0;
        this.frameLength = frameLength;
    }

    public float getSampleRate() {
        return this.format.getSampleRate();
    }

    public int getBufferSize() {
        return this.getFloatBuffer().length;
    }

    public int getOverlap() {
        return this.overlap;
    }

    public void setOverlap(int newOverlap) {
        this.overlap = newOverlap;
    }

    public void setBytesProcessed(long bytesProcessed) {
        this.bytesProcessed = bytesProcessed;
    }

    public double getTimeStamp() {
        return (double)((float)(this.bytesProcessed / (long)this.format.getFrameSize()) / this.format.getSampleRate());
    }

    public long getSamplesProcessed() {
        return this.bytesProcessed / (long)this.format.getFrameSize();
    }

    public double getProgress() {
        return (double)(this.bytesProcessed / (long)this.format.getFrameSize()) / (double)this.frameLength;
    }

    public byte[] getByteBuffer() {
        int length = this.getFloatBuffer().length * this.format.getFrameSize();
        if(this.byteBuffer == null || this.byteBuffer.length != length) {
            this.byteBuffer = new byte[length];
        }
        this.converter.toByteArray(this.getFloatBuffer(), this.byteBuffer);
        return this.byteBuffer;
    }

    public void setFloatBufferWithShortBuffer(short[] shortBuffer) {
        this.floatBuffer = new float[shortBuffer.length];
        for(int i = 0; i < shortBuffer.length; ++i) {
            this.floatBuffer[i] = (float)shortBuffer[i] / 32768.0F;
        }
    }

    public void setFloatBufferWithByteBuffer(byte[] byteBuffer) {
        int length = (int)((float)byteBuffer.length / (float)this.format.getFrameSize());
        this.floatBuffer = new float[length];
        this.converter.toFloatArray(byteBuffer, this.floatBuffer);
    }

    public void setFloatBuffer(float[] floatBuffer) {
        this.floatBuffer = floatBuffer;
    }

    public float[] getFloatBuffer() {
        return this.floatBuffer;
    }

    public double getRMS() {
        return calculateRMS(this.floatBuffer);
    }

    public static double calculateRMS(float[] floatBuffer) {
        double rms = 0.0D;
        for(int i = 0; i < floatBuffer.length; ++i) {
            rms += (double)(floatBuffer[i] * floatBuffer[i]);
        }
        rms /= Double.valueOf((double)floatBuffer.length).doubleValue();
        rms = Math.sqrt(rms);
        return rms;
    }

    public void clearFloatBuffer() {
        Arrays.fill(this.floatBuffer, 0.0F);
    }

    private double soundPressureLevel(float[] buffer) {
        double value = Math.pow(this.localEnergy(buffer), 0.5D);
        value /= (double)buffer.length;
        return this.linearToDecibel(value);
    }

    private double localEnergy(float[] buffer) {
        double power = 0.0D;
        for (float element : buffer) {
            power += (double)(element * element);
        }
        return power;
    }

    private double linearToDecibel(double value) {
        return 20.0D * Math.log10(value);
    }

    public boolean isSilence(double silenceThreshold) {
        return this.soundPressureLevel(this.floatBuffer) < silenceThreshold;
    }
}
