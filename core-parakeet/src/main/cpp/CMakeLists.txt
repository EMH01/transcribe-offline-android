cmake_minimum_required(VERSION 3.22.1)
project(transcribe_parakeet LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

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
    GIT_TAG 86c40c3bd6fc86f1187fb751d111b49e0fc18e84
    GIT_PROGRESS TRUE
)
FetchContent_MakeAvailable(whispercpp)

add_library(parakeet_jni SHARED parakeet_jni.cpp)

target_include_directories(
    parakeet_jni
    PRIVATE
    ${whispercpp_SOURCE_DIR}/include
    ${whispercpp_SOURCE_DIR}/ggml/include
)

find_library(log_lib log)
find_library(android_lib android)

target_link_libraries(
    parakeet_jni
    PRIVATE
    parakeet
    ${log_lib}
    ${android_lib}
)

if(NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    target_compile_options(parakeet_jni PRIVATE -O3 -ffunction-sections -fdata-sections)
    target_link_options(parakeet_jni PRIVATE -Wl,--gc-sections)
endif()
