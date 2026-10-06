# 本地模型插件 JNI 的构建脚本。
#
# 该 .so 由命令行 CMake + Android NDK 离线构建（见 build_native.ps1），
# 产物手动拷入 src/main/jniLibs/<abi>/，因此 AGP 不需要执行 externalNativeBuild。
#
# 可通过 -DMNN_ROOT=<MNN源码根> 与 -DMNN_LIB_DIR=<含libMNN.so的目录> 覆盖默认路径。
cmake_minimum_required(VERSION 3.22)
project(localllm CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(NOT DEFINED MNN_ROOT)
    # cpp -> main -> src -> localllm -> messageAIHelper -> WORK -> MNN
    set(MNN_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../MNN")
endif()

if(NOT DEFINED MNN_LIB_DIR)
    set(MNN_LIB_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}")
endif()

message(STATUS "localllm: MNN_ROOT=${MNN_ROOT}")
message(STATUS "localllm: MNN_LIB_DIR=${MNN_LIB_DIR}")

add_library(localllm SHARED llm_jni.cpp)

target_include_directories(localllm PRIVATE
    ${MNN_ROOT}/include
    ${MNN_ROOT}/transformers/llm/engine/include)

add_library(mnn_prebuilt SHARED IMPORTED)
set_target_properties(mnn_prebuilt PROPERTIES
    IMPORTED_LOCATION "${MNN_LIB_DIR}/libMNN.so")

find_library(log_lib log)

target_link_libraries(localllm
    mnn_prebuilt
    ${log_lib}
    android)

# MNN 以 -fno-exceptions/-fno-rtti 编译；JNI 层打开异常以便防御式捕获。
target_compile_options(localllm PRIVATE -O2 -fexceptions -frtti -fvisibility=default)
