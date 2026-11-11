// Host test of 0.0.110: the copy of the engine output for AirPlay (android/tap_ring.h).
#include <cmath>
#include <cstdio>
#include <thread>
#include <vector>

#include "../android/tap_ring.h"

using namespace nooklin::mix;
static int failures = 0;
static void check(bool ok, const char* what, double v) {
    std::printf("  %-70s %s (%.3f)\n", what, ok ? "ok  " : "FAIL", v);
    if (!ok) failures++;
}

int main() {
    TapRing t;
    std::vector<float> blk(2 * 256);
    for (int i = 0; i < 256; i++) { blk[2 * i] = 0.5f; blk[2 * i + 1] = -0.25f; }
    std::vector<int16_t> out(2 * 1024);
    t.push(blk.data(), 256);
    check(t.read(out.data(), 1024) == 0, "off: nothing is copied", 0);
    t.enable(true, false);
    t.push(blk.data(), 256);
    int n = t.read(out.data(), 1024);
    check(n == 256, "on: every frame arrives", n);
    check(std::abs(out[0] - 16384) <= 1 && std::abs(out[1] + 8192) <= 1, "left and right are 16-bit copies", out[0]);
    check(blk[0] == 0.5f, "local output is untouched when not muted", blk[0]);
    t.enable(true, true);
    t.push(blk.data(), 256);
    check(blk[0] == 0.0f && blk[511] == 0.0f, "muted: the phone's output is silence", blk[0]);
    n = t.read(out.data(), 1024);
    check(n == 256 && std::abs(out[0] - 16384) <= 1, "muted: the copy still holds the audio", out[0]);
    t.push(blk.data(), 256);
    t.flush();
    check(t.read(out.data(), 1024) == 0, "flush throws away what waits", 0);
    // full ring: the producer never blocks and never overwrites
    for (int i = 0; i < 1000; i++) { for (int j = 0; j < 256; j++) { blk[2 * j] = 0.5f; blk[2 * j + 1] = 0.5f; } t.push(blk.data(), 256); }
    int total = 0;
    while ((n = t.read(out.data(), 1024)) > 0) total += n;
    check(total == (int)TapRing::kFrames, "a full ring keeps exactly its capacity and drops the newest", total);
    // two threads: ramp in order, nothing lost or reordered while the consumer keeps up
    TapRing u;
    u.enable(true, false);
    const int blocks = 4000;
    std::thread prod([&] {
        std::vector<float> b(2 * 128);
        long k = 0;
        for (int i = 0; i < blocks; i++) {
            for (int j = 0; j < 128; j++, k++) { b[2 * j] = (float)(k % 20000) / 40000.0f; b[2 * j + 1] = b[2 * j]; }
            u.push(b.data(), 128);
            if (i % 8 == 0) std::this_thread::yield();
        }
    });
    long got = 0;
    bool ordered = true;
    int last = -1;
    std::vector<int16_t> o(2 * 512);
    int idle = 0;
    while (got < (long)blocks * 128 && idle < 2000000) {
        const int m = u.read(o.data(), 512);
        if (m == 0) { idle++; continue; }
        idle = 0;
        for (int i = 0; i < m; i++, got++) {
            const int v = o[2 * i];
            const int exp = (int)std::lrintf((float)(got % 20000) / 40000.0f * 32767.0f);
            if (std::abs(v - exp) > 1) ordered = false;
            last = v;
        }
    }
    prod.join();
    check(got == (long)blocks * 128 && ordered, "two threads: all frames, in order", (double)got);
    (void)last;
    std::printf(failures ? "FAILED\n" : "all ok\n");
    return failures ? 1 : 0;
}
