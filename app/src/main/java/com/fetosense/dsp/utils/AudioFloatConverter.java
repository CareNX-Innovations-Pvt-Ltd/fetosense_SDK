package com.fetosense.dsp.utils;

import com.fetosense.dsp.DSPAudioFormat;
import com.fetosense.dsp.DSPAudioFormat.Encoding;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;

public abstract class AudioFloatConverter {
    public static final DSPAudioFormat.Encoding PCM_FLOAT = new DSPAudioFormat.Encoding("PCM_FLOAT");
    private DSPAudioFormat format;

    public AudioFloatConverter() {
    }

    public static AudioFloatConverter getConverter(DSPAudioFormat format) {
        Object conv = null;
        if(format.getFrameSize() == 0) {
            return null;
        } else if(format.getFrameSize() != (format.getSampleSizeInBits() + 7) / 8 * format.getChannels()) {
            return null;
        } else {
            if(format.getEncoding().equals(Encoding.PCM_SIGNED)) {
                if(format.isBigEndian()) {
                    if(format.getSampleSizeInBits() <= 8) {
                        conv = new AudioFloatConverter.AudioFloatConversion8S();
                    } else if(format.getSampleSizeInBits() > 8 && format.getSampleSizeInBits() <= 16) {
                        conv = new AudioFloatConverter.AudioFloatConversion16SB();
                    } else if(format.getSampleSizeInBits() > 16 && format.getSampleSizeInBits() <= 24) {
                        conv = new AudioFloatConverter.AudioFloatConversion24SB();
                    } else if(format.getSampleSizeInBits() > 24 && format.getSampleSizeInBits() <= 32) {
                        conv = new AudioFloatConverter.AudioFloatConversion32SB();
                    } else if(format.getSampleSizeInBits() > 32) {
                        conv = new AudioFloatConverter.AudioFloatConversion32xSB((format.getSampleSizeInBits() + 7) / 8 - 4);
                    }
                } else if(format.getSampleSizeInBits() <= 8) {
                    conv = new AudioFloatConverter.AudioFloatConversion8S();
                } else if(format.getSampleSizeInBits() > 8 && format.getSampleSizeInBits() <= 16) {
                    conv = new AudioFloatConverter.AudioFloatConversion16SL();
                } else if(format.getSampleSizeInBits() > 16 && format.getSampleSizeInBits() <= 24) {
                    conv = new AudioFloatConverter.AudioFloatConversion24SL();
                } else if(format.getSampleSizeInBits() > 24 && format.getSampleSizeInBits() <= 32) {
                    conv = new AudioFloatConverter.AudioFloatConversion32SL();
                } else if(format.getSampleSizeInBits() > 32) {
                    conv = new AudioFloatConverter.AudioFloatConversion32xSL((format.getSampleSizeInBits() + 7) / 8 - 4);
                }
            } else if(format.getEncoding().equals(Encoding.PCM_UNSIGNED)) {
                if(format.isBigEndian()) {
                    if(format.getSampleSizeInBits() <= 8) {
                        conv = new AudioFloatConverter.AudioFloatConversion8U();
                    } else if(format.getSampleSizeInBits() > 8 && format.getSampleSizeInBits() <= 16) {
                        conv = new AudioFloatConverter.AudioFloatConversion16UB();
                    } else if(format.getSampleSizeInBits() > 16 && format.getSampleSizeInBits() <= 24) {
                        conv = new AudioFloatConverter.AudioFloatConversion24UB();
                    } else if(format.getSampleSizeInBits() > 24 && format.getSampleSizeInBits() <= 32) {
                        conv = new AudioFloatConverter.AudioFloatConversion32UB();
                    } else if(format.getSampleSizeInBits() > 32) {
                        conv = new AudioFloatConverter.AudioFloatConversion32xUB((format.getSampleSizeInBits() + 7) / 8 - 4);
                    }
                } else if(format.getSampleSizeInBits() <= 8) {
                    conv = new AudioFloatConverter.AudioFloatConversion8U();
                } else if(format.getSampleSizeInBits() > 8 && format.getSampleSizeInBits() <= 16) {
                    conv = new AudioFloatConverter.AudioFloatConversion16UL();
                } else if(format.getSampleSizeInBits() > 16 && format.getSampleSizeInBits() <= 24) {
                    conv = new AudioFloatConverter.AudioFloatConversion24UL();
                } else if(format.getSampleSizeInBits() > 24 && format.getSampleSizeInBits() <= 32) {
                    conv = new AudioFloatConverter.AudioFloatConversion32UL();
                } else if(format.getSampleSizeInBits() > 32) {
                    conv = new AudioFloatConverter.AudioFloatConversion32xUL((format.getSampleSizeInBits() + 7) / 8 - 4);
                }
            } else if(format.getEncoding().equals(PCM_FLOAT)) {
                if(format.getSampleSizeInBits() == 32) {
                    if(format.isBigEndian()) {
                        conv = new AudioFloatConverter.AudioFloatConversion32B();
                    } else {
                        conv = new AudioFloatConverter.AudioFloatConversion32L();
                    }
                } else if(format.getSampleSizeInBits() == 64) {
                    if(format.isBigEndian()) {
                        conv = new AudioFloatConverter.AudioFloatConversion64B();
                    } else {
                        conv = new AudioFloatConverter.AudioFloatConversion64L();
                    }
                }
            }

            if((format.getEncoding().equals(Encoding.PCM_SIGNED) || format.getEncoding().equals(Encoding.PCM_UNSIGNED)) && format.getSampleSizeInBits() % 8 != 0) {
                conv = new AudioFloatConverter.AudioFloatLSBFilter((AudioFloatConverter)conv, format);
            }

            if(conv != null) {
                ((AudioFloatConverter)conv).format = format;
            }

            return (AudioFloatConverter)conv;
        }
    }

