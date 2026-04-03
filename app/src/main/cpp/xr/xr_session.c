#include <string.h>
#include <stdlib.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES3/gl3.h>

#include "xr_session.h"
#include "xr_renderer.h"
#include "xr_math.h"
#include "logger.h"

#define LOG_TAG "zomdroid-xr"

extern XrRendererState g_xr_renderer;

#ifndef EGL_OPENGL_ES3_BIT_KHR
#define EGL_OPENGL_ES3_BIT_KHR 0x00000040
#endif

#define XR_CHECK(result, msg) do { \
    XrResult _r = (result); \
    if (XR_FAILED(_r)) { \
        LOGE("%s: %d", msg, _r); \
        return false; \
    } \
} while (0)

static bool create_egl_context(XrState* state) {
    state->egl_display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (state->egl_display == EGL_NO_DISPLAY) {
        LOGE("eglGetDisplay failed");
        return false;
    }

    if (!eglInitialize(state->egl_display, NULL, NULL)) {
        LOGE("eglInitialize failed");
        return false;
    }

    EGLint config_attribs[] = {
        EGL_RED_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 8,
        EGL_DEPTH_SIZE, 24,
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT_KHR,
        EGL_NONE
    };

    EGLint num_configs;
    if (!eglChooseConfig(state->egl_display, config_attribs,
                         &state->egl_config, 1, &num_configs) || num_configs < 1) {
        LOGE("eglChooseConfig failed");
        return false;
    }

    EGLint context_attribs[] = {EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE};
    state->egl_context = eglCreateContext(state->egl_display, state->egl_config,
                                          EGL_NO_CONTEXT, context_attribs);
    if (state->egl_context == EGL_NO_CONTEXT) {
        LOGE("eglCreateContext failed");
        return false;
    }

    /* Make context current without a surface (headless rendering for OpenXR) */
    if (!eglMakeCurrent(state->egl_display, EGL_NO_SURFACE,
                        EGL_NO_SURFACE, state->egl_context)) {
        /* Fallback: tiny pbuffer surface */
        EGLint pbuf_attribs[] = {EGL_WIDTH, 1, EGL_HEIGHT, 1, EGL_NONE};
        state->egl_surface = eglCreatePbufferSurface(state->egl_display,
                                                      state->egl_config, pbuf_attribs);
        if (!eglMakeCurrent(state->egl_display, state->egl_surface,
                            state->egl_surface, state->egl_context)) {
            LOGE("eglMakeCurrent failed");
            return false;
        }
    }

    LOGI("EGL context created: GLES %s", glGetString(GL_VERSION));
    return true;
}

static bool init_loader(JavaVM* vm, jobject activity) {
    PFN_xrInitializeLoaderKHR xrInitializeLoaderKHR = NULL;
    xrGetInstanceProcAddr(XR_NULL_HANDLE, "xrInitializeLoaderKHR",
                          (PFN_xrVoidFunction*)&xrInitializeLoaderKHR);
    if (!xrInitializeLoaderKHR) {
        LOGE("xrInitializeLoaderKHR not available");
        return false;
    }

    XrLoaderInitInfoAndroidKHR loader_info = {
        .type = XR_TYPE_LOADER_INIT_INFO_ANDROID_KHR,
        .applicationVM = vm,
        .applicationContext = activity
    };
    XR_CHECK(xrInitializeLoaderKHR((XrLoaderInitInfoBaseHeaderKHR*)&loader_info),
             "xrInitializeLoaderKHR");
    return true;
}

