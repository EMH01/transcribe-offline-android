cmake_minimum_required(VERSION 3.22.1)
project(transcribe_whisper LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Pin whisper.cpp so app builds are reproducible. The APK itself has no runtime
# network dependency; this source fetch happens only on the development machine.
include(FetchContent)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)

FetchContent_Declare(
    whispercpp
    GIT_REPOSITORY https://github.com/ggml-org/whisper.cpp.git
    GIT_TAG d1be6fde11ac6e0407606b4e42fe72d34add8037
    GIT_PROGRESS TRUE
)
FetchContent_MakeAvailable(whispercpp)

add_library(whisper_jni SHARED whisper_jni.cpp)

target_include_directories(
    whisper_jni
    PRIVATE
    ${whispercpp_SOURCE_DIR}/include
    ${whispercpp_SOURCE_DIR}/ggml/include
)

find_library(log_lib log)
find_library(android_lib android)

target_link_libraries(
    whisper_jni
    PRIVATE
    whisper
    ${log_lib}
    ${android_lib}
)

if(NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    target_compile_options(whisper_jni PRIVATE -O3 -ffunction-sections -fdata-sections)
    target_link_options(whisper_jni PRIVATE -Wl,--gc-sections)
endif()
