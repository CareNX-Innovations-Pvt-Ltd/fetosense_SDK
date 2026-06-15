#include <jni.h>
#include <dlfcn.h>
#include <android/log.h>

#define LOG_TAG "FetosenseAdpcm"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

typedef jint (*decode_func_t)(JNIEnv *, jclass, jbyteArray, jbyteArray, jint, jfloat);

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    void *handle = dlopen("libbluedecode.so", RTLD_NOW | RTLD_GLOBAL);
    if (!handle) {
        LOGE("Failed to load libbluedecode.so: %s", dlerror());
        return JNI_ERR;
    }

    decode_func_t decode_func = (decode_func_t)dlsym(handle, "Java_com_jumper_adpcm_Adpcm_decode");
    if (!decode_func) {
        LOGE("Failed to find decode: %s", dlerror());
        return JNI_ERR;
    }

    decode_func_t decodec_func = (decode_func_t)dlsym(handle, "Java_com_jumper_adpcm_Adpcm_decodec");
    if (!decodec_func) {
        LOGE("Failed to find decodec: %s", dlerror());
        return JNI_ERR;
    }

    JNINativeMethod methods[] = {
        {"decode", "([B[BIF)I", (void *)decode_func},
        {"decodec", "([B[BIF)I", (void *)decodec_func},
    };

    jclass cls = (*env)->FindClass(env, "com/fetosense/adpcm/Adpcm");
    if (!cls) {
        LOGE("Failed to find class com/fetosense/adpcm/Adpcm");
        return JNI_ERR;
    }

    if ((*env)->RegisterNatives(env, cls, methods, 2) < 0) {
        LOGE("Failed to register natives");
        return JNI_ERR;
    }

    LOGI("Registered decode/decodec for com.fetosense.adpcm.Adpcm");
    return JNI_VERSION_1_6;
}