static bool create_instance(XrState* state, JavaVM* vm, jobject activity) {
    const char* extensions[] = {
        XR_KHR_OPENGL_ES_ENABLE_EXTENSION_NAME,
        XR_KHR_ANDROID_CREATE_INSTANCE_EXTENSION_NAME
    };

    XrInstanceCreateInfoAndroidKHR android_info = {
        .type = XR_TYPE_INSTANCE_CREATE_INFO_ANDROID_KHR,
        .applicationVM = vm,
        .applicationActivity = activity
    };

    XrInstanceCreateInfo info = {
        .type = XR_TYPE_INSTANCE_CREATE_INFO,
        .next = &android_info,
        .applicationInfo = {
            .applicationVersion = 1,
            .engineVersion = 1,
            .apiVersion = XR_MAKE_VERSION(1, 0, 0)
        },
        .enabledExtensionCount = 2,
        .enabledExtensionNames = extensions
    };
    strncpy(info.applicationInfo.applicationName, "Zomdroid VR", XR_MAX_APPLICATION_NAME_SIZE);
    strncpy(info.applicationInfo.engineName, "Zomdroid", XR_MAX_ENGINE_NAME_SIZE);

    XR_CHECK(xrCreateInstance(&info, &state->instance), "xrCreateInstance");

    XrInstanceProperties props = {.type = XR_TYPE_INSTANCE_PROPERTIES};
    xrGetInstanceProperties(state->instance, &props);
    LOGI("OpenXR runtime: %s v%u.%u.%u", props.runtimeName,
         XR_VERSION_MAJOR(props.runtimeVersion),
         XR_VERSION_MINOR(props.runtimeVersion),
         XR_VERSION_PATCH(props.runtimeVersion));
    return true;
}

static bool get_system(XrState* state) {
    XrSystemGetInfo system_info = {
        .type = XR_TYPE_SYSTEM_GET_INFO,
        .formFactor = XR_FORM_FACTOR_HEAD_MOUNTED_DISPLAY
    };
    XR_CHECK(xrGetSystem(state->instance, &system_info, &state->system_id), "xrGetSystem");
    LOGI("XR system ID: %llu", (unsigned long long)state->system_id);
    return true;
}

static bool check_graphics_requirements(XrState* state) {
    PFN_xrGetOpenGLESGraphicsRequirementsKHR xrGetOpenGLESGraphicsRequirementsKHR = NULL;
    xrGetInstanceProcAddr(state->instance, "xrGetOpenGLESGraphicsRequirementsKHR",
                          (PFN_xrVoidFunction*)&xrGetOpenGLESGraphicsRequirementsKHR);
    if (!xrGetOpenGLESGraphicsRequirementsKHR) {
        LOGE("xrGetOpenGLESGraphicsRequirementsKHR not available");
        return false;
    }

    XrGraphicsRequirementsOpenGLESKHR gfx_reqs = {
        .type = XR_TYPE_GRAPHICS_REQUIREMENTS_OPENGL_ES_KHR
    };
    XR_CHECK(xrGetOpenGLESGraphicsRequirementsKHR(state->instance, state->system_id, &gfx_reqs),
             "xrGetOpenGLESGraphicsRequirementsKHR");
    LOGI("OpenXR GLES requirements: %u.%u.%u - %u.%u.%u",
         XR_VERSION_MAJOR(gfx_reqs.minApiVersionSupported),
         XR_VERSION_MINOR(gfx_reqs.minApiVersionSupported),
         XR_VERSION_PATCH(gfx_reqs.minApiVersionSupported),
         XR_VERSION_MAJOR(gfx_reqs.maxApiVersionSupported),
         XR_VERSION_MINOR(gfx_reqs.maxApiVersionSupported),
         XR_VERSION_PATCH(gfx_reqs.maxApiVersionSupported));
    return true;
}

static bool create_session(XrState* state) {
    XrGraphicsBindingOpenGLESAndroidKHR gfx_binding = {
        .type = XR_TYPE_GRAPHICS_BINDING_OPENGL_ES_ANDROID_KHR,
        .display = state->egl_display,
        .config = state->egl_config,
        .context = state->egl_context
    };

    XrSessionCreateInfo session_info = {
        .type = XR_TYPE_SESSION_CREATE_INFO,
        .next = &gfx_binding,
        .systemId = state->system_id
    };
    XR_CHECK(xrCreateSession(state->instance, &session_info, &state->session),
             "xrCreateSession");

    XrReferenceSpaceCreateInfo space_info = {
        .type = XR_TYPE_REFERENCE_SPACE_CREATE_INFO,
        .referenceSpaceType = XR_REFERENCE_SPACE_TYPE_LOCAL,
        .poseInReferenceSpace = {
            .orientation = {0.0f, 0.0f, 0.0f, 1.0f},
            .position = {0.0f, 0.0f, 0.0f}
        }
    };
    XR_CHECK(xrCreateReferenceSpace(state->session, &space_info, &state->local_space),
             "xrCreateReferenceSpace");

    LOGI("XR session and reference space created");
    return true;
}

