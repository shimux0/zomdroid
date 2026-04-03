#include <jni.h>
#include <pthread.h>
#include <bits/stdatomic.h>

#include "xr_session.h"
#include "xr_renderer.h"
#include "logger.h"

#define LOG_TAG "zomdroid-xr-jni"

static XrState g_xr_state;
XrRendererState g_xr_renderer;
static pthread_t g_xr_thread;
static atomic_bool g_xr_running;

typedef struct {
    JavaVM* vm;
    jobject activity;
} XrThreadArgs;

static void* xr_thread_func(void* arg) {
    XrThreadArgs* args = (XrThreadArgs*)arg;

    if (!xr_init(&g_xr_state, args->vm, args->activity)) {
        LOGE("XR init failed");
        JNIEnv* env;
        (*args->vm)->AttachCurrentThread(args->vm, &env, NULL);
        (*env)->DeleteGlobalRef(env, args->activity);
        (*args->vm)->DetachCurrentThread(args->vm);
        free(args);
        return NULL;
    }

    if (!xr_renderer_init(&g_xr_renderer)) {
        LOGE("XR renderer init failed");
        xr_destroy(&g_xr_state);
        JNIEnv* env;
        (*args->vm)->AttachCurrentThread(args->vm, &env, NULL);
        (*env)->DeleteGlobalRef(env, args->activity);
        (*args->vm)->DetachCurrentThread(args->vm);
        free(args);
        return NULL;
    }

    while (atomic_load(&g_xr_running)) {
        if (!xr_poll_events(&g_xr_state)) break;

        switch (g_xr_state.session_state) {
            case XR_SESSION_STATE_READY:
                if (!g_xr_state.is_session_running) {
                    xr_begin_session(&g_xr_state);
                }
                break;
            case XR_SESSION_STATE_STOPPING:
                xr_end_session(&g_xr_state);
                break;
            default:
                break;
        }

        if (g_xr_state.is_session_running) {
            xr_run_frame(&g_xr_state);
        }
    }

    xr_renderer_destroy(&g_xr_renderer);
    xr_destroy(&g_xr_state);

    JNIEnv* env;
    (*args->vm)->AttachCurrentThread(args->vm, &env, NULL);
    (*env)->DeleteGlobalRef(env, args->activity);
    (*args->vm)->DetachCurrentThread(args->vm);
    free(args);

    LOGI("XR thread finished");
    return NULL;
}

JNIEXPORT void JNICALL
Java_com_zomdroid_VrActivity_nativeXrCreate(JNIEnv* env, jobject thiz) {
    if (atomic_load(&g_xr_running)) {
        LOGW("XR already running");
        return;
    }

    JavaVM* vm;
    (*env)->GetJavaVM(env, &vm);

    XrThreadArgs* args = malloc(sizeof(XrThreadArgs));
    if (!args) {
        LOGE("malloc failed");
        return;
    }
    args->vm = vm;
    args->activity = (*env)->NewGlobalRef(env, thiz);

    atomic_store(&g_xr_running, true);
    if (pthread_create(&g_xr_thread, NULL, xr_thread_func, args) != 0) {
        LOGE("pthread_create failed");
        atomic_store(&g_xr_running, false);
        (*env)->DeleteGlobalRef(env, args->activity);
        free(args);
        return;
    }
    LOGI("XR thread started");
}

JNIEXPORT void JNICALL
Java_com_zomdroid_VrActivity_nativeXrDestroy(JNIEnv* env, jobject thiz) {
    atomic_store(&g_xr_running, false);
    pthread_join(g_xr_thread, NULL);
    LOGI("XR thread joined");
}
