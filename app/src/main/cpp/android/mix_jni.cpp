// JNI bridge and audio output of the Auto Mix engine. One Oboe output stream drives Engine::render on the audio thread;
// everything else is called from the Kotlin side (class org.nooklin.music.playback.mix.NativeMix).
#include <android/log.h>
#include <jni.h>
#include <oboe/Oboe.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <map>
#include <memory>
#include <mutex>
#include <thread>

#include "../mix/analyzer.h"
#include "../mix/alac.h"
#include "../mix/engine.h"
#include "../mix/harmony.h"
#include "../mix/planner.h"
#include "pcm_buffer.h"
#include "tap_ring.h"

using namespace nooklin::mix;

namespace {

constexpr int kRate = PcmBuffer::kEngineRate;

std::mutex g_lock;  // guards the stream and the buffer registry (never taken on the audio thread)
std::unique_ptr<Engine> g_engine;
std::shared_ptr<oboe::AudioStream> g_stream;
int32_t g_deviceId = oboe::kUnspecified;  // 0.0.131: the chosen output device (guarded by g_lock); kUnspecified lets Android decide
std::map<jlong, std::shared_ptr<PcmBuffer>> g_buffers;
std::mutex g_statusLock;  // guards the last good status snapshot (never taken on the audio thread)
double g_lastStatus[8] = {};
bool g_haveStatus = false;
jlong g_nextHandle = 1;
TapRing g_tap;  // 0.0.110: a copy of what the engine plays, for AirPlay

class Output : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
public:
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream*, void* data, int32_t frames) override {
        g_engine->render(static_cast<float*>(data), frames);
        g_tap.push(static_cast<float*>(data), frames);
        return oboe::DataCallbackResult::Continue;
    }
    // The device went away (headphones out, route change): open a new stream on the default device
    void onErrorAfterClose(oboe::AudioStream*, oboe::Result) override {
        std::thread([] {
            std::lock_guard<std::mutex> g(g_lock);
            g_stream.reset();
            openLocked();
        }).detach();
    }
    static bool openLocked();
};
Output g_output;

bool Output::openLocked() {
    if (g_stream) return true;
    oboe::AudioStreamBuilder b;
    b.setDirection(oboe::Direction::Output)
        ->setPerformanceMode(oboe::PerformanceMode::PowerSaving)
        ->setSharingMode(oboe::SharingMode::Shared)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(oboe::ChannelCount::Stereo)
        ->setSampleRate(kRate)
        ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
        ->setUsage(oboe::Usage::Media)
        ->setContentType(oboe::ContentType::Music)
        ->setDataCallback(&g_output)
        ->setErrorCallback(&g_output);
    if (g_deviceId != oboe::kUnspecified) b.setDeviceId(g_deviceId);
    std::shared_ptr<oboe::AudioStream> s;
    oboe::Result r = b.openStream(s);
    if (r != oboe::Result::OK && g_deviceId != oboe::kUnspecified) {
        // The chosen device is gone: back to the default one
        g_deviceId = oboe::kUnspecified;
        b.setDeviceId(oboe::kUnspecified);
        r = b.openStream(s);
    }
    if (r != oboe::Result::OK) return false;
    g_stream = s;
    return g_stream->requestStart() == oboe::Result::OK;
}

bool ensureRunning() {
    std::lock_guard<std::mutex> g(g_lock);
    if (!g_engine) g_engine = std::make_unique<Engine>(kRate);
    if (g_stream && g_stream->getState() == oboe::StreamState::Started) return true;
    if (g_stream && g_stream->requestStart() == oboe::Result::OK) return true;
    g_stream.reset();
    return Output::openLocked();
}

std::shared_ptr<PcmBuffer> bufferOf(jlong h) {
    std::lock_guard<std::mutex> g(g_lock);
    auto it = g_buffers.find(h);
    return it == g_buffers.end() ? nullptr : it->second;
}

