#include <string.h>
#include <math.h>
#include <GLES3/gl3.h>

#include "xr_renderer.h"
#include "xr_math.h"
#include "logger.h"

#define LOG_TAG "zomdroid-xr-renderer"

static const char* vertex_shader_src =
    "#version 300 es\n"
    "uniform mat4 u_mvp;\n"
    "in vec3 a_position;\n"
    "in vec3 a_color;\n"
    "out vec3 v_color;\n"
    "void main() {\n"
    "    gl_Position = u_mvp * vec4(a_position, 1.0);\n"
    "    v_color = a_color;\n"
    "}\n";

static const char* fragment_shader_src =
    "#version 300 es\n"
    "precision mediump float;\n"
    "in vec3 v_color;\n"
    "out vec4 frag_color;\n"
    "void main() {\n"
    "    frag_color = vec4(v_color, 1.0);\n"
    "}\n";

/* Cube: 24 vertices (4 per face), position + color */
static const float cube_vertices[] = {
    /* Front face (red) */
    -0.25f, -0.25f,  0.25f,  0.9f, 0.2f, 0.2f,
     0.25f, -0.25f,  0.25f,  0.9f, 0.2f, 0.2f,
     0.25f,  0.25f,  0.25f,  0.9f, 0.2f, 0.2f,
    -0.25f,  0.25f,  0.25f,  0.9f, 0.2f, 0.2f,
    /* Back face (green) */
     0.25f, -0.25f, -0.25f,  0.2f, 0.9f, 0.2f,
    -0.25f, -0.25f, -0.25f,  0.2f, 0.9f, 0.2f,
    -0.25f,  0.25f, -0.25f,  0.2f, 0.9f, 0.2f,
     0.25f,  0.25f, -0.25f,  0.2f, 0.9f, 0.2f,
    /* Top face (blue) */
    -0.25f,  0.25f,  0.25f,  0.2f, 0.2f, 0.9f,
     0.25f,  0.25f,  0.25f,  0.2f, 0.2f, 0.9f,
     0.25f,  0.25f, -0.25f,  0.2f, 0.2f, 0.9f,
    -0.25f,  0.25f, -0.25f,  0.2f, 0.2f, 0.9f,
    /* Bottom face (yellow) */
    -0.25f, -0.25f, -0.25f,  0.9f, 0.9f, 0.2f,
     0.25f, -0.25f, -0.25f,  0.9f, 0.9f, 0.2f,
     0.25f, -0.25f,  0.25f,  0.9f, 0.9f, 0.2f,
    -0.25f, -0.25f,  0.25f,  0.9f, 0.9f, 0.2f,
    /* Right face (magenta) */
     0.25f, -0.25f,  0.25f,  0.9f, 0.2f, 0.9f,
     0.25f, -0.25f, -0.25f,  0.9f, 0.2f, 0.9f,
     0.25f,  0.25f, -0.25f,  0.9f, 0.2f, 0.9f,
     0.25f,  0.25f,  0.25f,  0.9f, 0.2f, 0.9f,
    /* Left face (cyan) */
    -0.25f, -0.25f, -0.25f,  0.2f, 0.9f, 0.9f,
    -0.25f, -0.25f,  0.25f,  0.2f, 0.9f, 0.9f,
    -0.25f,  0.25f,  0.25f,  0.2f, 0.9f, 0.9f,
    -0.25f,  0.25f, -0.25f,  0.2f, 0.9f, 0.9f,
};

static const unsigned short cube_indices[] = {
     0,  1,  2,   2,  3,  0,  /* front */
     4,  5,  6,   6,  7,  4,  /* back */
     8,  9, 10,  10, 11,  8,  /* top */
    12, 13, 14,  14, 15, 12,  /* bottom */
    16, 17, 18,  18, 19, 16,  /* right */
    20, 21, 22,  22, 23, 20,  /* left */
};

