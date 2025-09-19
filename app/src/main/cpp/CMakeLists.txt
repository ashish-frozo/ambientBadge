# CMakeLists.txt for Whisper Android native library

cmake_minimum_required(VERSION 3.22.1)

project("whisper_android")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Find required packages
find_library(log-lib log)
find_library(android-lib android)

# Add the native library
add_library(whisper_android SHARED
    whisper_android.cpp)

# Include directories (would include CTranslate2 headers in real implementation)
target_include_directories(whisper_android PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR})

# Link libraries
target_link_libraries(whisper_android
    ${log-lib}
    ${android-lib})

# Compiler flags for optimization
target_compile_options(whisper_android PRIVATE
    -O3
    -ffast-math
    -DNDEBUG)

# In real implementation, would link CTranslate2 libraries:
# target_link_libraries(whisper_android
#     ctranslate2
#     openblas
#     ${log-lib}
#     ${android-lib})
