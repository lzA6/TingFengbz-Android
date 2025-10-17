cmake_minimum_required(VERSION 3.4.1)

# 修改库名为 directbuf
add_library(
        directbuf
        SHARED
        directbuf.cpp  # 确保源文件存在于 src/main/cpp/ 目录下
)

# 仅为32位ARM架构启用 -mfloat-abi=softfp
if(${ANDROID_ABI} STREQUAL "armeabi-v7a")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -mfloat-abi=softfp")
endif()

target_link_libraries(
        directbuf
        android
        log
)
