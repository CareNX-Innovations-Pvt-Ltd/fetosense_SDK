package com.fetosense.dsp;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DSPAudioFormat {
    protected DSPAudioFormat.Encoding encoding;
    private float sampleRate;
    private int sampleSizeInBits;
    private int channels;
    private int frameSize;
    private float frameRate;
    private boolean bigEndian;
    private HashMap<String, Object> properties;

    private DSPAudioFormat(DSPAudioFormat.Encoding encoding, float sampleRate, int sampleSizeInBits, int channels, int frameSize, float frameRate, boolean bigEndian) {
        this.encoding = encoding;
        this.sampleRate = sampleRate;
        this.sampleSizeInBits = sampleSizeInBits;
        this.channels = channels;
        this.frameSize = frameSize;
        this.frameRate = frameRate;
        this.bigEndian = bigEndian;
        this.properties = null;
    }

    public DSPAudioFormat(DSPAudioFormat.Encoding encoding, float sampleRate, int sampleSizeInBits, int channels, int frameSize, float frameRate, boolean bigEndian, Map<String, Object> properties) {
        this(encoding, sampleRate, sampleSizeInBits, channels, frameSize, frameRate, bigEndian);
        this.properties = (HashMap<String, Object>) properties;
    }

    public DSPAudioFormat(float sampleRate, int sampleSizeInBits, int channels, boolean signed, boolean bigEndian) {
        this(signed? DSPAudioFormat.Encoding.PCM_SIGNED: DSPAudioFormat.Encoding.PCM_UNSIGNED, sampleRate, sampleSizeInBits, channels, channels != -1 && sampleSizeInBits != -1?(sampleSizeInBits + 7) / 8 * channels:-1, sampleRate, bigEndian);
    }

    public DSPAudioFormat.Encoding getEncoding() {
        return this.encoding;
    }

    public float getSampleRate() {
        return this.sampleRate;
    }

    public int getSampleSizeInBits() {
        return this.sampleSizeInBits;
    }

    public int getChannels() {
        return this.channels;
    }

    public int getFrameSize() {
        return this.frameSize;
    }

    private float getFrameRate() {
        return this.frameRate;
    }

    public boolean isBigEndian() {
        return this.bigEndian;
    }

    public Map properties() {
        Object ret;
        if(this.properties == null) {
            ret = new HashMap(0);
        } else {
            ret = this.properties.clone();
        }
        return Collections.unmodifiableMap((Map)ret);
    }

    public Object getProperty(String key) {
        return this.properties == null?null:this.properties.get(key);
    }

    public boolean matches(DSPAudioFormat format) {
        return format.getEncoding().equals(this.getEncoding()) && (format.getSampleRate() == -1.0F || format.getSampleRate() == this.getSampleRate()) && format.getSampleSizeInBits() == this.getSampleSizeInBits() && format.getChannels() == this.getChannels() && format.getFrameSize() == this.getFrameSize() && (format.getFrameRate() == -1.0F || format.getFrameRate() == this.getFrameRate()) && (format.getSampleSizeInBits() <= 8 || format.isBigEndian() == this.isBigEndian());
    }

    public String toString() {
        String sEncoding = "";
        if(this.getEncoding() != null) {
            sEncoding = this.getEncoding().toString() + " ";
        }
        String sSampleRate;
        if(this.getSampleRate() == -1.0F) {
            sSampleRate = "unknown sample rate, ";
        } else {
            sSampleRate = this.getSampleRate() + " Hz, ";
        }
        String sSampleSizeInBits;
        if((float)this.getSampleSizeInBits() == -1.0F) {
            sSampleSizeInBits = "unknown bits per sample, ";
        } else {
            sSampleSizeInBits = this.getSampleSizeInBits() + " bit, ";
        }
        String sChannels;
        if(this.getChannels() == 1) {
            sChannels = "mono, ";
        } else if(this.getChannels() == 2) {
            sChannels = "stereo, ";
        } else if(this.getChannels() == -1) {
            sChannels = " unknown number of channels, ";
        } else {
            sChannels = this.getChannels() + " channels, ";
        }
        String sFrameSize;
        if((float)this.getFrameSize() == -1.0F) {
            sFrameSize = "unknown frame size, ";
        } else {
            sFrameSize = this.getFrameSize() + " bytes/frame, ";
        }
        String sFrameRate = "";
        if((double)Math.abs(this.getSampleRate() - this.getFrameRate()) > 1.0E-5D) {
            if(this.getFrameRate() == -1.0F) {
                sFrameRate = "unknown frame rate, ";
            } else {
                sFrameRate = this.getFrameRate() + " frames/second, ";
            }
        }
        String sEndian = "";
        if((this.getEncoding().equals(DSPAudioFormat.Encoding.PCM_SIGNED) || this.getEncoding().equals(DSPAudioFormat.Encoding.PCM_UNSIGNED)) && (this.getSampleSizeInBits() > 8 || this.getSampleSizeInBits() == -1)) {
            if(this.isBigEndian()) {
                sEndian = "big-endian";
            } else {
                sEndian = "little-endian";
            }
        }
        return sEncoding + sSampleRate + sSampleSizeInBits + sChannels + sFrameSize + sFrameRate + sEndian;
    }

    public static class Encoding {
        public static final DSPAudioFormat.Encoding PCM_SIGNED = new DSPAudioFormat.Encoding("PCM_SIGNED");
        public static final DSPAudioFormat.Encoding PCM_UNSIGNED = new DSPAudioFormat.Encoding("PCM_UNSIGNED");
        public static final DSPAudioFormat.Encoding ULAW = new DSPAudioFormat.Encoding("ULAW");
        public static final DSPAudioFormat.Encoding ALAW = new DSPAudioFormat.Encoding("ALAW");
        private String name;

        public Encoding(String name) {
            this.name = name;
        }

        public final boolean equals(Object obj) {
            return this.toString() == null?obj != null && obj.toString() == null:(obj instanceof Encoding && this.toString().equals(obj.toString()));
        }

        public final int hashCode() {
            return this.toString() == null?0:this.toString().hashCode();
        }

        public final String toString() {
            return this.name;
        }
    }
}
