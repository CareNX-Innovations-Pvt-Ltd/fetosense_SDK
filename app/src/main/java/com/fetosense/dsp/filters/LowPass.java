package com.fetosense.dsp.filters;

public class LowPass extends IIRFilter {
    public LowPass(float freq, float sampleRate) {
        super(freq > 60.0F?freq:60.0F, sampleRate);
    }

    protected void calcCoeff() {
        float freqFrac = this.getFrequency() / this.getSampleRate();
        float x = (float)Math.exp(-14.445D * (double)freqFrac);
        this.a = new float[]{(float)Math.pow((double)(1.0F - x), 4.0D)};
        this.b = new float[]{4.0F * x, -6.0F * x * x, 4.0F * x * x * x, -x * x * x * x};
    }
}