    public DSPAudioFormat getFormat() {
        return this.format;
    }

    public abstract float[] toFloatArray(byte[] var1, int var2, float[] var3, int var4, int var5);

    public float[] toFloatArray(byte[] in_buff, float[] out_buff, int out_offset, int out_len) {
        return this.toFloatArray(in_buff, 0, out_buff, out_offset, out_len);
    }

    public float[] toFloatArray(byte[] in_buff, int in_offset, float[] out_buff, int out_len) {
        return this.toFloatArray(in_buff, in_offset, out_buff, 0, out_len);
    }

    public float[] toFloatArray(byte[] in_buff, float[] out_buff, int out_len) {
        return this.toFloatArray(in_buff, 0, out_buff, 0, out_len);
    }

    public float[] toFloatArray(byte[] in_buff, float[] out_buff) {
        return this.toFloatArray(in_buff, 0, out_buff, 0, out_buff.length);
    }

    public abstract byte[] toByteArray(float[] var1, int var2, int var3, byte[] var4, int var5);

    public byte[] toByteArray(float[] in_buff, int in_len, byte[] out_buff, int out_offset) {
        return this.toByteArray(in_buff, 0, in_len, out_buff, out_offset);
    }

    public byte[] toByteArray(float[] in_buff, int in_offset, int in_len, byte[] out_buff) {
        return this.toByteArray(in_buff, in_offset, in_len, out_buff, 0);
    }

    public byte[] toByteArray(float[] in_buff, int in_len, byte[] out_buff) {
        return this.toByteArray(in_buff, 0, in_len, out_buff, 0);
    }

    public byte[] toByteArray(float[] in_buff, byte[] out_buff) {
        return this.toByteArray(in_buff, 0, in_buff.length, out_buff, 0);
    }

    private static class AudioFloatConversion8S extends AudioFloatConverter {
        private AudioFloatConversion8S() {
        }

        public float[] toFloatArray(byte[] in_buff, int in_offset, float[] out_buff, int out_offset, int out_len) {
            for(int i = 0; i < out_len; ++i) {
                out_buff[out_offset + i] = (float)(in_buff[in_offset + i] << 8) / 32768.0F;
            }
            return out_buff;
        }

        public byte[] toByteArray(float[] in_buff, int in_offset, int in_len, byte[] out_buff, int out_offset) {
            for(int i = 0; i < in_len; ++i) {
                float x = in_buff[in_offset + i] * 32768.0F;
                if(x > 32767.0F) {
                    x = 32767.0F;
                } else if(x < -32768.0F) {
                    x = -32768.0F;
                }
                out_buff[out_offset + i] = (byte)((int)((short)x) >> 8 & 255);
            }
            return out_buff;
        }
    }

    private static class AudioFloatConversion8U extends AudioFloatConverter {
        private AudioFloatConversion8U() {
        }

        public float[] toFloatArray(byte[] in_buff, int in_offset, float[] out_buff, int out_offset, int out_len) {
            for(int i = 0; i < out_len; ++i) {
                out_buff[out_offset + i] = ((float)(in_buff[in_offset + i] & 255) - 128.0F) / 128.0F;
            }
            return out_buff;
        }

