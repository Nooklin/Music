#pragma once
// Apple Lossless (ALAC) decoding for phones that have no ALAC codec. Packet in, 16-bit stereo out. Mono and stereo only.
#include <algorithm>
#include <cstdint>
#include <cstring>
#include <memory>
#include <vector>

#include "../third_party/alac/ALACBitUtilities.h"
#include "../third_party/alac/ALACDecoder.h"

namespace nooklin::mix {

class AlacStream {
public:
    /** [cookie] is the codec-specific data of the track (the 'magic cookie', with or without its 'alac' atom header). */
    bool open(const uint8_t* cookie, size_t size) {
        // The decoder reads the first 8 bytes before it checks the length; the config alone is 24 bytes
        if (size < 24) return false;
        std::vector<uint8_t> c(cookie, cookie + size);
        if (dec.Init(c.data(), (uint32_t)c.size()) != 0) return false;
        const ALACSpecificConfig& cfg = dec.mConfig;
        if (cfg.frameLength == 0 || cfg.frameLength > 65536) return false;
        if (cfg.numChannels < 1 || cfg.numChannels > 2) return false;  // surround is left to the player
        if (cfg.bitDepth != 16 && cfg.bitDepth != 20 && cfg.bitDepth != 24 && cfg.bitDepth != 32) return false;
        raw.assign((size_t)cfg.frameLength * cfg.numChannels * 4 + 16, 0);
        ok = true;
        return true;
    }

    int sampleRate() const { return (int)dec.mConfig.sampleRate; }
    int channels() const { return (int)dec.mConfig.numChannels; }
    int bitDepth() const { return (int)dec.mConfig.bitDepth; }
    int frameLength() const { return (int)dec.mConfig.frameLength; }

    /**
     * Decodes one packet into [out] (interleaved stereo, room for frameLength() frames). Returns the number of frames,
     * or -1 when the packet is damaged.
     */
    int decode(const uint8_t* packet, size_t size, int16_t* out) {
        if (!ok || size == 0) return -1;
        pkt.assign(packet, packet + size);
        pkt.resize(size + 8, 0);  // the bit reader may look a few bytes ahead
        BitBuffer bits;
        BitBufferInit(&bits, pkt.data(), (uint32_t)size);
        uint32_t n = 0;
        const ALACSpecificConfig& cfg = dec.mConfig;
        if (dec.Decode(&bits, raw.data(), cfg.frameLength, cfg.numChannels, &n) != 0) return -1;
        if (n > cfg.frameLength) return -1;
        const int ch = (int)cfg.numChannels;
        for (uint32_t i = 0; i < n; i++) {
            int16_t v[2];
            for (int c = 0; c < 2; c++) {
                const int cc = c < ch ? c : 0;  // mono is doubled
                v[c] = sample(i * (uint32_t)ch + (uint32_t)cc);
            }
            out[2 * i] = v[0];
            out[2 * i + 1] = v[1];
        }
        return (int)n;
    }

private:
    int16_t sample(uint32_t idx) const {
        switch (dec.mConfig.bitDepth) {
            case 16: {
                int16_t v;
                std::memcpy(&v, raw.data() + (size_t)idx * 2, 2);
                return v;
            }
            case 20:
            case 24: {
                // three bytes per sample, little endian; 20-bit samples are stored left-aligned like 24-bit ones
                const uint8_t* p = raw.data() + (size_t)idx * 3;
                int32_t v = (int32_t)(p[0] | (p[1] << 8) | (p[2] << 16));
                if (v & 0x800000) v -= 0x1000000;
                return clamp16((v + 128) >> 8);
            }
            default: {
                int32_t v;
                std::memcpy(&v, raw.data() + (size_t)idx * 4, 4);
                return clamp16((int32_t)(((int64_t)v + 32768) >> 16));
            }
        }
    }
    static int16_t clamp16(int32_t v) { return (int16_t)std::min(32767, std::max(-32768, v)); }

    ALACDecoder dec;
    std::vector<uint8_t> raw, pkt;
    bool ok = false;
};

}  // namespace nooklin::mix
