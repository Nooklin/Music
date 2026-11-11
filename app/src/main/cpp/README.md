# Auto Mix native engine

C++17. `mix/` is platform independent and is tested on a computer; `android/` is the Oboe output and the JNI bridge
(class `org.nooklin.music.playback.mix.NativeMix`). Built by Gradle through `CMakeLists.txt` (Oboe comes from Maven as a
prefab package).

Tests on a computer (needs only g++):

    g++ -O2 -std=c++17 -Ithird_party host/test_analyzer.cpp mix/analyzer.cpp -o t1 && ./t1
    g++ -O2 -std=c++17 -Ithird_party host/test_deck.cpp mix/analyzer.cpp -o t2 && ./t2
    g++ -O2 -std=c++17 -Ithird_party host/test_engine.cpp mix/analyzer.cpp mix/planner.cpp mix/engine.cpp -o t3 && ./t3
    g++ -O2 -std=c++17 -Ithird_party host/test_lock.cpp mix/analyzer.cpp mix/planner.cpp mix/engine.cpp -o t5 && ./t5
    g++ -O2 -std=c++17 host/test_resampler.cpp -o t4 && ./t4
    g++ -O2 -std=c++17 -Ithird_party host/test_planner.cpp mix/analyzer.cpp mix/planner.cpp -o t6 && ./t6
    g++ -O2 -std=c++17 -Ithird_party host/test_techniques.cpp mix/analyzer.cpp mix/planner.cpp mix/engine.cpp -o t7 && ./t7
    g++ -O2 -std=c++17 -pthread host/test_stem_dsp.cpp mix/stem_dsp.cpp -o t_stemdsp && ./t_stemdsp   # 0.0.130

Not covered by these tests: the Android files (they compile for arm64 with the NDK but have never run), real music.
    # ALAC against ffmpeg (needs ffmpeg and python3; the C files are compiled with gcc first)
    A=third_party/alac; for f in ALACBitUtilities EndianPortable ag_dec dp_dec matrix_dec; do gcc -c -O2 -w $A/$f.c -o $f.o; done
    g++ -O2 -std=c++17 -w -Ithird_party host/test_alac.cpp $A/ALACDecoder.cpp *.o -o t5
    python3 host/make_alac_fixture.py /tmp/fx stereo s16 && ./t5 /tmp/fx/cookie.bin /tmp/fx/packets.bin /tmp/fx/ref.s16

`third_party/alac` is Apple's reference ALAC decoder, unchanged (Apache 2.0).

0.0.103: `host/test_cues.cpp` (cue velden, families, niveauregelaar) bouwt met
`g++ -O2 -std=c++17 -Ithird_party host/test_cues.cpp mix/analyzer.cpp mix/planner.cpp -o t_cues`.
`host/render_pair.cpp` is het meetinstrument en de luistertest, zie de kop van dat bestand.