// ---- packed maps and plans ------------------------------------------------------------------------------------------
// map:  [bpm, confidence, downbeatPhase, phrasePhase, cueIn, cueOut, duration, beatCount, beats..., energyCount,
//        energy..., chromaCount, chroma..., sectionCount, (beat, high, ratio)..., loudness, vocalCount, vocal...]  (the last parts are missing in an older map)
// plan: [kind, startBeatA, startBeatB, lengthBeats, rampBeats, tempoRatioA, beatFactor, fallbackStartA, fallbackStartB,
//        fallbackSeconds, energyA, energyB, keyClash, cost, keyA, keyB]
jdoubleArray packMap(JNIEnv* env, const TrackMap& m) {
    std::vector<double> v = {m.bpm, m.confidence, (double)m.downbeatPhase, (double)m.phrasePhase, m.cueIn, m.cueOut,
                             m.duration, (double)m.beats.size()};
    v.insert(v.end(), m.beats.begin(), m.beats.end());
    v.push_back((double)m.energy.size());
    v.insert(v.end(), m.energy.begin(), m.energy.end());
    v.push_back((double)m.chroma.size());
    v.insert(v.end(), m.chroma.begin(), m.chroma.end());
    v.push_back((double)m.sections.size());
    for (const auto& sec : m.sections) {
        v.push_back((double)sec.beat);
        v.push_back(sec.high ? 1.0 : 0.0);
        v.push_back((double)sec.ratio);
    }
    v.push_back(m.loudness);
    v.push_back((double)m.vocal.size());
    v.insert(v.end(), m.vocal.begin(), m.vocal.end());
    v.push_back((double)(m.tempoEvidence.size() / 2));
    v.insert(v.end(), m.tempoEvidence.begin(), m.tempoEvidence.end());
    // 0.0.103: cue fields, after the tempo evidence: [count, cueConfidence, phraseBeats, introEnd, outroStart, usableIntroBeats,
    // usableOutroBeats, energyStart, energyEnd, swing]; count 0 for a map without them
    if (m.hasCues) {
        v.push_back(9.0);
        v.insert(v.end(), {m.cueConfidence, (double)m.phraseBeats, m.introEnd, m.outroStart, (double)m.usableIntroBeats,
                           (double)m.usableOutroBeats, m.energyStart, m.energyEnd, m.swing});
    }
    jdoubleArray r = env->NewDoubleArray((jsize)v.size());
    env->SetDoubleArrayRegion(r, 0, (jsize)v.size(), v.data());
    return r;
}

TrackMap unpackMap(JNIEnv* env, jdoubleArray a) {
    TrackMap m;
    const jsize len = a ? env->GetArrayLength(a) : 0;
    if (len < 8) return m;
    std::vector<double> v((size_t)len);
    env->GetDoubleArrayRegion(a, 0, len, v.data());
    m.bpm = v[0];
    m.confidence = v[1];
    m.downbeatPhase = (int)v[2];
    m.phrasePhase = (int)v[3];
    m.cueIn = v[4];
    m.cueOut = v[5];
    m.duration = v[6];
    const size_t n = std::min((size_t)v[7], (size_t)len - 8);
    m.beats.assign(v.begin() + 8, v.begin() + 8 + (long)n);
    size_t at = 8 + n;
    for (std::vector<float>* part : {&m.energy, &m.chroma}) {
        if (at >= (size_t)len) break;
        const size_t cnt = std::min((size_t)v[at], (size_t)len - at - 1);
        part->assign(v.begin() + (long)at + 1, v.begin() + (long)at + 1 + (long)cnt);
        at += 1 + cnt;
    }
    if (at < (size_t)len) {
        const size_t cnt = std::min((size_t)v[at], ((size_t)len - at - 1) / 3);
        for (size_t i = 0; i < cnt; i++) {
            TrackMap::Section sec;
            sec.beat = (long)v[at + 1 + 3 * i];
            sec.high = v[at + 2 + 3 * i] > 0.5;
            sec.ratio = (float)v[at + 3 + 3 * i];
            m.sections.push_back(sec);
        }
        at += 1 + cnt * 3;
        if (at < (size_t)len) {
            m.loudness = v[at++];
            if (at < (size_t)len) {
                const size_t vc = std::min((size_t)v[at], (size_t)len - at - 1);
                m.vocal.assign(v.begin() + (long)at + 1, v.begin() + (long)at + 1 + (long)vc);
                at += 1 + vc;
                if (at < (size_t)len) {
                    const size_t ec = std::min((size_t)v[at], ((size_t)len - at - 1) / 2);
                    m.tempoEvidence.assign(v.begin() + (long)at + 1, v.begin() + (long)at + 1 + (long)(2 * ec));
                    at += 1 + 2 * ec;
                    if (at + 10 <= (size_t)len && v[at] >= 9.0) {
                        m.cueConfidence = v[at + 1];
                        m.phraseBeats = (int)v[at + 2];
                        m.introEnd = v[at + 3];
                        m.outroStart = v[at + 4];
                        m.usableIntroBeats = (int)v[at + 5];
                        m.usableOutroBeats = (int)v[at + 6];
                        m.energyStart = v[at + 7];
                        m.energyEnd = v[at + 8];
                        m.swing = v[at + 9];
                        m.hasCues = true;
                    }
                }
            }
        }
    }
    return m;
}

