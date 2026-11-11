// 0.0.130: the bridge to mix/stem_dsp.cpp (class org.nooklin.music.playback.stems.NativeStemDsp). The arrays come in as they are; the large
// ones are not moved by the garbage collector, so Get...Elements normally gives the memory itself and not a copy.
#include <jni.h>

#include "../mix/stem_dsp.h"

using namespace nooklin::mix::stemdsp;

namespace {

// Holds the elements of a float array for the length of a call; mode 0 copies changes back, JNI_ABORT leaves the array as it was
struct Floats {
    JNIEnv* env;
    jfloatArray array;
    jfloat* p;
    jint mode;
    Floats(JNIEnv* e, jfloatArray a, bool output) : env(e), array(a), p(e->GetFloatArrayElements(a, nullptr)), mode(output ? 0 : JNI_ABORT) {}
    ~Floats() { if (p) env->ReleaseFloatArrayElements(array, p, mode); }
    Floats(const Floats&) = delete;
    Floats& operator=(const Floats&) = delete;
};

void fail(JNIEnv* env, const char* what) {
    jclass c = env->FindClass("java/lang/IllegalStateException");
    if (c) env->ThrowNew(c, what);
}

}  // namespace

extern "C" {

#define DSP(ret, name) JNIEXPORT ret JNICALL Java_org_nooklin_music_playback_stems_NativeStemDsp_##name

DSP(void, nativeStft)(JNIEnv* env, jclass, jfloatArray left, jfloatArray right, jint frames, jint stride, jfloatArray spec0,
                      jfloatArray spec1, jfloatArray mags, jint threads) {
    const jint samples = env->GetArrayLength(left);
    Floats l(env, left, false), r(env, right, false), s0(env, spec0, true), s1(env, spec1, true), m(env, mags, true);
    if (!l.p || !r.p || !s0.p || !s1.p || !m.p) { fail(env, "stem dsp: could not get the arrays"); return; }
    stft(l.p, r.p, samples, frames, stride, s0.p, s1.p, m.p, threads);
}

DSP(void, nativeEnergy)(JNIEnv* env, jclass, jfloatArray spec0, jfloatArray spec1, jfloatArray e0, jfloatArray e1, jfloatArray e2,
                        jfloatArray e3, jint frames, jint stride, jint stem, jfloatArray out, jint threads) {
    Floats s0(env, spec0, false), s1(env, spec1, false), a(env, e0, false), b(env, e1, false), c(env, e2, false), d(env, e3, false),
        o(env, out, true);
    if (!s0.p || !s1.p || !a.p || !b.p || !c.p || !d.p || !o.p) { fail(env, "stem dsp: could not get the arrays"); return; }
    const float* est[kStems] = {a.p, b.p, c.p, d.p};
    energy(s0.p, s1.p, est, frames, stride, stem, o.p, threads);
}

DSP(void, nativeRender)(JNIEnv* env, jclass, jfloatArray spec0, jfloatArray spec1, jfloatArray e0, jfloatArray e1, jfloatArray e2,
                        jfloatArray e3, jint frames, jint stride, jfloatArray gains, jfloatArray outL, jfloatArray outR, jint threads) {
    const jint samples = env->GetArrayLength(outL);
    Floats s0(env, spec0, false), s1(env, spec1, false), a(env, e0, false), b(env, e1, false), c(env, e2, false), d(env, e3, false),
        g(env, gains, false), l(env, outL, true), r(env, outR, true);
    if (!s0.p || !s1.p || !a.p || !b.p || !c.p || !d.p || !g.p || !l.p || !r.p) { fail(env, "stem dsp: could not get the arrays"); return; }
    const float* est[kStems] = {a.p, b.p, c.p, d.p};
    render(s0.p, s1.p, est, frames, stride, samples, g.p, l.p, r.p, threads);
}

}  // extern "C"
