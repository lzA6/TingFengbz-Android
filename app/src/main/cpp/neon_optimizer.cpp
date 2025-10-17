#include <jni.h>
#include <algorithm>  // 用于std::min和std::max
#include <cstdlib>    // 用于std::abs

#ifdef __ARM_NEON__
#include <arm_neon.h>  // Neon头文件
#endif

extern "C" JNIEXPORT jint JNICALL
Java_com_example_tfgy999_FrameInterpolator_calculateBlockDifferenceNeon(
        JNIEnv *env, jobject /* this */, jbyteArray prev, jbyteArray curr, jint x, jint y,
        jint dx, jint dy, jint width, jint height, jint blockSize) {
    jbyte *prevPtr = env->GetByteArrayElements(prev, nullptr);
    jbyte *currPtr = env->GetByteArrayElements(curr, nullptr);
    int diff = 0;

#ifdef __ARM_NEON__
    // Neon优化实现（适用于ARM架构）
    for (int by = 0; by < blockSize; ++by) {
        int py = std::min(std::max(y + by, 0), height - 1);
        int cy = std::min(std::max(y + by + dy, 0), height - 1);
        for (int bx = 0; bx < blockSize; bx += 8) {  // 每次处理8个像素
            int px_base = std::min(std::max(x + bx, 0), width - 1);
            int cx_base = std::min(std::max(x + bx + dx, 0), width - 1);

            // 使用Neon加载和计算
            uint8x8_t prev_vec = vld1_u8(reinterpret_cast<uint8_t*>(prevPtr + py * width + px_base));
            uint8x8_t curr_vec = vld1_u8(reinterpret_cast<uint8_t*>(currPtr + cy * width + cx_base));
            uint8x8_t diff_vec = vabd_u8(prev_vec, curr_vec);

            // 水平加法：将uint8x8_t拓宽并累加
            uint16x4_t sum1 = vpaddl_u8(diff_vec);       // 成对加法，拓宽为uint16x4_t
            uint32x2_t sum2 = vpaddl_u16(sum1);          // 再次成对加法，拓宽为uint32x2_t
            uint32x2_t sum3 = vpadd_u32(sum2, sum2);     // 累加sum2的两个元素
            diff += vget_lane_u32(sum3, 0);              // 提取标量值
        }
    }
#else
    // 回退实现（适用于非Neon架构，如x86）
    for (int by = 0; by < blockSize; ++by) {
        for (int bx = 0; bx < blockSize; ++bx) {
            int px = std::min(std::max(x + bx, 0), width - 1);
            int py = std::min(std::max(y + by, 0), height - 1);
            int cx = std::min(std::max(x + bx + dx, 0), width - 1);
            int cy = std::min(std::max(y + by + dy, 0), height - 1);
            int prevPixel = prevPtr[py * width + px];
            int currPixel = currPtr[cy * width + cx];
            diff += std::abs(prevPixel - currPixel);
        }
    }
#endif

    env->ReleaseByteArrayElements(prev, prevPtr, 0);
    env->ReleaseByteArrayElements(curr, currPtr, 0);
    return diff;
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_example_tfgy999_FrameInterpolator_calculateSubPixelOffset(JNIEnv *env, jobject thiz,
                                                                   jobject prev, jobject curr) {
    // TODO: implement calculateSubPixelOffset()
}
extern "C"
JNIEXPORT void JNICALL
Java_com_example_tfgy999_FrameInterpolator_nativeApplyCLInterpolation(JNIEnv *env, jobject thiz,
                                                                      jbyteArray prev_data,
                                                                      jbyteArray curr_data,
                                                                      jobject buffer, jint width,
                                                                      jint height, jfloat factor) {
    // TODO: implement nativeApplyCLInterpolation()
}