cmake_minimum_required(VERSION 3.22.1)
project(saklolo_whisper)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Fetched by the fetchWhisperCpp Gradle task. Layout: <repo>/third_party/whisper.cpp
get_filename_component(WHISPER_LIB_DIR "${CMAKE_SOURCE_DIR}/../../../../third_party/whisper.cpp" ABSOLUTE)

if(NOT EXISTS "${WHISPER_LIB_DIR}/include/whisper.h")
    message(FATAL_ERROR "whisper.cpp not found at ${WHISPER_LIB_DIR}. Run the fetchWhisperCpp task.")
endif()

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(GGML_STATIC ON CACHE BOOL "" FORCE)

include(FetchContent)
FetchContent_Declare(ggml SOURCE_DIR "${WHISPER_LIB_DIR}/ggml")
FetchContent_MakeAvailable(ggml)

add_library(
    saklolo_whisper
    SHARED
    whisper_jni.c
    ${WHISPER_LIB_DIR}/src/whisper.cpp
    ${WHISPER_LIB_DIR}/src/aneforge/whisper-aneforge.cpp
)

target_include_directories(
    saklolo_whisper
    PRIVATE
    ${WHISPER_LIB_DIR}/include
    ${WHISPER_LIB_DIR}/src
    ${WHISPER_LIB_DIR}/ggml/include
    ${WHISPER_LIB_DIR}/ggml/src
)

target_compile_definitions(saklolo_whisper PRIVATE WHISPER_VERSION="1.9.5")
target_compile_options(saklolo_whisper PRIVATE -O3 -fvisibility=hidden)

# Static ggml backends register from global constructors. Keep them.
target_link_options(saklolo_whisper PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(
    saklolo_whisper
    log
    android
    -Wl,--whole-archive
    ggml
    -Wl,--no-whole-archive
)
