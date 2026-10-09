cmake_minimum_required(VERSION 3.22.1)
project(transcribe_llm LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)

FetchContent_Declare(
    llamacpp
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
    GIT_TAG v0.6.0
    GIT_PROGRESS TRUE
)
FetchContent_MakeAvailable(llamacpp)

add_library(llm_jni SHARED llm_jni.cpp)

target_include_directories(
    llm_jni
    PRIVATE
    ${llamacpp_SOURCE_DIR}/include
    ${llamacpp_SOURCE_DIR}/ggml/include
)

find_library(log_lib log)
find_library(android_lib android)

target_link_libraries(
    llm_jni
    PRIVATE
    llama
    ${log_lib}
    ${android_lib}
)

if(NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    target_compile_options(llm_jni PRIVATE -O3 -ffunction-sections -fdata-sections)
    target_link_options(llm_jni PRIVATE -Wl,--gc-sections)
endif()
