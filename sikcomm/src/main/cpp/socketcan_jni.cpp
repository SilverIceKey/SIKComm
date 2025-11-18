#include <jni.h>
#include <string>
#include <errno.h>
#include <string.h>
#include <unistd.h>
#include <poll.h>
#include <sys/socket.h>
#include <sys/ioctl.h>
#include <net/if.h>
#include <linux/can.h>
#include <linux/can/raw.h>
#include <android/log.h>

#define LOG_TAG "NativeCan"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)

// flags bit 定义（和 Kotlin 那边保持一致）
static const int CAN_FLAG_EXTENDED = 0x01;
static const int CAN_FLAG_RTR      = 0x02;
static const int CAN_FLAG_FD       = 0x04;
static const int CAN_FLAG_BRS      = 0x08;

static std::string JStringToString(JNIEnv* env, jstring jstr) {
    if (jstr == nullptr) return {};
    const char* utf = env->GetStringUTFChars(jstr, nullptr);
    if (utf == nullptr) return {};
    std::string res(utf);
    env->ReleaseStringUTFChars(jstr, utf);
    return res;
}

/**
 * 打开 AF_INET socket，获取/设置 if flags。
 */
static int SetIfUpDown(const std::string& ifName, bool up) {
    int s = ::socket(AF_INET, SOCK_DGRAM, 0);
    if (s < 0) {
        int err = errno;
        LOGE("socket(AF_INET) failed: %s", strerror(err));
        return -err;
    }

    struct ifreq ifr{};
    std::strncpy(ifr.ifr_name, ifName.c_str(), IFNAMSIZ - 1);

    if (ioctl(s, SIOCGIFFLAGS, &ifr) < 0) {
        int err = errno;
        LOGE("SIOCGIFFLAGS(%s) failed: %s", ifName.c_str(), strerror(err));
        ::close(s);
        return -err;
    }

    if (up) {
        ifr.ifr_flags |= IFF_UP;
    } else {
        ifr.ifr_flags &= ~IFF_UP;
    }

    if (ioctl(s, SIOCSIFFLAGS, &ifr) < 0) {
        int err = errno;
        LOGE("SIOCSIFFLAGS(%s) failed: %s", ifName.c_str(), strerror(err));
        ::close(s);
        return -err;
    }

    ::close(s);
    return 0;
}

/**
 * 获取 ifindex。
 */
static int GetIfIndex(const std::string& ifName) {
    int s = ::socket(AF_INET, SOCK_DGRAM, 0);
    if (s < 0) {
        int err = errno;
        LOGE("socket(AF_INET) failed: %s", strerror(err));
        return -err;
    }

    struct ifreq ifr{};
    std::strncpy(ifr.ifr_name, ifName.c_str(), IFNAMSIZ - 1);

    if (ioctl(s, SIOCGIFINDEX, &ifr) < 0) {
        int err = errno;
        LOGE("SIOCGIFINDEX(%s) failed: %s", ifName.c_str(), strerror(err));
        ::close(s);
        return -err;
    }

    ::close(s);
    return ifr.ifr_ifindex;
}