        public byte[] toByteArray(float[] in_buff, int in_offset, int in_len, byte[] out_buff, int out_offset) {
            for(int i = 0; i < in_len; ++i) {
                float x = in_buff[in_offset + i] * 128.0F;
                if(x > 127.0F) {
                    x = 127.0F;
                } else if(x < -128.0F) {
                    x = -128.0F;
                }
                out_buff[out_offset + i] = (byte)((int)x + 128);
            }
            return out_buff;
        }
    }

    private static class AudioFloatConversion16SB extends AudioFloatConverter {
        private AudioFloatConversion16SB() {
        }

        public float[] toFloatArray(byte[] in_buff, int in_offset, float[] out_buff, int out_offset, int out_len) {
            for(int i = 0; i < out_len; ++i) {
                out_buff[out_offset + i] = (float)(in_buff[in_offset + i * 2] << 8 | in_buff[in_offset + i * 2 + 1] & 255) / 32768.0F;
            }
            return out_buff;
        }

        public byte[] toByteArray(float[] in_buff, int in_offset, int in_len, byte[] out_buff, int out_offset) {
            for(int i = 0; i < in_len; ++i) {
                float x = in_buff[in_offset + i] * 32768.0F;
                if(x > 32767.0F) {
                    x = 32767.0F;
                } else if(x < -32768.0F) {
                    x = -32768.0F;
                }
                int xx = (int)x;
                out_buff[out_offset + i * 2] = (byte)(xx >> 8);
                out_buff[out_offset + i * 2 + 1] = (byte)(xx & 255);
            }
            return out_buff;
        }
    }

    private static class AudioFloatConversion16SL extends AudioFloatConverter {
        private AudioFloatConversion16SL() {
        }

        public float[] toFloatArray(byte[] in_buff, int in_offset, float[] out_buff, int out_offset, int out_len) {
            for(int i = 0; i < out_len; ++i) {
                out_buff[out_offset + i] = (float)(in_buff[in_offset + i * 2 + 1] << 8 | in_buff[in_offset + i * 2] & 255) / 32768.0F;
            }
            return out_buff;
        }

        public byte[] toByteArray(float[] in_buff, int in_offset, int in_len, byte[] out_buff, int out_offset) {
            for(int i = 0; i < in_len; ++i) {
                float x = in_buff[in_offset + i] * 32768.0F;
                if(x > 32767.0F) {
                    x = 32767.0F;
                } else if(x < -32768.0F) {
                    x = -32768.0F;
                }
                int xx = (int)x;
                out_buff[out_offset + i * 2] = (byte)(xx & 255);
                out_buff[out_offset + i * 2 + 1] = (byte)(xx >> 8);
            }
            return out_buff;
        }
    }

    private static class AudioFloatConversion16UB extends AudioFloatConverter {
        private AudioFloatConversion16UB() {
        }

        public float[] toFloatArray(byte[] in_buff, int in_offset, float[] out_buff, int out_offset, int out_len) {
            for(int i = 0; i < out_len; ++i) {
                int x = (in_buff[in_offset + i * 2] & 255) << 8 | in_buff[in_offset + i * 2 + 1] & 255;
                out_buff[out_offset + i] = ((float)x - 32768.0F) / 32768.0F;
            }
            return out_buff;
        }

        public byte[] toByteArray(float[] in_buff, int in_offset, int in_len, byte[] out_buff, int out_offset) {
            for(int i = 0; i < in_len; ++i) {
                float x = in_buff[in_offset + i] * 32768.0F;
                if(x > 32767.0F) {
                    x = 32767.0F;
                } else if(x < -32768.0F) {
                    x = -32768.0F;
                }
                int xx = (int)((double)x + 32768.0D);
                out_buff[out_offset + i * 2] = (byte)(xx >> 8);
                out_buff[out_offset + i * 2 + 1] = (byte)(xx & 255);
            }
            return out_buff;
        }
    }

    private static class AudioFloatConversion16UL extends AudioFloatConverter {
        private AudioFloatConversion16UL() {
        }

        public float[] toFloatArray(byte[] in_buff, int in_offset, float[] out_buff, int out_offset, int out_len) {
            for(int i = 0; i < out_len; ++i) {
                int x = (in_buff[in_offset + i * 2 + 1] & 255) << 8 | in_buff[in_offset + i * 2] & 255;
                out_buff[out_offset + i] = ((float)x - 32768.0F) / 32768.0F;
            }
            return out_buff;
        }

