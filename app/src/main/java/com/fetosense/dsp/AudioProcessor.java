package com.fetosense.dsp;

public interface AudioProcessor {
    boolean process(AudioEvent var1);
    void processingFinished();
}
