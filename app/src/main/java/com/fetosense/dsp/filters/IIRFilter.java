package com.fetosense.dsp.filters;

import com.fetosense.dsp.AudioEvent;
import com.fetosense.dsp.AudioProcessor;

public abstract class IIRFilter implements AudioProcessor {
    protected float[] b;
    protected float[] a;
    protected float[] in;
    protected float[] out;
    private final float frequency;
    private final float sampleRate;

    public IIRFilter(float freq, float sampleRate) {
        this.sampleRate = sampleRate;
        this.frequency = freq;
        this.calcCoeff();
        this.in = new float[this.a.length];
        this.out = new float[this.b.length];
    }

    protected final float getFrequency() {
        return this.frequency;
    }

    protected final float getSampleRate() {
        return this.sampleRate;
    }

    protected abstract void calcCoeff();

    public boolean process(AudioEvent audioEvent) {
        float[] audioFloatBuffer = audioEvent.getFloatBuffer();

        for(int i = audioEvent.getOverlap(); i < audioFloatBuffer.length; ++i) {
            System.arraycopy(this.in, 0, this.in, 1, this.in.length - 1);
            this.in[0] = audioFloatBuffer[i];
            float y = 0.0F;

            int j;
            for(j = 0; j < this.a.length; ++j) {
                y += this.a[j] * this.in[j];
            }

            for(j = 0; j < this.b.length; ++j) {
                y += this.b[j] * this.out[j];
            }

            System.arraycopy(this.out, 0, this.out, 1, this.out.length - 1);
            this.out[0] = y;
            audioFloatBuffer[i] = y;
        }

        return true;
    }

    public void processingFinished() {
    }
}