        public byte[] toByteArray(float[] in_buff, int in_offset, int in_len, byte[] out_buff, int out_offset) {
            for(int i = 0; i < in_len; ++i) {
                float x = in_buff[in_offset + i] * 32768.0F;
                if(x > 32767.0F) {
                    x = 32767.0F;
                } else if(x < -32768.0F) {
                    x = -32768.0F;
                }
                int xx = (int)((double)x + 32768.0D);
                out_buff[out_offset + i * 2] = (byte)(xx & 255);
                out_buff[out_offset + i * 2 + 1] = (byte)(xx >> 8);
            }
            return out_buff;
        }
    }

    private static class AudioFloatConversion24SB extends AudioFloatConverter {
        private AudioFloatConversion24SB() {
        }

        public float[] toFloatArray(byte[] in_buff, int in_offset, float[] out_buff, int out_offset, int out_len) {
            for(int i = 0; i < out_len; ++i) {
                out_buff[out_offset + i] = (float)(in_buff[in_offset + i * 3] << 16 | (in_buff[in_offset + i * 3 + 1] & 255) << 8 | in_buff[in_offset + i * 3 + 2] & 255) / 8.3886072E6F;
            }
            return out_buff;
        }

        public byte[] toByteArray(float[] in_buff, int in_offset, int in_len, byte[] out_buff, int out_offset) {
            for(int i = 0; i < in_len; ++i) {
                float x = in_buff[in_offset + i] * 8.3886072E6F;
                if(x > 8388607.0F) {
                    x = 8388607.0F;
                } else if(x < -8388608.0F) {
                    x = -8388608.0F;
                }
                int xx = (int)x;
                out_buff[out_offset + i * 3] = (byte)(xx >> 16);
                out_buff[out_offset + i * 3 + 1] = (byte)(xx >> 8);
                out_buff[out_offset + i * 3 + 2] = (byte)(xx & 255);
            }
            return out_buff;
        }
    }

    private static class AudioFloatConversion24SL extends AudioFloatConverter {
        private AudioFloatConversion24SL() {
        }

        public float[] toFloatArray(byte[] in_buff, int in_offset, float[] out_buff, int out_offset, int out_len) {
            for(int i = 0; i < out_len; ++i) {
                out_buff[out_offset + i] = (float)(in_buff[in_offset + i * 3 + 2] << 16 | (in_buff[in_offset + i * 3 + 1] & 255) << 8 | in_buff[in_offset + i * 3] & 255) / 8.3886072E6F;
            }
            return out_buff;
        }

        public byte[] toByteArray(float[] in_buff, int in_offset, int in_len, byte[] out_buff, int out_offset) {
            for(int i = 0; i < in_len; ++i) {
                float x = in_buff[in_offset + i] * 8.3886072E6F;
                if(x > 8388607.0F) {
                    x = 8388607.0F;
                } else if(x < -8388608.0F) {
                    x = -8388608.0F;
                }
                int xx = (int)x;
                out_buff[out_offset + i * 3 + 2] = (byte)(xx >> 16);
                out_buff[out_offset + i * 3 + 1] = (byte)(xx >> 8);
                out_buff[out_offset + i * 3] = (byte)(xx & 255);
            }
            return out_buff;
        }
    }

    private static class AudioFloatConversion24UB extends AudioFloatConverter {
        private AudioFloatConversion24UB() {
        }

        public float[] toFloatArray(byte[] in_buff, int in_offset, float[] out_buff, int out_offset, int out_len) {
            for(int i = 0; i < out_len; ++i) {
                int x = (in_buff[in_offset + i * 3] & 255) << 16 | (in_buff[in_offset + i * 3 + 1] & 255) << 8 | in_buff[in_offset + i * 3 + 2] & 255;
                out_buff[out_offset + i] = ((float)x - 8.388608E6F) / 8.388608E6F;
            }
            return out_buff;
        }

        public byte[] toByteArray(float[] in_buff, int in_offset, int in_len, byte[] out_buff, int out_offset) {
            for(int i = 0; i < in_len; ++i) {
                float x = in_buff[in_offset + i] * 8.388608E6F;
                if(x > 8.388607E6F) {
                    x = 8.388607E6F;
                } else if(x < -8.388608E6F) {
                    x = -8.388608E6F;
                }
                int xx = (int)((double)x + 8.388608E6D);
                out_buff[out_offset + i * 3] = (byte)(xx >> 16);
                out_buff[out_offset + i * 3 + 1] = (byte)(xx >> 8);
                out_buff[out_offset + i * 3 + 2] = (byte)(xx & 255);
            }
            return out_buff;
        }
    }

    private static class AudioFloatConversion24UL extends AudioFloatConverter {
        private AudioFloatConversion24UL() {
        }

