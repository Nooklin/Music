#pragma once
#include <cstddef>

#include "types.h"

namespace nooklin::mix {

/**
 * Finds the beat grid of a song from mono audio: a multi-band spectral-flux onset curve, a comb-enhanced tempo
 * estimate, dynamic-programming beat tracking (Ellis 2007), a grid per song that follows slow drift, the first beat of
 * the bar and the phrase phase. Pure computation, no I/O. [x] is mono audio at [sampleRate].
 * Returns a TrackMap with bpm == 0 when the song has no regular beat.
 */
TrackMap analyzeTrack(const float* x, size_t n, int sampleRate, double forcedBpm = 0.0);
// forcedBpm > 0 (0.0.95): the grid is built at that tempo instead of the tempo the search chose; the tempo evidence is still measured.

}  // namespace nooklin::mix
