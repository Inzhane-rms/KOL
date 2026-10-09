#include <jni.h>
#include <android/log.h>
#include <stdlib.h>
#include <string.h>
#include "whisper.h"

#define TAG "SakloloWhisper"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

JNIEXPORT jlong JNICALL
Java_ph_appbuilders_saklolo_stt_WhisperNative_initContext(
        JNIEnv *env, jclass clazz, jstring model_path) {
    (void) clazz;
    const char *path = (*env)->GetStringUTFChars(env, model_path, NULL);
    struct whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    struct whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    (*env)->ReleaseStringUTFChars(env, model_path, path);
    if (ctx == NULL) {
        LOGE("Failed to load whisper model");
        return 0;
    }
    LOGI("Whisper context ready");
    return (jlong) ctx;
}

JNIEXPORT void JNICALL
Java_ph_appbuilders_saklolo_stt_WhisperNative_freeContext(
        JNIEnv *env, jclass clazz, jlong ptr) {
    (void) env;
    (void) clazz;
    if (ptr != 0) {
        whisper_free((struct whisper_context *) ptr);
    }
}

JNIEXPORT jstring JNICALL
Java_ph_appbuilders_saklolo_stt_WhisperNative_transcribe(
        JNIEnv *env,
        jclass clazz,
        jlong ptr,
        jfloatArray audio,
        jint threads,
        jstring language) {
    (void) clazz;
    if (ptr == 0 || audio == NULL) {
        return NULL;
    }

    struct whisper_context *ctx = (struct whisper_context *) ptr;
    jfloat *samples = (*env)->GetFloatArrayElements(env, audio, NULL);
    const jsize n_samples = (*env)->GetArrayLength(env, audio);
    const char *lang = language != NULL ? (*env)->GetStringUTFChars(env, language, NULL) : NULL;

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.no_context = true;
    params.single_segment = false;
    params.no_timestamps = true;
    params.n_threads = threads > 0 ? threads : 2;
    params.offset_ms = 0;
    params.suppress_blank = true;
    params.suppress_nst = true;
    params.temperature = 0.0f;
    if (lang == NULL || lang[0] == '\0' || strcmp(lang, "auto") == 0) {
        params.language = "auto";
        params.detect_language = true;
    } else {
        params.language = lang;
        params.detect_language = false;
    }

    LOGI("transcribe samples=%d threads=%d lang=%s", (int) n_samples, params.n_threads, params.language);
    const int rc = whisper_full(ctx, params, samples, n_samples);
    (*env)->ReleaseFloatArrayElements(env, audio, samples, JNI_ABORT);
    if (lang != NULL) {
        (*env)->ReleaseStringUTFChars(env, language, lang);
    }
    if (rc != 0) {
        LOGE("whisper_full failed rc=%d", rc);
        return NULL;
    }

    const int count = whisper_full_n_segments(ctx);
    size_t cap = 1024;
    size_t len = 0;
    char *buf = (char *) malloc(cap);
    if (buf == NULL) {
        return NULL;
    }
    buf[0] = '\0';
    for (int i = 0; i < count; i++) {
        const char *text = whisper_full_get_segment_text(ctx, i);
        if (text == NULL) {
            continue;
        }
        const size_t tlen = strlen(text);
        if (len + tlen + 1 > cap) {
            size_t next_cap = (len + tlen + 1) * 2;
            char *grown = (char *) realloc(buf, next_cap);
            if (grown == NULL) {
                free(buf);
                return NULL;
            }
            buf = grown;
            cap = next_cap;
        }
        memcpy(buf + len, text, tlen);
        len += tlen;
        buf[len] = '\0';
    }

    jstring out = (*env)->NewStringUTF(env, buf);
    free(buf);
    return out;
}