static GLuint compile_shader(GLenum type, const char* source) {
    GLuint shader = glCreateShader(type);
    glShaderSource(shader, 1, &source, NULL);
    glCompileShader(shader);

    GLint status;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &status);
    if (!status) {
        char log[512];
        glGetShaderInfoLog(shader, sizeof(log), NULL, log);
        LOGE("Shader compile error: %s", log);
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

bool xr_renderer_init(XrRendererState* state) {
    memset(state, 0, sizeof(XrRendererState));

    GLuint vs = compile_shader(GL_VERTEX_SHADER, vertex_shader_src);
    GLuint fs = compile_shader(GL_FRAGMENT_SHADER, fragment_shader_src);
    if (!vs || !fs) {
        if (vs) glDeleteShader(vs);
        if (fs) glDeleteShader(fs);
        return false;
    }

    state->program = glCreateProgram();
    glAttachShader(state->program, vs);
    glAttachShader(state->program, fs);
    glLinkProgram(state->program);
    glDeleteShader(vs);
    glDeleteShader(fs);

    GLint status;
    glGetProgramiv(state->program, GL_LINK_STATUS, &status);
    if (!status) {
        char log[512];
        glGetProgramInfoLog(state->program, sizeof(log), NULL, log);
        LOGE("Program link error: %s", log);
        return false;
    }

    state->loc_mvp = glGetUniformLocation(state->program, "u_mvp");

    glGenVertexArrays(1, &state->vao);
    glBindVertexArray(state->vao);

    glGenBuffers(1, &state->vbo);
    glBindBuffer(GL_ARRAY_BUFFER, state->vbo);
    glBufferData(GL_ARRAY_BUFFER, sizeof(cube_vertices), cube_vertices, GL_STATIC_DRAW);

    glGenBuffers(1, &state->ibo);
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, state->ibo);
    glBufferData(GL_ELEMENT_ARRAY_BUFFER, sizeof(cube_indices), cube_indices, GL_STATIC_DRAW);

    GLint pos_loc = glGetAttribLocation(state->program, "a_position");
    GLint col_loc = glGetAttribLocation(state->program, "a_color");

    glEnableVertexAttribArray(pos_loc);
    glVertexAttribPointer(pos_loc, 3, GL_FLOAT, GL_FALSE, 6 * sizeof(float), (void*)0);

    glEnableVertexAttribArray(col_loc);
    glVertexAttribPointer(col_loc, 3, GL_FLOAT, GL_FALSE, 6 * sizeof(float),
                          (void*)(3 * sizeof(float)));

    glBindVertexArray(0);

    state->is_initialized = true;
    LOGI("Renderer initialized");
    return true;
}

void xr_renderer_destroy(XrRendererState* state) {
    if (!state->is_initialized) return;
    glDeleteVertexArrays(1, &state->vao);
    glDeleteBuffers(1, &state->vbo);
    glDeleteBuffers(1, &state->ibo);
    glDeleteProgram(state->program);
    memset(state, 0, sizeof(XrRendererState));
}

void xr_renderer_draw(XrRendererState* state, const float* view_proj, float time) {
    if (!state->is_initialized) return;

    glUseProgram(state->program);
    glBindVertexArray(state->vao);

    /* Cube 1: spinning, 1.5m in front of origin */
    float model[16], mvp[16];
    float rot[16], trans[16];
    xr_math_rotation_y(rot, time * 0.8f);
    xr_math_translation(trans, 0.0f, 0.0f, -1.5f);
    xr_math_mul(model, trans, rot);
    xr_math_mul(mvp, view_proj, model);
    glUniformMatrix4fv(state->loc_mvp, 1, GL_FALSE, mvp);
    glDrawElements(GL_TRIANGLES, 36, GL_UNSIGNED_SHORT, 0);

    /* Cube 2: offset, slower rotation */
    xr_math_rotation_y(rot, -time * 0.4f);
    xr_math_translation(trans, 0.8f, 0.3f, -2.0f);
    xr_math_mul(model, trans, rot);
    xr_math_mul(mvp, view_proj, model);
    glUniformMatrix4fv(state->loc_mvp, 1, GL_FALSE, mvp);
    glDrawElements(GL_TRIANGLES, 36, GL_UNSIGNED_SHORT, 0);

    /* Cube 3: other side */
    xr_math_rotation_y(rot, time * 0.6f);
    xr_math_translation(trans, -0.7f, -0.2f, -1.8f);
    xr_math_mul(model, trans, rot);
    xr_math_mul(mvp, view_proj, model);
    glUniformMatrix4fv(state->loc_mvp, 1, GL_FALSE, mvp);
    glDrawElements(GL_TRIANGLES, 36, GL_UNSIGNED_SHORT, 0);

    /* Ground plane quad (dark gray) */
    float ground_model[16];
    float ground_scale[16];
    xr_math_translation(trans, 0.0f, -0.8f, -1.5f);
    xr_math_scale(ground_scale, 6.0f, 0.02f, 6.0f);
    xr_math_mul(ground_model, trans, ground_scale);
    xr_math_mul(mvp, view_proj, ground_model);
    glUniformMatrix4fv(state->loc_mvp, 1, GL_FALSE, mvp);
    glDrawElements(GL_TRIANGLES, 36, GL_UNSIGNED_SHORT, 0);

    glBindVertexArray(0);
    glUseProgram(0);
}