static bool create_swapchains(XrState* state) {
    uint32_t config_count = 0;
    xrEnumerateViewConfigurationViews(state->instance, state->system_id,
                                       XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO,
                                       0, &config_count, NULL);
    if (config_count > XR_MAX_VIEWS) config_count = XR_MAX_VIEWS;
    state->view_count = config_count;

    for (uint32_t i = 0; i < config_count; i++) {
        state->view_configs[i].type = XR_TYPE_VIEW_CONFIGURATION_VIEW;
    }
    xrEnumerateViewConfigurationViews(state->instance, state->system_id,
                                       XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO,
                                       config_count, &config_count,
                                       state->view_configs);

    for (uint32_t i = 0; i < state->view_count; i++) {
        XrViewConfigurationView* vc = &state->view_configs[i];
        uint32_t w = vc->recommendedImageRectWidth;
        uint32_t h = vc->recommendedImageRectHeight;
        LOGI("View %u: %ux%u (samples: %u)", i, w, h, vc->recommendedSwapchainSampleCount);

        XrSwapchainCreateInfo sc_info = {
            .type = XR_TYPE_SWAPCHAIN_CREATE_INFO,
            .usageFlags = XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT |
                          XR_SWAPCHAIN_USAGE_SAMPLED_BIT,
            .format = GL_SRGB8_ALPHA8,
            .sampleCount = 1,
            .width = w,
            .height = h,
            .faceCount = 1,
            .arraySize = 1,
            .mipCount = 1
        };
        XR_CHECK(xrCreateSwapchain(state->session, &sc_info,
                                    &state->swapchains[i].handle),
                 "xrCreateSwapchain");
        state->swapchains[i].width = w;
        state->swapchains[i].height = h;

        uint32_t img_count = 0;
        xrEnumerateSwapchainImages(state->swapchains[i].handle, 0, &img_count, NULL);
        if (img_count > XR_MAX_SWAPCHAIN_IMAGES) img_count = XR_MAX_SWAPCHAIN_IMAGES;
        state->swapchains[i].image_count = img_count;

        for (uint32_t j = 0; j < img_count; j++) {
            state->swapchains[i].images[j].type = XR_TYPE_SWAPCHAIN_IMAGE_OPENGL_ES_KHR;
        }
        xrEnumerateSwapchainImages(state->swapchains[i].handle, img_count, &img_count,
                                    (XrSwapchainImageBaseHeader*)state->swapchains[i].images);

        /* Create FBO + depth renderbuffer per swapchain image */
        glGenFramebuffers(img_count, state->swapchains[i].framebuffers);
        glGenRenderbuffers(img_count, state->swapchains[i].depth_renderbuffers);
        for (uint32_t j = 0; j < img_count; j++) {
            glBindFramebuffer(GL_FRAMEBUFFER, state->swapchains[i].framebuffers[j]);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D,
                                   state->swapchains[i].images[j].image, 0);
            glBindRenderbuffer(GL_RENDERBUFFER, state->swapchains[i].depth_renderbuffers[j]);
            glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, w, h);
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT,
                                      GL_RENDERBUFFER,
                                      state->swapchains[i].depth_renderbuffers[j]);
            GLenum fbo_status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
            if (fbo_status != GL_FRAMEBUFFER_COMPLETE) {
                LOGE("FBO %u/%u incomplete: 0x%x", i, j, fbo_status);
                return false;
            }
        }
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    LOGI("Created %u swapchains", state->view_count);
    return true;
}

bool xr_init(XrState* state, JavaVM* vm, jobject activity) {
    memset(state, 0, sizeof(XrState));
    state->session_state = XR_SESSION_STATE_UNKNOWN;

    if (!init_loader(vm, activity)) return false;
    if (!create_instance(state, vm, activity)) return false;
    if (!get_system(state)) return false;
    if (!check_graphics_requirements(state)) return false;
    if (!create_egl_context(state)) return false;
    if (!create_session(state)) return false;
    if (!create_swapchains(state)) return false;

    LOGI("XR initialized");
    return true;
}

