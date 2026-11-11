cmake_minimum_required(VERSION 3.22.1)
project(nooklinmix LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(oboe REQUIRED CONFIG)

add_library(nooklinmix SHARED
    android/mix_jni.cpp
    android/stem_dsp_jni.cpp
    mix/stem_dsp.cpp
    mix/analyzer.cpp
    mix/planner.cpp
    mix/engine.cpp
    third_party/alac/ALACDecoder.cpp
    third_party/alac/ALACBitUtilities.c
    third_party/alac/EndianPortable.c
    third_party/alac/ag_dec.c
    third_party/alac/dp_dec.c
    third_party/alac/matrix_dec.c)

# Apple's reference decoder is used as it is (Apache 2.0, see third_party/LICENSE-alac.txt)
set_source_files_properties(
    third_party/alac/ALACDecoder.cpp third_party/alac/ALACBitUtilities.c third_party/alac/EndianPortable.c
    third_party/alac/ag_dec.c third_party/alac/dp_dec.c third_party/alac/matrix_dec.c
    PROPERTIES COMPILE_FLAGS "-w")

target_include_directories(nooklinmix PRIVATE third_party)
target_compile_options(nooklinmix PRIVATE -O3 -Wall)
# 16 KB memory pages (required for Android 15+ devices that use them)
target_link_options(nooklinmix PRIVATE -Wl,-z,max-page-size=16384)
target_link_libraries(nooklinmix PRIVATE oboe::oboe log)
