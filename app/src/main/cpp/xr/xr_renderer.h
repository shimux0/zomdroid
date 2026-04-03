#ifndef ZOMDROID_XR_RENDERER_H
#define ZOMDROID_XR_RENDERER_H

#include <stdbool.h>
#include <GLES3/gl3.h>

typedef struct {
    GLuint program;
    GLint loc_mvp;
    GLuint vao;
    GLuint vbo;
    GLuint ibo;
    bool is_initialized;
} XrRendererState;

bool xr_renderer_init(XrRendererState* state);
void xr_renderer_destroy(XrRendererState* state);
void xr_renderer_draw(XrRendererState* state, const float* view_proj, float time);

#endif //ZOMDROID_XR_RENDERER_H
