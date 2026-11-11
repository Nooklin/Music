#pragma once
// 0.0.130: the signal processing around the stem separation model (Spleeter) in native code: the spectrogram of a stretch of music,
// the energy of one stem per frame, and the stretch put together again with a volume per stem and frame. The same formulas as
// StemSeparator.kt had: a periodic Hann window of 4096 samples, a hop of 1024, both channels in one complex transform, 1024 of the
// 2049 bins go to the model, the four masks are ratio masks of the model estimates and above bin 1024 the mean of the masks below it.
// The model itself (ONNX Runtime) stays in Kotlin.
#include <cstddef>

namespace nooklin::mix::stemdsp {

constexpr int kFft = 4096;
constexpr int kHop = 1024;
constexpr int kPad = kFft - kHop;
constexpr int kBins = kFft / 2 + 1;
constexpr int kModelBins = 1024;
constexpr int kStems = 4;

/**
 * Spectrum of [left] and [right] ([samples] long, 44.1 kHz). [frames] = ceil((kPad + samples) / kHop). spec0/spec1 (per channel,
 * frames * kBins * 2 floats: real and imaginary part) and [magnitudes] (2 * stride * kModelBins floats: the first kModelBins bins of
 * each frame, channel c frame t at (c * stride + t) * kModelBins) are filled; the rest of [magnitudes] is not touched.
 */
void stft(const float* left, const float* right, int samples, int frames, int stride, float* spec0, float* spec1, float* magnitudes,
          int threads);

/** The energy of [stem] per frame (the sum over the bins of the masked magnitude, squared). est[s]: estimates of stem s, as magnitudes. */
void energy(const float* spec0, const float* spec1, const float* const est[kStems], int frames, int stride, int stem, float* out,
            int threads);

/** The stretch with every stem at its own volume; [gains] has kStems volumes per frame (frame t, stem s at t * kStems + s). outL/outR: [samples] long. */
void render(const float* spec0, const float* spec1, const float* const est[kStems], int frames, int stride, int samples,
            const float* gains, float* outL, float* outR, int threads);

}  // namespace nooklin::mix::stemdsp