void xr_destroy(XrState* state) {
    if (state->is_session_running) {
        xr_end_session(state);
    }

    for (uint32_t i = 0; i < state->view_count; i++) {
        XrSwapchainState* sc = &state->swapchains[i];
        if (sc->image_count > 0) {
            glDeleteFramebuffers(sc->image_count, sc->framebuffers);
            glDeleteRenderbuffers(sc->image_count, sc->depth_renderbuffers);
        }
        if (sc->handle != XR_NULL_HANDLE) {
            xrDestroySwapchain(sc->handle);
        }
    }

    if (state->local_space != XR_NULL_HANDLE) xrDestroySpace(state->local_space);
    if (state->session != XR_NULL_HANDLE) xrDestroySession(state->session);
    if (state->instance != XR_NULL_HANDLE) xrDestroyInstance(state->instance);

    if (state->egl_surface != EGL_NO_SURFACE) {
        eglDestroySurface(state->egl_display, state->egl_surface);
    }
    if (state->egl_context != EGL_NO_CONTEXT) {
        eglMakeCurrent(state->egl_display, EGL_NO_SURFACE,
                        EGL_NO_SURFACE, EGL_NO_CONTEXT);
        eglDestroyContext(state->egl_display, state->egl_context);
    }
    if (state->egl_display != EGL_NO_DISPLAY) {
        eglTerminate(state->egl_display);
    }

    memset(state, 0, sizeof(XrState));
    LOGI("XR destroyed");
}

bool xr_poll_events(XrState* state) {
    XrEventDataBuffer event = {.type = XR_TYPE_EVENT_DATA_BUFFER};
    while (xrPollEvent(state->instance, &event) == XR_SUCCESS) {
        switch (event.type) {
            case XR_TYPE_EVENT_DATA_SESSION_STATE_CHANGED: {
                XrEventDataSessionStateChanged* sc =
                    (XrEventDataSessionStateChanged*)&event;
                state->session_state = sc->state;
                LOGI("Session state: %d", state->session_state);

                if (sc->state == XR_SESSION_STATE_EXITING ||
                    sc->state == XR_SESSION_STATE_LOSS_PENDING) {
                    return false;
                }
                break;
            }
            case XR_TYPE_EVENT_DATA_INSTANCE_LOSS_PENDING:
                LOGE("Instance loss pending");
                return false;
            default:
                break;
        }
        event.type = XR_TYPE_EVENT_DATA_BUFFER;
    }
    return true;
}

bool xr_begin_session(XrState* state) {
    XrSessionBeginInfo begin_info = {
        .type = XR_TYPE_SESSION_BEGIN_INFO,
        .primaryViewConfigurationType = XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO
    };
    XrResult r = xrBeginSession(state->session, &begin_info);
    if (XR_FAILED(r)) {
        LOGE("xrBeginSession failed: %d", r);
        return false;
    }
    state->is_session_running = true;
    LOGI("Session started");
    return true;
}

void xr_end_session(XrState* state) {
    if (state->is_session_running) {
        xrEndSession(state->session);
        state->is_session_running = false;
        LOGI("Session ended");
    }
}