        public float[] toFloatArray(byte[] in_buff, int in_offset, float[] out_buff, int out_offset, int out_len) {
            for(int i = 0; i < out_len; ++i) {
                int x = (in_buff[in_offset + i * 3 + 2] & 255) << 16 | (in_buff[in_offset + i * 3 + 1] & 255) << 8 | in_buff[in_offset + i * 3] & 255;
                out_buff[out_offset + i] = ((float)x - 8.388608E6F) / 8.388608E6F;
            }
            return out_buff;
        }

        public byte[] toByteArray(float[] in_buff, int in_offset, int in_len, byte[] out_buff, int out_offset) {
            for(int i = 0; i < in_len; ++i) {
                float x = in_buff[in_offset + i] * 8.388608E6F;
                if(x > 8.388607E6F) {
                    x = 8.388607E6F;
                } else if(x < -8.388608E6F) {
                    x = -8.388608E6F;
                }
                int xx = (int)((double)x + 8.388608E6D);
                out_buff[out_offset + i * 3 + 2] = (byte)(xx >> 16);
                out_buff[out_offset + i * 3 + 1] = (byte)(xx >> 8);
                out_buff[out_offset + i * 3] = (byte)(xx & 255);
            }
            return out_buff;
        }
    }

    private static class AudioFloatConversion32B extends AudioFloatConverter {
        private AudioFloatConversion32B() {
        }

        public float[] toFloatArray(byte[] in_buff, int in_offset, float[] out_buff, int out_offset, int out_len) {
            for(int i = 0; i < out_len; ++i) {
                out_buff[out_offset + i] = ByteBuffer.wrap(in_buff, in_offset + i * 4, 4).order(ByteOrder.BIG_ENDIAN).getFloat();
            }
            return out_buff;
        }

        public byte[] toByteArray(float[] in_buff, int in_offset, int in_len, byte[] out_buff, int out_offset) {
            ByteBuffer bb = ByteBuffer.wrap(out_buff, out_offset, in_len * 4).order(ByteOrder.BIG_ENDIAN);
            bb.asFloatBuffer().put(in_buff, in_offset, in_len);
            return out_buff;
        }
    }

    private static class AudioFloatConversion32L extends AudioFloatConverter {
        private AudioFloatConversion32L() {
        }

        public float[] toFloatArray(byte[] in_buff, int in_offset, float[] out_buff, int out_offset, int out_len) {
            for(int i = 0; i < out_len; ++i) {
                out_buff[out_offset + i] = ByteBuffer.wrap(in_buff, in_offset + i * 4, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
            }
            return out_buff;
        }

        public byte[] toByteArray(float[] in_buff, int in_offset, int in_len, byte[] out_buff, int out_offset) {
            ByteBuffer bb = ByteBuffer.wrap(out_buff, out_offset, in_len * 4).order(ByteOrder.LITTLE_ENDIAN);
            bb.asFloatBuffer().put(in_buff, in_offset, in_len);
            return out_buff;
        }
    }

    private static class AudioFloatConversion32SB extends AudioFloatConverter {
        private AudioFloatConversion32SB() {
        }

        public float[] toFloatArray(byte[] in_buff, int in_offset, float[] out_buff, int out_offset, int out_len) {
            for(int i = 0; i < out_len; ++i) {
                out_buff[out_offset + i] = (float)(in_buff[in_offset + i * 4] << 24 | (in_buff[in_offset + i * 4 + 1] & 255) << 16 | (in_buff[in_offset + i * 4 + 2] & 255) << 8 | in_buff[in_offset + i * 4 + 3] & 255) / 2.14748365E9F;
            }
            return out_buff;
        }

        public byte[] toByteArray(float[] in_buff, int in_offset, int in_len, byte[] out_buff, int out_offset) {
            for(int i = 0; i < in_len; ++i) {
                float x = in_buff[in_offset + i] * 2.14748365E9F;
                if(x > 2.14748365E9F) {
                    x = 2.14748365E9F;
                } else if(x < -2.14748365E9F) {
                    x = -2.14748365E9F;
                }
                int xx = (int)x;
                out_buff[out_offset + i * 4] = (byte)(xx >> 24);
                out_buff[out_offset + i * 4 + 1] = (byte)(xx >> 16);
                out_buff[out_offset + i * 4 + 2] = (byte)(xx >> 8);
                out_buff[out_offset + i * 4 + 3] = (byte)(xx & 255);
            }
            return out_buff;
        }
    }

    private static class AudioFloatConversion32SL extends AudioFloatConverter {
        private AudioFloatConversion32SL() {
        }

