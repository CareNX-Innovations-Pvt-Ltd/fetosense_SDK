package com.fetosense.audioplay;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import com.fetosense.audiodecoder.PCMFormat;
import com.fetosense.dsp.AudioEvent;
import com.fetosense.dsp.DSPAudioFormat;
import com.fetosense.dsp.filters.LowPass;

public class MyAudioTrack8Bit {

    private final static int CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO;
    private final static PCMFormat mPCM_FORMAT = PCMFormat.PCM_8BIT;

    public AudioTrack mAudioTrack = null;
    private DSPAudioFormat format;
    private AudioEvent mAudioEvent;
    private LowPass mLowPass;

    public MyAudioTrack8Bit() {
    }

    public void prepareAudioTrack() {
        short freq = 4000;
        int primePlaySize = 200;
        this.mAudioTrack = new AudioTrack(AudioManager.STREAM_SYSTEM, freq, CHANNEL_CONFIG,
                mPCM_FORMAT.getAudioFormat(), primePlaySize, AudioTrack.MODE_STREAM);
        this.format = new DSPAudioFormat((float)freq, 8, 1, false, false);
        mLowPass = new LowPass(300, freq);
        this.mAudioTrack.play();
    }

    private void setAudioEvent(byte[] buf){
        mAudioEvent = new AudioEvent(format, (long) buf.length);
        mAudioEvent.setFloatBufferWithByteBuffer(buf);
        mLowPass.process(mAudioEvent);
    }

    public void writeAudioTrack(byte[] buf, int start, int len) {
        setAudioEvent(buf);
        this.mAudioTrack.write(mAudioEvent.getByteBuffer(), start, len);
    }

    public void releaseAudioTrack() {
        this.mAudioTrack.release();
        this.mAudioTrack = null;
    }
}
