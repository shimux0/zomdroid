#ifndef ZOMDROID_XR_MATH_H
#define ZOMDROID_XR_MATH_H

#include <string.h>
#include <math.h>

#define XR_USE_GRAPHICS_API_OPENGL_ES
#define XR_USE_PLATFORM_ANDROID
#include <openxr/openxr.h>

static inline void xr_math_identity(float* m) {
    memset(m, 0, 16 * sizeof(float));
    m[0] = m[5] = m[10] = m[15] = 1.0f;
}

static inline void xr_math_mul(float* result, const float* a, const float* b) {
    float tmp[16];
    for (int i = 0; i < 4; i++) {
        for (int j = 0; j < 4; j++) {
            tmp[j * 4 + i] = 0.0f;
            for (int k = 0; k < 4; k++) {
                tmp[j * 4 + i] += a[k * 4 + i] * b[j * 4 + k];
            }
        }
    }
    memcpy(result, tmp, 16 * sizeof(float));
}

static inline void xr_math_translation(float* m, float x, float y, float z) {
    xr_math_identity(m);
    m[12] = x;
    m[13] = y;
    m[14] = z;
}

static inline void xr_math_scale(float* m, float sx, float sy, float sz) {
    xr_math_identity(m);
    m[0] = sx;
    m[5] = sy;
    m[10] = sz;
}

static inline void xr_math_rotation_y(float* m, float radians) {
    xr_math_identity(m);
    float c = cosf(radians);
    float s = sinf(radians);
    m[0] = c;
    m[2] = s;
    m[8] = -s;
    m[10] = c;
}

/* Asymmetric projection from OpenXR field-of-view angles */
static inline void xr_math_projection_fov(float* m, XrFovf fov,
                                           float near_z, float far_z) {
    float left = tanf(fov.angleLeft) * near_z;
    float right = tanf(fov.angleRight) * near_z;
    float up = tanf(fov.angleUp) * near_z;
    float down = tanf(fov.angleDown) * near_z;

    float width = right - left;
    float height = up - down;

    memset(m, 0, 16 * sizeof(float));
    m[0]  = 2.0f * near_z / width;
    m[5]  = 2.0f * near_z / height;
    m[8]  = (right + left) / width;
    m[9]  = (up + down) / height;
    m[10] = -(far_z + near_z) / (far_z - near_z);
    m[11] = -1.0f;
    m[14] = -(2.0f * far_z * near_z) / (far_z - near_z);
}

/* Convert OpenXR pose (position + quaternion) to a view matrix (inverse of pose) */
static inline void xr_math_pose_to_view(float* m, XrPosef pose) {
    float x = pose.orientation.x;
    float y = pose.orientation.y;
    float z = pose.orientation.z;
    float w = pose.orientation.w;

    float r00 = 1.0f - 2.0f * (y * y + z * z);
    float r01 = 2.0f * (x * y - z * w);
    float r02 = 2.0f * (x * z + y * w);
    float r10 = 2.0f * (x * y + z * w);
    float r11 = 1.0f - 2.0f * (x * x + z * z);
    float r12 = 2.0f * (y * z - x * w);
    float r20 = 2.0f * (x * z - y * w);
    float r21 = 2.0f * (y * z + x * w);
    float r22 = 1.0f - 2.0f * (x * x + y * y);

    float px = pose.position.x;
    float py = pose.position.y;
    float pz = pose.position.z;

    /* View matrix = inverse of pose transform (transposed rotation, negated translation) */
    m[0]  = r00; m[4]  = r10; m[8]  = r20; m[12] = -(r00 * px + r10 * py + r20 * pz);
    m[1]  = r01; m[5]  = r11; m[9]  = r21; m[13] = -(r01 * px + r11 * py + r21 * pz);
    m[2]  = r02; m[6]  = r12; m[10] = r22; m[14] = -(r02 * px + r12 * py + r22 * pz);
    m[3]  = 0.0f; m[7] = 0.0f; m[11] = 0.0f; m[15] = 1.0f;
}

#endif //ZOMDROID_XR_MATH_H