        public float[] toFloatArray(byte[] in_buff, int in_offset, float[] out_buff, int out_offset, int out_len) {
            for(int i = 0; i < out_len; ++i) {
                out_buff[out_offset + i] = (float)(in_buff[in_offset + i * 4 + 3] << 24 | (in_buff[in_offset + i * 4 + 2] & 255) << 16 | (in_buff[in_offset + i * 4 + 1] & 255) << 8 | in_buff[in_offset + i * 4] & 255) / 2.14748365E9F;
            }
            return out_buff;
        }

        public byte[] toByteArray(float[] in_buff, int in_offset, int in_len, byte[] out_buff, int out_offset) {
            for(int i = 0; i < in_len; ++i) {
                float x = in_buff[in_offset + i] * 2.14748365E9F;
                if(x > 2.14748365E9F) {
                    x = 2.14748365E9F;
                } else if(x < -2.14748365E9F) {
                    x = -2.14748365E9F;
                }
                int xx = (int)x;
                out_buff[out_offset + i * 4 + 3] = (byte)(xx >> 24);
                out_buff[out_offset + i * 4 + 2] = (byte)(xx >> 16);
                out_buff[out_offset + i * 4 + 1] = (byte)(xx >> 8);
                out_buff[out_offset + i * 4] = (byte)(xx & 255);
            }
            return out_buff;
        }
    }

    private static class AudioFloatConversion32UB extends AudioFloatConverter {
        private AudioFloatConversion32UB() {
        }

        public float[] toFloatArray(byte[] in_buff, int in_offset, float[] out_buff, int out_offset, int out_len) {
            for(int i = 0; i < out_len; ++i) {
                int x = (in_buff[in_offset + i * 4] & 255) << 24 | (in_buff[in_offset + i * 4 + 1] & 255) << 16 | (in_buff[in_offset + i * 4 + 2] & 255) << 8 | in_buff[in_offset + i * 4 + 3] & 255;
                out_buff[out_offset + i] = ((float)x - 2.14748365E9F) / 2.14748365E9F;
            }
            return out_buff;
        }

        public byte[] toByteArray(float[] in_buff, int in_offset, int in_len, byte[] out_buff, int out_offset) {
            for(int i = 0; i < in_len; ++i) {
                float x = in_buff[in_offset + i] * 2.14748365E9F;
                if(x > 2.14748365E9F) {
                    x = 2.14748365E9F;
                } else if(x < -2.14748365E9F) {
                    x = -2.14748365E9F;
                }
                int xx = (int)((double)x + 2.14748365E9D);
                out_buff[out_offset + i * 4] = (byte)(xx >> 24);
                out_buff[out_offset + i * 4 + 1] = (byte)(xx >> 16);
                out_buff[out_offset + i * 4 + 2] = (byte)(xx >> 8);
                out_buff[out_offset + i * 4 + 3] = (byte)(xx & 255);
            }
            return out_buff;
        }
    }

    private static class AudioFloatConversion32UL extends AudioFloatConverter {
        private AudioFloatConversion32UL() {
        }

        public float[] toFloatArray(byte[] in_buff, int in_offset, float[] out_buff, int out_offset, int out_len) {
            for(int i = 0; i < out_len; ++i) {
                int x = (in_buff[in_offset + i * 4 + 3] & 255) << 24 | (in_buff[in_offset + i * 4 + 2] & 255) << 16 | (in_buff[in_offset + i * 4 + 1] & 255) << 8 | in_buff[in_offset + i * 4] & 255;
                out_buff[out_offset + i] = ((float)x - 2.14748365E9F) / 2.14748365E9F;
            }
            return out_buff;
        }

        public byte[] toByteArray(float[] in_buff, int in_offset, int in_len, byte[] out_buff, int out_offset) {
            for(int i = 0; i < in_len; ++i) {
                float x = in_buff[in_offset + i] * 2.14748365E9F;
                if(x > 2.14748365E9F) {
                    x = 2.14748365E9F;
                } else if(x < -2.14748365E9F) {
                    x = -2.14748365E9F;
                }
                int xx = (int)((double)x + 2.14748365E9D);
                out_buff[out_offset + i * 4 + 3] = (byte)(xx >> 24);
                out_buff[out_offset + i * 4 + 2] = (byte)(xx >> 16);
                out_buff[out_offset + i * 4 + 1] = (byte)(xx >> 8);
                out_buff[out_offset + i * 4] = (byte)(xx & 255);
            }
            return out_buff;
        }
    }