extern "C" {

/**
 * int bringUp(String ifName, int bitrate, boolean fdMode)
 *
 * 注意：这里我们只负责 up 接口，不动 bitrate 和 FD 设置。
 * bitrate / fdMode 只是预留参数，如果你以后要加 netlink/ip 配置再搞。
 */
JNIEXPORT jint JNICALL
Java_com_sik_comm_NativeCan_bringUp(
        JNIEnv* env,
        jclass,
        jstring jIfName,
        jint bitrate,
        jboolean fdMode
) {
    std::string ifName = JStringToString(env, jIfName);
    if (ifName.empty()) {
        return -EINVAL;
    }

    LOGI("bringUp(%s), bitrate=%d, fdMode=%s (bitrate is not configured here)",
         ifName.c_str(), bitrate, fdMode ? "true" : "false");

    int ret = SetIfUpDown(ifName, true);
    return ret; // 0 ok, <0 -errno
}

/**
 * int bringDown(String ifName)
 */
JNIEXPORT jint JNICALL
Java_com_sik_comm_NativeCan_bringDown(
        JNIEnv* env,
        jclass,
        jstring jIfName
) {
    std::string ifName = JStringToString(env, jIfName);
    if (ifName.empty()) {
        return -EINVAL;
    }

    LOGI("bringDown(%s)", ifName.c_str());
    int ret = SetIfUpDown(ifName, false);
    return ret;
}

/**
 * long open(String ifName)
 *
 * 逻辑：
 * 1. 先尝试把接口 up（SetIfUpDown）
 * 2. 获取 ifindex
 * 3. socket(PF_CAN, SOCK_RAW, CAN_RAW) + bind
 */
JNIEXPORT jlong JNICALL
Java_com_sik_comm_NativeCan_open(
        JNIEnv* env,
        jclass,
        jstring jIfName
) {
    std::string ifName = JStringToString(env, jIfName);
    if (ifName.empty()) {
        LOGE("open: ifName is empty");
        return -EINVAL;
    }

    // 尝试 up 一下接口（失败的话就直接返回错误）
    int upRet = SetIfUpDown(ifName, true);
    if (upRet < 0) {
        LOGE("open: SetIfUpDown(%s) failed: %d", ifName.c_str(), upRet);
        return upRet;
    }

    int ifindex = GetIfIndex(ifName);
    if (ifindex < 0) {
        LOGE("open: GetIfIndex(%s) failed: %d", ifName.c_str(), ifindex);
        return ifindex;
    }

    int fd = ::socket(PF_CAN, SOCK_RAW, CAN_RAW);
    if (fd < 0) {
        int err = errno;
        LOGE("socket(PF_CAN) failed: %s", strerror(err));
        return -err;
    }

    struct sockaddr_can addr{};
    addr.can_family = AF_CAN;
    addr.can_ifindex = ifindex;

    if (bind(fd, reinterpret_cast<struct sockaddr*>(&addr), sizeof(addr)) < 0) {
        int err = errno;
        LOGE("bind(can, %s) failed: %s", ifName.c_str(), strerror(err));
        ::close(fd);
        return -err;
    }

    LOGI("CAN open(%s) success, fd=%d", ifName.c_str(), fd);
    return static_cast<jlong>(fd);
}

/**
 * int write(long handle, int frameId, int flags, byte[] data, int offset, int length, int timeoutMs)
 *
 * 只支持经典 CAN，length <= 8
 * flags:
 *  bit0: 扩展帧
 *  bit1: RTR
 *  bit2: FD (不支持 -> -ENOTSUP)
 *  bit3: BRS (不支持 -> -ENOTSUP)
 */
JNIEXPORT jint JNICALL
Java_com_sik_comm_NativeCan_write(
        JNIEnv* env,
        jclass,
        jlong handle,
        jint frameId,
        jint flags,
        jbyteArray jData,
        jint offset,
        jint length,
        jint timeoutMs
) {
    int fd = static_cast<int>(handle);
    if (fd < 0) return -EBADF;

    if (flags & (CAN_FLAG_FD | CAN_FLAG_BRS)) {
        // 当前实现不支持 CAN FD
        return -ENOTSUP;
    }

    if (jData == nullptr || length <= 0) return -EINVAL;
    if (length > 8) return -EINVAL; // 经典 CAN 最多 8 字节

    jsize arrayLen = env->GetArrayLength(jData);
    if (offset < 0 || length < 0 || offset + length > arrayLen) return -EINVAL;

    struct pollfd pfd{};
    pfd.fd = fd;
    pfd.events = POLLOUT;

    int pret = poll(&pfd, 1, timeoutMs);
    if (pret < 0) {
        int err = errno;
        LOGE("CAN write poll failed: %s", strerror(err));
        return -err;
    } else if (pret == 0) {
        return 0; // 超时
    }

    jbyte* buf = env->GetByteArrayElements(jData, nullptr);
    if (buf == nullptr) return -ENOMEM;

    struct can_frame frame{};
    canid_t cid = 0;

    if (flags & CAN_FLAG_EXTENDED) {
        cid = static_cast<canid_t>(frameId & CAN_EFF_MASK);
        cid |= CAN_EFF_FLAG;
    } else {
        cid = static_cast<canid_t>(frameId & CAN_SFF_MASK);
    }

    if (flags & CAN_FLAG_RTR) {
        cid |= CAN_RTR_FLAG;
    }

    frame.can_id = cid;
    frame.can_dlc = static_cast<__u8>(length);
    memcpy(frame.data, buf + offset, static_cast<size_t>(length));

    env->ReleaseByteArrayElements(jData, buf, JNI_ABORT);

    ssize_t n = ::write(fd, &frame, sizeof(frame));
    int savedErr = errno;

    if (n < 0) {
        LOGE("CAN write failed: %s", strerror(savedErr));
        return -savedErr;
    }

    return static_cast<jint>(n);
}

/**
 * int read(long handle, int[] outFrameId, int[] outFlags, byte[] data, int offset, int maxLen, int timeoutMs)
 *
 * 返回值：
 *  >0: payload 实际长度
 *  0: 超时
 *  <0: 错误
 */
JNIEXPORT jint JNICALL
Java_com_sik_comm_NativeCan_read(
        JNIEnv* env,
        jclass,
        jlong handle,
        jintArray jOutFrameId,
        jintArray jOutFlags,
        jbyteArray jData,
        jint offset,
        jint maxLen,
        jint timeoutMs
) {
    int fd = static_cast<int>(handle);
    if (fd < 0) return -EBADF;

    if (jOutFrameId == nullptr || jOutFlags == nullptr || jData == nullptr) return -EINVAL;
    if (maxLen <= 0) return -EINVAL;
    if (maxLen > 8) maxLen = 8; // 经典 CAN 限制

    jsize arrLen = env->GetArrayLength(jData);
    if (offset < 0 || offset >= arrLen) return -EINVAL;

    struct pollfd pfd{};
    pfd.fd = fd;
    pfd.events = POLLIN;

    int pret = poll(&pfd, 1, timeoutMs);
    if (pret < 0) {
        int err = errno;
        LOGE("CAN read poll failed: %s", strerror(err));
        return -err;
    } else if (pret == 0) {
        return 0; // 超时
    }

    struct can_frame frame{};
    ssize_t n = ::read(fd, &frame, sizeof(frame));
    int savedErr = errno;

    if (n < 0) {
        LOGE("CAN read failed: %s", strerror(savedErr));
        return -savedErr;
    }

    // 解析 frame
    jint frameId = 0;
    jint flags = 0;

    if (frame.can_id & CAN_EFF_FLAG) {
        frameId = static_cast<jint>(frame.can_id & CAN_EFF_MASK);
        flags |= CAN_FLAG_EXTENDED;
    } else {
        frameId = static_cast<jint>(frame.can_id & CAN_SFF_MASK);
    }

    if (frame.can_id & CAN_RTR_FLAG) {
        flags |= CAN_FLAG_RTR;
    }

    // 写回 outFrameId/outFlags
    jint tmpId[1];
    jint tmpFlags[1];
    tmpId[0] = frameId;
    tmpFlags[0] = flags;
    env->SetIntArrayRegion(jOutFrameId, 0, 1, tmpId);
    env->SetIntArrayRegion(jOutFlags, 0, 1, tmpFlags);

    // 写 payload
    jsize bufLen = env->GetArrayLength(jData);
    if (offset + frame.can_dlc > bufLen) {
        // 缓冲区太小，只写得下的部分
        int copyLen = std::min<int>(frame.can_dlc, bufLen - offset);
        env->SetByteArrayRegion(jData, offset, copyLen,
                                reinterpret_cast<jbyte*>(frame.data));
    } else {
        env->SetByteArrayRegion(jData, offset, frame.can_dlc,
                                reinterpret_cast<jbyte*>(frame.data));
    }

    return static_cast<jint>(frame.can_dlc);
}

/**
 * void close(long handle)
 */
JNIEXPORT void JNICALL
Java_com_sik_comm_NativeCan_close(
        JNIEnv*,
        jclass,
        jlong handle
) {
    int fd = static_cast<int>(handle);
    if (fd >= 0) {
        ::close(fd);
        LOGI("CAN close fd=%d", fd);
    }
}

} // extern "C"
