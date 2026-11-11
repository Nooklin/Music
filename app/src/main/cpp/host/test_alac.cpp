// Host test of the ALAC decoder against ffmpeg: arguments are the magic cookie file, a packet file (4-byte little endian
// size, then the packet, repeated) and the reference PCM (16-bit stereo, little endian) that ffmpeg decoded from the same
// file. Prepared by host/make_alac_fixture.py. Exit code 0 when the decoded audio is identical to the reference.
#include <algorithm>
#include <cstdio>
#include <cstdlib>
#include <vector>

#include "../mix/alac.h"

using namespace nooklin::mix;

static std::vector<uint8_t> slurp(const char* path) {
    std::vector<uint8_t> d;
    FILE* f = std::fopen(path, "rb");
    if (!f) return d;
    uint8_t buf[65536];
    size_t n;
    while ((n = std::fread(buf, 1, sizeof buf, f)) > 0) d.insert(d.end(), buf, buf + n);
    std::fclose(f);
    return d;
}

int main(int argc, char** argv) {
    if (argc < 4) {
        std::printf("usage: test_alac cookie.bin packets.bin reference.s16\n");
        return 2;
    }
    auto cookie = slurp(argv[1]), packets = slurp(argv[2]), ref = slurp(argv[3]);
    AlacStream s;
    if (!s.open(cookie.data(), cookie.size())) {
        std::printf("FAIL: cookie not accepted\n");
        return 1;
    }
    std::printf("alac: %d Hz, %d channels, %d bit, frame length %d\n", s.sampleRate(), s.channels(), s.bitDepth(), s.frameLength());
    std::vector<int16_t> pcm;
    std::vector<int16_t> out((size_t)s.frameLength() * 2);
    size_t pos = 0;
    int count = 0;
    while (pos + 4 <= packets.size()) {
        const uint32_t sz = packets[pos] | (packets[pos + 1] << 8) | (packets[pos + 2] << 16) | ((uint32_t)packets[pos + 3] << 24);
        pos += 4;
        if (pos + sz > packets.size()) break;
        const int n = s.decode(packets.data() + pos, sz, out.data());
        pos += sz;
        if (n < 0) {
            std::printf("FAIL: packet %d not decoded\n", count);
            return 1;
        }
        pcm.insert(pcm.end(), out.begin(), out.begin() + 2 * n);
        count++;
    }
    const size_t refN = ref.size() / 2;
    size_t diff = 0, first = (size_t)-1;
    int maxDiff = 0;
    const size_t cmp = std::min(refN, pcm.size());
    for (size_t i = 0; i < cmp; i++) {
        const int16_t r = (int16_t)(ref[2 * i] | (ref[2 * i + 1] << 8));
        if (r != pcm[i]) {
            if (first == (size_t)-1) first = i;
            diff++;
            maxDiff = std::max(maxDiff, std::abs((int)r - (int)pcm[i]));
        }
    }
    std::printf("%d packets, %zu samples decoded, %zu in the reference, %zu differ by at most %d (first at %zd)\n", count, pcm.size(),
                refN, diff, maxDiff, first == (size_t)-1 ? -1 : (ssize_t)first);
    // 16-bit audio must be identical; deeper audio is rounded to 16 bit and may differ from ffmpeg's rounding by one step
    const bool ok = maxDiff <= (s.bitDepth() == 16 ? 0 : 1) && pcm.size() >= refN && pcm.size() - refN < 2 * (size_t)s.frameLength();
    std::printf("%s\n", ok ? "ALL OK" : "FAILED");
    return ok ? 0 : 1;
}