    private static class AudioFloatConversion32xSB extends AudioFloatConverter {
        private final int xbytes;

        public AudioFloatConversion32xSB(int xbytes) {
            this.xbytes = xbytes;
        }

        public float[] toFloatArray(byte[] in_buff, int in_offset, float[] out_buff, int out_offset, int out_len) {
            int xlen = this.xbytes + 4;
            int n;
            int i;
            for(i = 0; i < out_len; ++i) {
                n = 0;
                for(int j = 0; j < xlen; ++j) {
                    n <<= 8;
                    n |= in_buff[in_offset + i * xlen + j] & 255;
                }
                n >>= (this.xbytes + 1) * 8 - 1;
                out_buff[out_offset + i] = (float)((double)((float)n / Math.pow(2.0D, (double)((this.xbytes + 4) * 8 - 1))));
            }
            return out_buff;
        }

        public byte[] toByteArray(float[] in_buff, int in_offset, int in_len, byte[] out_buff, int out_offset) {
            int xlen = this.xbytes + 4;
            int n;
            int i;
            for(i = 0; i < in_len; ++i) {
                float x = in_buff[in_offset + i];
                n = (int)((double)(x * (float)Math.pow(2.0D, (double)((xlen * 8 - 1) - this.xbytes * 8))) * Math.pow(2.0D, (double)(this.xbytes * 8)));
                for(int j = xlen - 1; j >= 0; --j) {
                    out_buff[out_offset + i * xlen + j] = (byte)(n & 255);
                    n >>= 8;
                }
            }
            return out_buff;
        }
    }

    private static class AudioFloatConversion32xSL extends AudioFloatConverter {
        private final int xbytes;

        public AudioFloatConversion32xSL(int xbytes) {
            this.xbytes = xbytes;
        }

        public float[] toFloatArray(byte[] in_buff, int in_offset, float[] out_buff, int out_offset, int out_len) {
            int xlen = this.xbytes + 4;
            int n;
            int i;
            for(i = 0; i < out_len; ++i) {
                n = 0;
                for(int j = xlen - 1; j >= 0; --j) {
                    n <<= 8;
                    n |= in_buff[in_offset + i * xlen + j] & 255;
                }
                n >>= (this.xbytes + 1) * 8 - 1;
                out_buff[out_offset + i] = (float)((double)((float)n / Math.pow(2.0D, (double)((this.xbytes + 4) * 8 - 1))));
            }
            return out_buff;
        }

        public byte[] toByteArray(float[] in_buff, int in_offset, int in_len, byte[] out_buff, int out_offset) {
            int xlen = this.xbytes + 4;
            int n;
            int i;
            for(i = 0; i < in_len; ++i) {
                float x = in_buff[in_offset + i];
                n = (int)((double)(x * (float)Math.pow(2.0D, (double)((xlen * 8 - 1) - this.xbytes * 8))) * Math.pow(2.0D, (double)(this.xbytes * 8)));
                for(int j = 0; j < xlen; ++j) {
                    out_buff[out_offset + i * xlen + j] = (byte)(n & 255);
                    n >>= 8;
                }
            }
            return out_buff;
        }
    }

    private static class AudioFloatConversion32xUB extends AudioFloatConverter {
        private final int xbytes;

        public AudioFloatConversion32xUB(int xbytes) {
            this.xbytes = xbytes;
        }

        public float[] toFloatArray(byte[] in_buff, int in_offset, float[] out_buff, int out_offset, int out_len) {
            int xlen = this.xbytes + 4;
            int n;
            int i;
            for(i = 0; i < out_len; ++i) {
                n = 0;
                for(int j = 0; j < xlen; ++j) {
                    n <<= 8;
                    n |= in_buff[in_offset + i * xlen + j] & 255;
                }
                n -= Math.pow(2.0D, (double)(xlen * 8 - 1));
                out_buff[out_offset + i] = (float)((double)((float)((double)n / Math.pow(2.0D, (double)(xlen * 8 - 1)))) * 2.0F);
            }
            return out_buff;
        }

        public byte[] toByteArray(float[] in_buff, int in_offset, int in_len, byte[] out_buff, int out_offset) {
            int xlen = this.xbytes + 4;
            int n;
            int i;
            for(i = 0; i < in_len; ++i) {
                float x = in_buff[in_offset + i] / 2.0F;
                n = (int)((double)((x + 1.0F) * (float)Math.pow(2.0D, (double)(xlen * 8 - 1))));
                for(int j = xlen - 1; j >= 0; --j) {
                    out_buff[out_offset + i * xlen + j] = (byte)(n & 255);
                    n >>= 8;
                }
            }
            return out_buff;
        }
    }

