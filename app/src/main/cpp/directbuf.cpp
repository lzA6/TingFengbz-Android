#include <jni.h>
#include <sys/mman.h>
#include <unistd.h>
#include <cerrno>
#include <cstring>

extern "C" {

JNIEXPORT jlong JNICALL Java_com_example_tfgy999_DirectBufferPool_nativeAlloc(JNIEnv *env, jclass clazz, jint size) {
    // 使用 mmap 分配匿名内存
    void* ptr = mmap(nullptr, size, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (ptr == MAP_FAILED) {
        // 分配失败，返回 0
        return 0;
    }
    return reinterpret_cast<jlong>(ptr);
}

JNIEXPORT void JNICALL Java_com_example_tfgy999_DirectBufferPool_nativeFree(JNIEnv *env, jclass clazz, jlong ptr, jint size) {
    if (ptr != 0) {
        // 使用 munmap 释放内存
        // 注意：size 必须与分配时的大小一致，否则可能导致内存错误
        if (munmap(reinterpret_cast<void*>(ptr), size) == -1) {
            // 释放失败，记录错误（可选）
            // 这里不抛异常，因为 JNI 中异常处理复杂
        }
    }
}

}
