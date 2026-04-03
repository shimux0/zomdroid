#ifndef ZOMDROID_XR_SESSION_H
#define ZOMDROID_XR_SESSION_H

#include <stdbool.h>
#include <jni.h>
#include <EGL/egl.h>
#include <GLES3/gl3.h>

#define XR_USE_GRAPHICS_API_OPENGL_ES
#define XR_USE_PLATFORM_ANDROID
#include <openxr/openxr.h>
#include <openxr/openxr_platform.h>

#define XR_MAX_VIEWS 2
#define XR_MAX_SWAPCHAIN_IMAGES 4

typedef struct {
    XrSwapchain handle;
    uint32_t width;
    uint32_t height;
    uint32_t image_count;
    XrSwapchainImageOpenGLESKHR images[XR_MAX_SWAPCHAIN_IMAGES];
    GLuint framebuffers[XR_MAX_SWAPCHAIN_IMAGES];
    GLuint depth_renderbuffers[XR_MAX_SWAPCHAIN_IMAGES];
} XrSwapchainState;

typedef struct {
    /* OpenXR core objects */
    XrInstance instance;
    XrSystemId system_id;
    XrSession session;
    XrSpace local_space;

    /* View configuration */
    uint32_t view_count;
    XrViewConfigurationView view_configs[XR_MAX_VIEWS];
    XrSwapchainState swapchains[XR_MAX_VIEWS];

    /* Session state */
    XrSessionState session_state;
    bool is_session_running;

    /* EGL state */
    EGLDisplay egl_display;
    EGLContext egl_context;
    EGLConfig egl_config;
    EGLSurface egl_surface;
} XrState;

bool xr_init(XrState* state, JavaVM* vm, jobject activity);
void xr_destroy(XrState* state);
bool xr_poll_events(XrState* state);
bool xr_begin_session(XrState* state);
void xr_end_session(XrState* state);
bool xr_run_frame(XrState* state);

#endif //ZOMDROID_XR_SESSION_H
