# CMakeLists.txt for AI native libraries (Whisper + LLaMA)

cmake_minimum_required(VERSION 3.22.1)

project("ai_android")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Find required packages
find_library(log-lib log)
find_library(android-lib android)

# Add the Whisper native library
add_library(whisper_android SHARED
    whisper_android.cpp)

# Add the LLaMA native library
add_library(llama_android SHARED
    llama_android.cpp)

# Include directories
target_include_directories(whisper_android PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR})

target_include_directories(llama_android PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR})

# Link libraries for Whisper
target_link_libraries(whisper_android
    ${log-lib}
    ${android-lib})

# Link libraries for LLaMA
target_link_libraries(llama_android
    ${log-lib}
    ${android-lib})

# Compiler flags for optimization
target_compile_options(whisper_android PRIVATE
    -O3
    -ffast-math
    -DNDEBUG)

target_compile_options(llama_android PRIVATE
    -O3
    -ffast-math
    -DNDEBUG)

# In real implementation, would link actual libraries:
# target_link_libraries(whisper_android
#     ctranslate2
#     openblas
#     ${log-lib}
#     ${android-lib})
#
# target_link_libraries(llama_android
#     llama
#     ggml
#     ${log-lib}
#     ${android-lib})
