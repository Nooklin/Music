#pragma once
#include <string>
#include "types.h"

namespace nooklin::mix {

struct PlannerSettings {
    /** Largest tempo change between the two songs that is still beat matched (0.10 = 10%). */
    double maxTempoChange = 0.10;
    /** Up to this much A alone is sped up or slowed down; above it the change is shared by both songs. */
    double soloTempoChange = 0.05;
    /** Overlap length in beats; 0 chooses by tempo (16 beats, 32 for fast songs). */
    int lengthBeats = 0;
    /** Seconds the plan must still lie ahead of the current position (time to prepare the next song). */
    double leadSeconds = 6.0;
    int rampBeats = 8;
    /** A blend whose keys clash more than this (0..1, weighted by how sure the keys are) gives way to a clean exit. */
    double maxBlendClash = 0.55;
    /** Genre class of the two songs: 0 unknown, 1 hip-hop, pop and open format (cuts), 2 house and techno (long blends), 3 bass music and drum and bass (drop swaps). */
    int genre = 0;
    /** True when the singing of one song can be taken out of the overlap (stems): two songs that sing together then cost little. Without it they cost a lot. */
    bool stems = false;
    /** 0.0.99: seconds that must lie between now and the start of the overlap for a plan that needs stems (splitting the stems takes 25 to 55 s); 0: no check. */
    double stemLeadSeconds = 0.0;
    /** True when a blend may run past the end of A by repeating its last bars (beat loop) instead of an exit. */
    bool loops = false;
    /** 0.0.101: style (1 relaxed, 2 rolling, 3 double drop, 4 exit, 6 breakdown swap) and exit kind (Template as int) of the transition before this one; -1: none. */
    int prevStyle = -1;
    int prevExit = -1;
};

/**
 * Plans the change from A to B the way a DJ chooses: a beat matched blend (a long one with a melody and a bass swap, or a
 * short one with a kick swap) when the tempos can be matched and the keys do not clash, the tempo change shared by both
 * songs when it is larger than A alone should take; otherwise a clean exit on a bar line: an echo, a filter sweep or a cut
 * (also onto a drop of B), chosen by how loud the two songs are there. The moment is chosen by cost: loud stretches on top
 * of each other, clashing keys, leaving early, skipping the start of B. Without a usable grid for A: a short fade.
 * [nowA] is the current position in A (seconds).
 */
TransitionPlan planTransition(const TrackMap& a, const TrackMap& b, double nowA, const PlannerSettings& s = {});

/** A text for the log: what the planner saw in both songs and why blends were turned down. */
std::string explainPlan(const TrackMap& a, const TrackMap& b, double nowA, const PlannerSettings& s = PlannerSettings());

}  // namespace nooklin::mix