jdoubleArray packPlan(JNIEnv* env, const TransitionPlan& p) {
    const double v[28] = {(double)(int)p.kind, p.startBeatA, p.startBeatB, (double)p.lengthBeats, (double)p.rampBeats,
                          p.tempoRatioA, p.beatFactor, p.fallbackStartA, p.fallbackStartB, p.fallbackSeconds,
                          p.energyA, p.energyB, p.keyClash, p.cost, (double)p.keyA, (double)p.keyB,
                          p.tempoRatioB, (double)p.glideBeats, p.entrySecB, (double)p.flags, (double)p.style, p.vocalA, p.vocalB, (double)p.genre, p.vocalOverlap, p.loopBeat, (double)p.loopBeats, p.bassSwapBeat};
    jdoubleArray r = env->NewDoubleArray(28);
    env->SetDoubleArrayRegion(r, 0, 28, v);
    return r;
}

TransitionPlan unpackPlan(JNIEnv* env, jdoubleArray a) {
    TransitionPlan p;
    double v[28] = {};
    v[27] = -1.0;
    v[21] = v[22] = v[24] = -1.0;
    v[14] = v[15] = -1.0;
    v[16] = 1.0;
    v[17] = 8.0;
    if (a && env->GetArrayLength(a) >= 28) env->GetDoubleArrayRegion(a, 0, 28, v);
    else if (a && env->GetArrayLength(a) >= 27) env->GetDoubleArrayRegion(a, 0, 27, v);
    else if (a && env->GetArrayLength(a) >= 25) env->GetDoubleArrayRegion(a, 0, 25, v);
    else if (a && env->GetArrayLength(a) >= 24) env->GetDoubleArrayRegion(a, 0, 24, v);
    else if (a && env->GetArrayLength(a) >= 21) env->GetDoubleArrayRegion(a, 0, 21, v);
    else if (a && env->GetArrayLength(a) >= 20) env->GetDoubleArrayRegion(a, 0, 20, v);
    else if (a && env->GetArrayLength(a) >= 16) env->GetDoubleArrayRegion(a, 0, 16, v);
    else if (a && env->GetArrayLength(a) >= 10) env->GetDoubleArrayRegion(a, 0, 10, v);
    p.kind = (Template)(int)v[0];
    p.startBeatA = v[1];
    p.startBeatB = v[2];
    p.lengthBeats = (int)v[3];
    p.rampBeats = (int)v[4];
    p.tempoRatioA = v[5];
    p.beatFactor = v[6];
    p.fallbackStartA = v[7];
    p.fallbackStartB = v[8];
    p.fallbackSeconds = v[9];
    p.tempoRatioB = v[16];
    p.glideBeats = (int)v[17];
    p.entrySecB = v[18];
    p.flags = (int)v[19];
    p.style = (int)v[20];
    p.vocalA = v[21];
    p.vocalB = v[22];
    p.genre = (int)v[23];
    p.vocalOverlap = v[24];
    p.loopBeat = v[25];
    p.loopBeats = (int)v[26];
    p.bassSwapBeat = v[27];
    return p;
}