bool xr_run_frame(XrState* state) {
    XrFrameWaitInfo wait_info = {.type = XR_TYPE_FRAME_WAIT_INFO};
    XrFrameState frame_state = {.type = XR_TYPE_FRAME_STATE};
    XrResult r = xrWaitFrame(state->session, &wait_info, &frame_state);
    if (XR_FAILED(r)) {
        LOGE("xrWaitFrame failed: %d", r);
        return false;
    }

    XrFrameBeginInfo begin_info = {.type = XR_TYPE_FRAME_BEGIN_INFO};
    r = xrBeginFrame(state->session, &begin_info);
    if (XR_FAILED(r)) {
        LOGE("xrBeginFrame failed: %d", r);
        return false;
    }

    XrCompositionLayerProjectionView projection_views[XR_MAX_VIEWS] = {0};
    XrCompositionLayerProjection layer = {
        .type = XR_TYPE_COMPOSITION_LAYER_PROJECTION,
        .space = state->local_space,
        .viewCount = state->view_count,
        .views = projection_views
    };

    bool should_render = frame_state.shouldRender == XR_TRUE;
    if (should_render) {
        XrView views[XR_MAX_VIEWS] = {0};
        for (uint32_t i = 0; i < state->view_count; i++) {
            views[i].type = XR_TYPE_VIEW;
        }

        XrViewLocateInfo locate_info = {
            .type = XR_TYPE_VIEW_LOCATE_INFO,
            .viewConfigurationType = XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO,
            .displayTime = frame_state.predictedDisplayTime,
            .space = state->local_space
        };
        XrViewState view_state = {.type = XR_TYPE_VIEW_STATE};
        uint32_t view_count = 0;
        XrResult lv_r = xrLocateViews(state->session, &locate_info, &view_state,
                                       state->view_count, &view_count, views);
        if (XR_FAILED(lv_r) ||
            !(view_state.viewStateFlags & XR_VIEW_STATE_POSITION_VALID_BIT)) {
            should_render = false;
        }

        /* Derive time in seconds for animation */
        static XrTime start_time = 0;
        if (start_time == 0) start_time = frame_state.predictedDisplayTime;
        float time_sec = (float)(frame_state.predictedDisplayTime - start_time) / 1e9f;

        for (uint32_t i = 0; i < view_count; i++) {
            XrSwapchainState* sc = &state->swapchains[i];

            uint32_t img_index;
            XrSwapchainImageAcquireInfo acq_info = {
                .type = XR_TYPE_SWAPCHAIN_IMAGE_ACQUIRE_INFO
            };
            XrResult acq_r = xrAcquireSwapchainImage(sc->handle, &acq_info, &img_index);
            if (XR_FAILED(acq_r)) {
                LOGE("xrAcquireSwapchainImage failed: %d", acq_r);
                continue;
            }

            XrSwapchainImageWaitInfo wait_img = {
                .type = XR_TYPE_SWAPCHAIN_IMAGE_WAIT_INFO,
                .timeout = XR_INFINITE_DURATION
            };
            XrResult wait_r = xrWaitSwapchainImage(sc->handle, &wait_img);
            if (XR_FAILED(wait_r)) {
                LOGE("xrWaitSwapchainImage failed: %d", wait_r);
                xrReleaseSwapchainImage(sc->handle,
                    &(XrSwapchainImageReleaseInfo){.type = XR_TYPE_SWAPCHAIN_IMAGE_RELEASE_INFO});
                continue;
            }

            glBindFramebuffer(GL_FRAMEBUFFER, sc->framebuffers[img_index]);
            glViewport(0, 0, sc->width, sc->height);
            glClearColor(0.05f, 0.05f, 0.1f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            glEnable(GL_DEPTH_TEST);

            /* Build view-projection matrix */
            float view_mat[16], proj_mat[16], view_proj[16];
            xr_math_pose_to_view(view_mat, views[i].pose);
            xr_math_projection_fov(proj_mat, views[i].fov, 0.05f, 100.0f);
            xr_math_mul(view_proj, proj_mat, view_mat);

            xr_renderer_draw(&g_xr_renderer, view_proj, time_sec);

            glBindFramebuffer(GL_FRAMEBUFFER, 0);

            XrSwapchainImageReleaseInfo rel_info = {
                .type = XR_TYPE_SWAPCHAIN_IMAGE_RELEASE_INFO
            };
            xrReleaseSwapchainImage(sc->handle, &rel_info);

            projection_views[i] = (XrCompositionLayerProjectionView){
                .type = XR_TYPE_COMPOSITION_LAYER_PROJECTION_VIEW,
                .pose = views[i].pose,
                .fov = views[i].fov,
                .subImage = {
                    .swapchain = sc->handle,
                    .imageRect = {
                        .offset = {0, 0},
                        .extent = {(int32_t)sc->width, (int32_t)sc->height}
                    }
                }
            };
        }
    }

    const XrCompositionLayerBaseHeader* layers[] = {
        (XrCompositionLayerBaseHeader*)&layer
    };
    XrFrameEndInfo end_info = {
        .type = XR_TYPE_FRAME_END_INFO,
        .displayTime = frame_state.predictedDisplayTime,
        .environmentBlendMode = XR_ENVIRONMENT_BLEND_MODE_OPAQUE,
        .layerCount = should_render ? 1 : 0,
        .layers = should_render ? layers : NULL
    };
    r = xrEndFrame(state->session, &end_info);
    if (XR_FAILED(r)) {
        LOGE("xrEndFrame failed: %d", r);
    }

    return true;
}
