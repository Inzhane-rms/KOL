#include <jni.h>
#include <android/log.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include "whisper.h"

#define TAG "SakloloWhisper"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

static volatile int g_cancel = 0;

typedef struct {
    int64_t deadline_us;
} saklolo_abort;

static int64_t saklolo_now_us(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t) ts.tv_sec * 1000000LL + (int64_t) (ts.tv_nsec / 1000L);
}

static bool saklolo_should_abort(void * data) {
    if (g_cancel) {
        return true;
    }
    saklolo_abort * clock = (saklolo_abort *) data;
    if (clock == NULL || clock->deadline_us <= 0) {
        return false;
    }
    return saklolo_now_us() >= clock->deadline_us;
}

static void saklolo_fill(
        struct whisper_full_params * params,
        int beam,
        const char * hint,
        saklolo_abort * clock) {
    params->print_realtime = false;
    params->print_progress = false;
    params->print_timestamps = false;
    params->print_special = false;
    params->translate = false;
    params->no_context = true;
    params->single_segment = false;
    params->no_timestamps = true;
    params->n_threads = params->n_threads > 0 ? params->n_threads : 4;
    params->offset_ms = 0;
    params->suppress_blank = true;
    params->suppress_nst = true;
    params->temperature = 0.0f;
    params->temperature_inc = 0.2f;
    params->entropy_thold = 2.4f;
    params->logprob_thold = -1.0f;
    params->language = "tl";
    params->detect_language = false;
    if (beam > 1) {
        params->beam_search.beam_size = beam;
    } else {
        params->greedy.best_of = 3;
    }
    params->initial_prompt = (hint != NULL && hint[0] != '\0') ? hint : NULL;
    params->carry_initial_prompt = params->initial_prompt != NULL;
    params->abort_callback = saklolo_should_abort;
    params->abort_callback_user_data = clock;
}

static char * saklolo_collect(struct whisper_context * ctx) {
    const int count = whisper_full_n_segments(ctx);
    size_t cap = 1024;
    size_t len = 0;
    char * buf = (char *) malloc(cap);
    if (buf == NULL) {
        return NULL;
    }
    buf[0] = '\0';
    for (int i = 0; i < count; i++) {
        const char * text = whisper_full_get_segment_text(ctx, i);
        if (text == NULL) {
            continue;
        }
        const size_t tlen = strlen(text);
        if (len + tlen + 1 > cap) {
            size_t next_cap = (len + tlen + 1) * 2;
            char * grown = (char *) realloc(buf, next_cap);
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
    return buf;
}

static jstring saklolo_answer(JNIEnv * env, char * text, bool aborted) {
    if (text == NULL) {
        return aborted ? (*env)->NewStringUTF(env, "\x1e") : NULL;
    }
    if (!aborted) {
        jstring out = (*env)->NewStringUTF(env, text);
        free(text);
        return out;
    }
    const size_t len = strlen(text);
    char * marked = (char *) malloc(len + 2);
    if (marked == NULL) {
        free(text);
        return (*env)->NewStringUTF(env, "\x1e");
    }
    marked[0] = '\x1e';
    memcpy(marked + 1, text, len + 1);
    free(text);
    jstring out = (*env)->NewStringUTF(env, marked);
    free(marked);
    return out;
}

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

JNIEXPORT void JNICALL
Java_ph_appbuilders_saklolo_stt_WhisperNative_requestAbort(
        JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;
    g_cancel = 1;
}

JNIEXPORT jstring JNICALL
Java_ph_appbuilders_saklolo_stt_WhisperNative_transcribe(
        JNIEnv *env,
        jclass clazz,
        jlong ptr,
        jfloatArray audio,
        jint threads,
        jstring language,
        jstring prompt,
        jint beam,
        jlong budget_ms) {
    (void) clazz;
    (void) language;
    if (ptr == 0 || audio == NULL) {
        return NULL;
    }

    g_cancel = 0;
    struct whisper_context *ctx = (struct whisper_context *) ptr;
    jfloat *samples = (*env)->GetFloatArrayElements(env, audio, NULL);
    const jsize n_samples = (*env)->GetArrayLength(env, audio);
    const char *hint = prompt != NULL ? (*env)->GetStringUTFChars(env, prompt, NULL) : NULL;
    const int n_threads = threads > 0 ? threads : 4;
    saklolo_abort clock;
    clock.deadline_us = budget_ms > 0 ? saklolo_now_us() + budget_ms * 1000LL : 0;

    /* Greedy best_of 3 is the default. Beam is opt-in from Kotlin.
       A deadline of about 1.5x the clip, at least 3s, aborts the decode. */
    struct whisper_full_params params = whisper_full_default_params(
            beam > 1 ? WHISPER_SAMPLING_BEAM_SEARCH : WHISPER_SAMPLING_GREEDY);
    params.n_threads = n_threads;
    saklolo_fill(&params, beam, hint, &clock);

    LOGI("transcribe samples=%d threads=%d beam=%d budget_ms=%lld", (int) n_samples, n_threads, (int) beam, (long long) budget_ms);
    int rc = whisper_full(ctx, params, samples, n_samples);
    bool aborted = rc == -6 || rc == -8 || g_cancel;
    char * text = (rc == 0 || aborted) ? saklolo_collect(ctx) : NULL;

    if (aborted && beam > 1 && !g_cancel) {
        const int64_t remain = clock.deadline_us - saklolo_now_us();
        if (remain >= 3000000LL) {
            clock.deadline_us = saklolo_now_us() + remain;
            struct whisper_full_params greedy = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
            greedy.n_threads = n_threads;
            saklolo_fill(&greedy, 1, hint, &clock);
            const int retry = whisper_full(ctx, greedy, samples, n_samples);
            const bool retry_abort = retry == -6 || retry == -8 || g_cancel;
            char * second = (retry == 0 || retry_abort) ? saklolo_collect(ctx) : NULL;
            const int first_len = text == NULL ? 0 : (int) strlen(text);
            const int second_len = second == NULL ? 0 : (int) strlen(second);
            if (retry == 0 && second_len > 0) {
                aborted = false;
                free(text);
                text = second;
                rc = 0;
            } else if (retry == 0 && first_len == 0) {
                aborted = false;
                free(text);
                text = second;
                rc = 0;
            } else if (second_len > first_len) {
                free(text);
                text = second;
            } else {
                free(second);
            }
        }
    }

    (*env)->ReleaseFloatArrayElements(env, audio, samples, JNI_ABORT);
    if (hint != NULL) {
        (*env)->ReleaseStringUTFChars(env, prompt, hint);
    }
    if (rc != 0 && !aborted) {
        LOGE("whisper_full failed rc=%d", rc);
        free(text);
        return NULL;
    }
    return saklolo_answer(env, text, aborted);
}