    private static class AudioFloatConversion32xUL extends AudioFloatConverter {
        private final int xbytes;

        public AudioFloatConversion32xUL(int xbytes) {
            this.xbytes = xbytes;
        }

        public float[] toFloatArray(byte[] in_buff, int in_offset, float[] out_buff, int out_offset, int out_len) {
            int xlen = this.xbytes + 4;
            int n;
            int i;
            for(i = 0; i < out_len; ++i) {
                n = 0;
                for(int j = xlen - 1; j >= 0; --j) {
                    n <<= 8;
                    n |= in_buff[in_offset + i * xlen + j] & 255;
                }
                n -= Math.pow(2.0D, (double)(xlen * 8 - 1));
                out_buff[out_offset + i] = (float)((double)((float)((double)n / Math.pow(2.0D, (double)(xlen * 8 - 1)))) * 2.0F);
            }
            return out_buff;
        }

        public byte[] toByteArray(float[] in_buff, int in_offset, int in_len, byte[] out_buff, int out_offset) {
            int xlen = this.xbytes + 4;
            int n;
            int i;
            for(i = 0; i < in_len; ++i) {
                float x = in_buff[in_offset + i] / 2.0F;
                n = (int)((double)((x + 1.0F) * (float)Math.pow(2.0D, (double)(xlen * 8 - 1))));
                for(int j = 0; j < xlen; ++j) {
                    out_buff[out_offset + i * xlen + j] = (byte)(n & 255);
                    n >>= 8;
                }
            }
            return out_buff;
        }
    }

    private static class AudioFloatConversion64B extends AudioFloatConverter {
        private AudioFloatConversion64B() {
        }

        public float[] toFloatArray(byte[] in_buff, int in_offset, float[] out_buff, int out_offset, int out_len) {
            for(int i = 0; i < out_len; ++i) {
                out_buff[out_offset + i] = (float)ByteBuffer.wrap(in_buff, in_offset + i * 8, 8).order(ByteOrder.BIG_ENDIAN).getDouble();
            }
            return out_buff;
        }

        public byte[] toByteArray(float[] in_buff, int in_offset, int in_len, byte[] out_buff, int out_offset) {
            int i;
            for(i = 0; i < in_len; ++i) {
                ByteBuffer bb = ByteBuffer.wrap(out_buff, out_offset + i * 8, 8).order(ByteOrder.BIG_ENDIAN);
                bb.putDouble((double)in_buff[in_offset + i]);
            }
            return out_buff;
        }
    }

    private static class AudioFloatConversion64L extends AudioFloatConverter {
        private AudioFloatConversion64L() {
        }

        public float[] toFloatArray(byte[] in_buff, int in_offset, float[] out_buff, int out_offset, int out_len) {
            for(int i = 0; i < out_len; ++i) {
                out_buff[out_offset + i] = (float)ByteBuffer.wrap(in_buff, in_offset + i * 8, 8).order(ByteOrder.LITTLE_ENDIAN).getDouble();
            }
            return out_buff;
        }

        public byte[] toByteArray(float[] in_buff, int in_offset, int in_len, byte[] out_buff, int out_offset) {
            for(int i = 0; i < in_len; ++i) {
                ByteBuffer bb = ByteBuffer.wrap(out_buff, out_offset + i * 8, 8).order(ByteOrder.LITTLE_ENDIAN);
                bb.putDouble((double)in_buff[in_offset + i]);
            }
            return out_buff;
        }
    }

    private static class AudioFloatLSBFilter extends AudioFloatConverter {
        private int length;
        private final AudioFloatConverter converter;

        public AudioFloatLSBFilter(AudioFloatConverter converter, DSPAudioFormat format) {
            this.converter = converter;
            boolean bigEndian = format.isBigEndian();
            this.length = format.getFrameSize();
            if(!bigEndian) {
                ++this.length;
            }
        }

        public float[] toFloatArray(byte[] in_buff, int in_offset, float[] out_buff, int out_offset, int out_len) {
            for(int i = 0; i < out_len; ++i) {
                out_buff[out_offset + i] = this.converter.toFloatArray(in_buff, in_offset + i * this.length, out_buff, out_offset + i, 1)[out_offset + i];
            }
            return out_buff;
        }

        public byte[] toByteArray(float[] in_buff, int in_offset, int in_len, byte[] out_buff, int out_offset) {
            return this.converter.toByteArray(in_buff, in_offset, in_len, out_buff, out_offset);
        }
    }
}