// Commands are taken by the audio thread within a few milliseconds; wait for that (never forever)
template <typename F>
bool command(F&& f) {
    if (!ensureRunning()) return false;
    for (int i = 0; i < 100; i++) {
        if (g_engine->idleCommands() && f()) return true;
        std::this_thread::sleep_for(std::chrono::milliseconds(5));
    }
    return false;
}

}  // namespace

extern "C" {

#define MIX(ret, name) JNIEXPORT ret JNICALL Java_org_nooklin_music_playback_mix_NativeMix_##name

// 0.0.110: AirPlay takes what the engine plays. enable: copy the audio into the ring; muteLocal: the phone's own output then plays silence.
MIX(void, nativeTapEnable)(JNIEnv*, jclass, jboolean on, jboolean muteLocal) { g_tap.enable(on == JNI_TRUE, muteLocal == JNI_TRUE); }
MIX(void, nativeTapFlush)(JNIEnv*, jclass) { g_tap.flush(); }
// Fills [out] (16-bit stereo, room for [maxFrames] frames) with what is waiting; returns the number of frames
MIX(jint, nativeTapRead)(JNIEnv* env, jclass, jshortArray out, jint maxFrames) {
    const jsize cap = env->GetArrayLength(out) / 2;
    const int n = std::min<int>(maxFrames, cap);
    if (n <= 0) return 0;
    jshort* p = env->GetShortArrayElements(out, nullptr);
    if (!p) return 0;
    const int got = g_tap.read(reinterpret_cast<int16_t*>(p), n);
    env->ReleaseShortArrayElements(out, p, 0);
    return got;
}

MIX(jboolean, nativeStart)(JNIEnv*, jclass) { return ensureRunning(); }

MIX(void, nativeSleepStream)(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> g(g_lock);
    if (g_stream && g_engine && g_engine->pausedSilent()) g_stream->requestPause();
}

// 0.0.131: the output chosen in the menu. A running stream is closed and opened again on the new device; a sleeping
// or closed stream picks it up the next time it starts.
MIX(void, nativeSetOutputDevice)(JNIEnv*, jclass, jint id) {
    std::lock_guard<std::mutex> g(g_lock);
    if (g_deviceId == id) return;
    g_deviceId = id;
    if (!g_stream) return;
    const bool running = g_stream->getState() == oboe::StreamState::Started;
    g_stream->close();
    g_stream.reset();
    if (running) Output::openLocked();
}

MIX(void, nativeRelease)(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> g(g_lock);
    if (g_stream) {
        g_stream->close();
        g_stream.reset();
    }
    g_engine.reset();
    g_buffers.clear();
    {
        std::lock_guard<std::mutex> gs(g_statusLock);
        g_haveStatus = false;
    }
}

// ---- buffers --------------------------------------------------------------------------------------------------------
MIX(jlong, nativeNewBuffer)(JNIEnv*, jclass, jint srcRate, jlong expectedFrames) {
    auto b = std::make_shared<PcmBuffer>((int)srcRate, (int64_t)expectedFrames);
    std::lock_guard<std::mutex> g(g_lock);
    const jlong h = g_nextHandle++;
    g_buffers[h] = b;
    return h;
}

MIX(void, nativeAppend)(JNIEnv* env, jclass, jlong h, jshortArray data, jint frames) {
    auto b = bufferOf(h);
    if (!b) return;
    jshort* p = env->GetShortArrayElements(data, nullptr);
    b->append(reinterpret_cast<const int16_t*>(p), frames);
    env->ReleaseShortArrayElements(data, p, JNI_ABORT);
}

// ---- ALAC (a decoder for phones that have no ALAC codec) ---------------------------------------------------------------
MIX(jlong, nativeAlacOpen)(JNIEnv* env, jclass, jbyteArray cookie) {
    const jsize n = env->GetArrayLength(cookie);
    std::vector<uint8_t> c((size_t)n);
    env->GetByteArrayRegion(cookie, 0, n, reinterpret_cast<jbyte*>(c.data()));
    auto* a = new AlacStream();
    if (!a->open(c.data(), c.size())) {
        delete a;
        return 0;
    }
    return (jlong)(intptr_t)a;
}

// [sampleRate, channels, bitDepth, frameLength]
MIX(jintArray, nativeAlacInfo)(JNIEnv* env, jclass, jlong h) {
    auto* a = reinterpret_cast<AlacStream*>((intptr_t)h);
    jintArray r = env->NewIntArray(4);
    if (!a) return r;
    const jint v[4] = {a->sampleRate(), a->channels(), a->bitDepth(), a->frameLength()};
    env->SetIntArrayRegion(r, 0, 4, v);
    return r;
}

MIX(jint, nativeAlacDecode)(JNIEnv* env, jclass, jlong h, jbyteArray packet, jint size, jshortArray out) {
    auto* a = reinterpret_cast<AlacStream*>((intptr_t)h);
    if (!a || size <= 0 || env->GetArrayLength(packet) < size || env->GetArrayLength(out) < 2 * a->frameLength()) return -1;
    jbyte* in = env->GetByteArrayElements(packet, nullptr);
    jshort* o = env->GetShortArrayElements(out, nullptr);
    const int frames = a->decode(reinterpret_cast<const uint8_t*>(in), (size_t)size, reinterpret_cast<int16_t*>(o));
    env->ReleaseShortArrayElements(out, o, 0);
    env->ReleaseByteArrayElements(packet, in, JNI_ABORT);
    return frames;
}

MIX(void, nativeAlacClose)(JNIEnv*, jclass, jlong h) { delete reinterpret_cast<AlacStream*>((intptr_t)h); }

MIX(void, nativeFinish)(JNIEnv*, jclass, jlong h) {
    if (auto b = bufferOf(h)) b->finish();
}

// ---- other audio over a stretch of a song (the singing taken out of an overlap) ---------------------------------------------
// A copy of [frames] frames of the decoded song from frame [start] (interleaved 16-bit stereo, without any overlay)
MIX(jshortArray, nativeCopy)(JNIEnv* env, jclass, jlong h, jlong start, jint frames) {
    auto b = bufferOf(h);
    if (!b || !b->isFinished() || frames <= 0) return nullptr;
    std::vector<int16_t> v((size_t)frames * 2);
    b->copyDecoded((int64_t)start, v.data(), frames);
    jshortArray r = env->NewShortArray((jsize)v.size());
    if (!r) return nullptr;
    env->SetShortArrayRegion(r, 0, (jsize)v.size(), reinterpret_cast<const jshort*>(v.data()));
    return r;
}

MIX(jboolean, nativeSetOverlay)(JNIEnv* env, jclass, jlong h, jlong start, jshortArray data, jint frames) {
    auto b = bufferOf(h);
    if (!b || frames <= 0 || env->GetArrayLength(data) < frames * 2) return JNI_FALSE;
    jshort* p = env->GetShortArrayElements(data, nullptr);
    const bool ok = b->setOverlay((int64_t)start, reinterpret_cast<const int16_t*>(p), frames);
    env->ReleaseShortArrayElements(data, p, JNI_ABORT);
    return ok ? JNI_TRUE : JNI_FALSE;
}

MIX(void, nativeClearOverlay)(JNIEnv*, jclass, jlong h) {
    if (auto b = bufferOf(h)) b->clearOverlay();
}

MIX(jlong, nativeFrames)(JNIEnv*, jclass, jlong h) {
    auto b = bufferOf(h);
    return b ? (jlong)b->baseFrames() : 0;
}

MIX(void, nativeFreeBuffer)(JNIEnv*, jclass, jlong h) {
    std::lock_guard<std::mutex> g(g_lock);
    g_buffers.erase(h);  // the engine keeps its own reference until the song is released
}

// ---- analysis and planning ------------------------------------------------------------------------------------------
MIX(jdoubleArray, nativeAnalyze)(JNIEnv* env, jclass, jlong h, jdouble forcedBpm) {
    auto b = bufferOf(h);
    TrackMap m;
    if (b && b->isFinished()) {
        const std::vector<float> mono = b->decimatedMono();
        m = analyzeTrack(mono.data(), mono.size(), kRate / 2, forcedBpm);
        m.duration = (double)b->baseFrames() / kRate;
        if (m.cueOut > m.duration) m.cueOut = m.duration;
    }
    return packMap(env, m);
}

// The key of a whole song from its pitch classes; -1 when it is not clear (root + 12 when minor)
MIX(jint, nativeKeyOf)(JNIEnv* env, jclass, jdoubleArray a) {
    const TrackMap m = unpackMap(env, a);
    if (m.chroma.empty()) return -1;
    double c[12] = {};
    m.chromaSum(0.0, (double)m.beats.size(), c);
    const Key k = estimateKey(c);
    return k.confidence() >= 0.5 ? k.code() : -1;
}

MIX(jdoubleArray, nativePlan)(JNIEnv* env, jclass, jdoubleArray a, jdoubleArray b, jdouble nowA, jdouble maxTempo, jint genre, jboolean stems, jboolean loops, jint prevStyle, jint prevExit) {
    PlannerSettings s;
    s.maxTempoChange = maxTempo;
    s.genre = genre;
    s.stems = stems == JNI_TRUE;
    s.stemLeadSeconds = 60.0;
    s.loops = loops == JNI_TRUE;
    s.prevStyle = prevStyle;
    s.prevExit = prevExit;
    return packPlan(env, planTransition(unpackMap(env, a), unpackMap(env, b), nowA, s));
}

MIX(jstring, nativeExplain)(JNIEnv* env, jclass, jdoubleArray a, jdoubleArray b, jdouble nowA, jdouble maxTempo, jint genre, jboolean stems, jboolean loops, jint prevStyle, jint prevExit) {
    PlannerSettings s;
    s.maxTempoChange = maxTempo;
    s.genre = genre;
    s.stems = stems == JNI_TRUE;
    s.stemLeadSeconds = 60.0;
    s.loops = loops == JNI_TRUE;
    s.prevStyle = prevStyle;
    s.prevExit = prevExit;
    return env->NewStringUTF(explainPlan(unpackMap(env, a), unpackMap(env, b), nowA, s).c_str());
}

// ---- playing --------------------------------------------------------------------------------------------------------
MIX(jboolean, nativePlay)(JNIEnv* env, jclass, jlong h, jdoubleArray map, jdouble startSec) {
    auto b = bufferOf(h);
    if (!b || !b->isFinished()) return JNI_FALSE;
    const TrackMap m = unpackMap(env, map);
    if (!ensureRunning()) return JNI_FALSE;
    // The next-song slot has to be free first: a song waiting there (and its plan) is dropped
    if (!command([] { return g_engine->dropNext(); })) return JNI_FALSE;
    for (int i = 0; i < 100 && !g_engine->idleCommands(); i++) std::this_thread::sleep_for(std::chrono::milliseconds(5));
    return command([&] {
               g_engine->reclaim();
               return g_engine->play(b, m, startSec);
           })
               ? JNI_TRUE
               : JNI_FALSE;
}

MIX(jboolean, nativeSetCurrentMap)(JNIEnv* env, jclass, jdoubleArray map, jint epoch) {
    if (!g_engine) return JNI_FALSE;
    return g_engine->setCurrentMap(unpackMap(env, map), (int)epoch) ? JNI_TRUE : JNI_FALSE;
}

MIX(jboolean, nativeLoadNext)(JNIEnv* env, jclass, jlong h, jdoubleArray map) {
    auto b = bufferOf(h);
    if (!b || !b->isFinished() || !ensureRunning()) return JNI_FALSE;
    g_engine->reclaim();
    return g_engine->loadNext(b, unpackMap(env, map)) ? JNI_TRUE : JNI_FALSE;
}

MIX(jboolean, nativeSetPlan)(JNIEnv* env, jclass, jdoubleArray plan) {
    const TransitionPlan p = unpackPlan(env, plan);
    return command([&] { return g_engine->setPlan(p); }) ? JNI_TRUE : JNI_FALSE;
}

MIX(jboolean, nativeClearPlan)(JNIEnv*, jclass) {
    return command([] { return g_engine->clearPlan(); }) ? JNI_TRUE : JNI_FALSE;
}

MIX(jboolean, nativeDropNext)(JNIEnv*, jclass) {
    if (!command([] { return g_engine->dropNext(); })) return JNI_FALSE;
    // Wait until the audio thread has taken the command, then free the slot
    for (int i = 0; i < 100 && !g_engine->idleCommands(); i++) std::this_thread::sleep_for(std::chrono::milliseconds(5));
    g_engine->reclaim();
    return JNI_TRUE;
}

MIX(jboolean, nativeStop)(JNIEnv*, jclass) {
    if (!g_engine) return JNI_TRUE;
    const bool ok = command([] { return g_engine->stop(); });
    for (int i = 0; i < 100 && !g_engine->idleCommands(); i++) std::this_thread::sleep_for(std::chrono::milliseconds(5));
    g_engine->reclaim();
    return ok ? JNI_TRUE : JNI_FALSE;
}

MIX(void, nativeReclaim)(JNIEnv*, jclass) {
    if (g_engine) g_engine->reclaim();
}

MIX(void, nativeSetSpeed)(JNIEnv*, jclass, jdouble v) {
    if (g_engine) g_engine->setSpeed(v);
}

MIX(void, nativeSetGain)(JNIEnv*, jclass, jdouble v) {
    if (g_engine) g_engine->setGain(v);
}

MIX(void, nativeSetPaused)(JNIEnv*, jclass, jboolean p) {
    if (p) {
        if (g_engine) g_engine->setPaused(true);
    } else if (ensureRunning()) {
        g_engine->setPaused(false);
    }
}

MIX(jboolean, nativeNextSlotFree)(JNIEnv*, jclass) {
    if (!g_engine) return JNI_TRUE;
    g_engine->reclaim();
    return g_engine->nextSlotFree() ? JNI_TRUE : JNI_FALSE;
}

// [state, positionSec, phaseErrorMs, blendBeats, changes, ended, pausedSilent, epoch]
MIX(jdoubleArray, nativeStatus)(JNIEnv* env, jclass) {
    double v[8] = {};
    if (g_engine) {
        auto& t = g_engine->telemetry;
        // Seqlock read: the values belong together (position, change count and epoch of one moment)
        bool ok = false;
        for (int tries = 0; tries < 2000 && !ok; tries++) {
            const unsigned s1 = t.seq.load(std::memory_order_acquire);
            if (s1 & 1u) {
                if (tries >= 50) std::this_thread::yield();
                continue;
            }
            v[0] = t.state.load();
            v[1] = t.positionSec.load();
            v[2] = t.phaseErrorMs.load();
            v[3] = t.blendBeats.load();
            v[4] = t.changes.load();
            v[5] = t.ended.load();
            v[7] = t.epoch.load();
            ok = t.seq.load(std::memory_order_acquire) == s1;
        }
        v[6] = g_engine->pausedSilent() ? 1.0 : 0.0;
        // No consistent read (0.0.67 log: a status of only zeros made the host think a handoff had happened and B started
        // again from 0): the last good snapshot is returned instead
        std::lock_guard<std::mutex> gs(g_statusLock);
        if (ok) {
            for (int i = 0; i < 8; i++) g_lastStatus[i] = v[i];
            g_haveStatus = true;
        } else {
            __android_log_print(ANDROID_LOG_WARN, "NooklinMix", "status read failed, %s",
                                g_haveStatus ? "last good snapshot used" : "no snapshot to fall back on");
            if (g_haveStatus) {
                for (int i = 0; i < 8; i++)
                    if (i != 6) v[i] = g_lastStatus[i];
            }
        }
    }
    jdoubleArray r = env->NewDoubleArray(8);
    env->SetDoubleArrayRegion(r, 0, 8, v);
    return r;
}

}  // extern "C"
