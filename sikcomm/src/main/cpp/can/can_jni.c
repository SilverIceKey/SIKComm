#include <jni.h>
#include <sys/socket.h>
#include <sys/ioctl.h>
#include <net/if.h>
#include <unistd.h>
#include <string.h>
#include <errno.h>
#include <time.h>
#include <stdio.h>
#include <android/log.h>   // ✅ 日志

#define LOG_TAG "CAN-JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ---- Minimal SocketCAN UAPI ----
#ifndef PF_CAN
#define PF_CAN 29
#endif
#ifndef AF_CAN
#define AF_CAN PF_CAN
#endif
#ifndef CAN_RAW
#define CAN_RAW 1
#endif
#ifndef SOL_CAN_BASE
#define SOL_CAN_BASE 100
#endif
#ifndef SOL_CAN_RAW
#define SOL_CAN_RAW (SOL_CAN_BASE + CAN_RAW)
#endif
#ifndef CAN_RAW_FILTER
#define CAN_RAW_FILTER 1
#endif
#ifndef CAN_RAW_ERR_FILTER
#define CAN_RAW_ERR_FILTER 2
#endif
#ifndef CAN_RAW_LOOPBACK
#define CAN_RAW_LOOPBACK 3
#endif
#ifndef CAN_RAW_RECV_OWN_MSGS
#define CAN_RAW_RECV_OWN_MSGS 4
#endif
#ifndef SIOCGIFINDEX
#define SIOCGIFINDEX 0x8933
#endif

typedef unsigned int canid_t;

struct can_frame {
    canid_t can_id;
    unsigned char can_dlc;
    unsigned char __pad;
    unsigned char __res0;
    unsigned char __res1;
    unsigned char data[8];
};

struct sockaddr_can {
    unsigned short can_family;
    int can_ifindex;
    struct { canid_t rx_id, tx_id; } can_addr; // not used
};

struct can_filter {
    canid_t can_id;
    canid_t can_mask;
};
// --------------------------------

static jint throwErrno(JNIEnv* env, const char* who, int err) {
    char msg[192];
    snprintf(msg, sizeof(msg), "%s failed: errno=%d (%s)", who, err, strerror(err));
    LOGE("%s", msg); // ✅ 打到 Logcat
    jclass ex = (*env)->FindClass(env, "java/io/IOException");
    if (!ex) return -1;
    (*env)->ThrowNew(env, ex, msg);
    return -1;
}

JNIEXPORT jint JNICALL
Java_com_sik_comm_impl_1can_NativeCan_openSocket(JNIEnv* env, jobject thiz, jstring ifname) {
    const char* ifn = (*env)->GetStringUTFChars(env, ifname, 0);
    LOGI("openSocket(if=%s) ...", ifn);

    int fd = socket(PF_CAN, SOCK_RAW, CAN_RAW);
    if (fd < 0) {
        (*env)->ReleaseStringUTFChars(env, ifname, ifn);
        return throwErrno(env, "socket(PF_CAN,SOCK_RAW,CAN_RAW)", errno);
    }

    // loopback / recv_own：调试期建议开 1，联调稳定后可改 0
    int loopback = 0;
    if (setsockopt(fd, SOL_CAN_RAW, CAN_RAW_LOOPBACK, &loopback, sizeof(loopback)) < 0) {
        LOGW("setsockopt(CAN_RAW_LOOPBACK) errno=%d(%s)", errno, strerror(errno));
    }
    int recv_own = 0;
    if (setsockopt(fd, SOL_CAN_RAW, CAN_RAW_RECV_OWN_MSGS, &recv_own, sizeof(recv_own)) < 0) {
        LOGW("setsockopt(CAN_RAW_RECV_OWN_MSGS) errno=%d(%s)", errno, strerror(errno));
    }

    // 默认 500ms 超时
    struct timeval tv; tv.tv_sec = 0; tv.tv_usec = 500*1000;
    if (setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv)) < 0) {
        LOGW("setsockopt(SO_RCVTIMEO) errno=%d(%s)", errno, strerror(errno));
    }

    struct ifreq ifr; memset(&ifr, 0, sizeof(ifr));
    strncpy(ifr.ifr_name, ifn, sizeof(ifr.ifr_name)-1);
    if (ioctl(fd, SIOCGIFINDEX, &ifr) < 0) {
        int e = errno; close(fd);
        (*env)->ReleaseStringUTFChars(env, ifname, ifn);
        return throwErrno(env, "ioctl(SIOCGIFINDEX)", e);
    }
    LOGI("ifindex(%s)=%d", ifn, ifr.ifr_ifindex);

    struct sockaddr_can addr; memset(&addr, 0, sizeof(addr));
    addr.can_family = AF_CAN;
    addr.can_ifindex = ifr.ifr_ifindex;
    if (bind(fd, (struct sockaddr*)&addr, sizeof(addr)) < 0) {
        int e = errno; close(fd);
        (*env)->ReleaseStringUTFChars(env, ifname, ifn);
        return throwErrno(env, "bind(AF_CAN)", e);
    }

    (*env)->ReleaseStringUTFChars(env, ifname, ifn);
    LOGI("openSocket OK, fd=%d", fd);
    return fd;
}

JNIEXPORT jint JNICALL
Java_com_sik_comm_impl_1can_NativeCan_setReadTimeoutMs(JNIEnv* env, jobject thiz, jint fd, jint timeoutMs) {
    struct timeval tv;
    tv.tv_sec  = timeoutMs / 1000;
    tv.tv_usec = (timeoutMs % 1000) * 1000;
    int r = setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
    if (r < 0) {
        LOGW("setReadTimeoutMs(%d) errno=%d(%s)", timeoutMs, errno, strerror(errno));
        return -errno;
    }
    LOGI("setReadTimeoutMs OK: %d ms", timeoutMs);
    return 0;
}

JNIEXPORT jint JNICALL
Java_com_sik_comm_impl_1can_NativeCan_setFilters(JNIEnv* env, jobject thiz, jint fd, jintArray ids, jintArray masks) {
    if (!ids || !masks) { LOGW("setFilters: null arrays"); return -1; }
    jsize n = (*env)->GetArrayLength(env, ids);
    if (n != (*env)->GetArrayLength(env, masks)) { LOGW("setFilters: length mismatch"); return -2; }

    if (n == 0) {
        LOGI("setFilters: clear (receive all)");
        setsockopt(fd, SOL_CAN_RAW, CAN_RAW_FILTER, NULL, 0);
        return 0;
    }

    struct can_filter flt[16];
    if (n > 16) n = 16;
    jint* idsBuf = (*env)->GetIntArrayElements(env, ids, 0);
    jint* mskBuf = (*env)->GetIntArrayElements(env, masks, 0);
    for (int i = 0; i < n; i++) {
        flt[i].can_id   = (canid_t)(idsBuf[i] & 0x7FF);
        flt[i].can_mask = (canid_t)(mskBuf[i] & 0x7FF);
        LOGD("filter[%d]: id=0x%03X mask=0x%03X", i, flt[i].can_id, flt[i].can_mask);
    }
    (*env)->ReleaseIntArrayElements(env, ids, idsBuf, 0);
    (*env)->ReleaseIntArrayElements(env, masks, mskBuf, 0);

    if (setsockopt(fd, SOL_CAN_RAW, CAN_RAW_FILTER, flt, sizeof(flt[0]) * n) < 0) {
        LOGE("setsockopt(CAN_RAW_FILTER) errno=%d(%s)", errno, strerror(errno));
        return -errno;
    }
    LOGI("setFilters OK, n=%d", n);
    return 0;
}

JNIEXPORT jint JNICALL
Java_com_sik_comm_impl_1can_NativeCan_writeFrame(JNIEnv* env, jobject thiz, jint fd, jint canid, jbyteArray payload, jint len) {
    if (len < 0 || len > 8) len = 8;
    struct can_frame f; memset(&f, 0, sizeof(f));
    f.can_id = (canid_t)(canid & 0x7FF);
    f.can_dlc = (unsigned char) len;

    jsize n = (*env)->GetArrayLength(env, payload);
    jbyte* buf = (*env)->GetByteArrayElements(env, payload, 0);
    memcpy(f.data, buf, (len <= n ? len : n));
    (*env)->ReleaseByteArrayElements(env, payload, buf, 0);

    int ret = (int) write(fd, &f, sizeof(f));
    if (ret < 0) {
        LOGE("writeFrame id=0x%03X len=%d errno=%d(%s)", f.can_id, len, errno, strerror(errno));
        return -errno;
    }
    LOGD("writeFrame OK id=0x%03X len=%d data=%02X %02X %02X %02X %02X %02X %02X %02X",
         f.can_id, len,
         f.data[0],f.data[1],f.data[2],f.data[3],f.data[4],f.data[5],f.data[6],f.data[7]);
    return ret;
}

JNIEXPORT jint JNICALL
Java_com_sik_comm_impl_1can_NativeCan_readFrame(JNIEnv* env, jobject thiz, jint fd, jintArray outId, jbyteArray outData) {
    struct can_frame f;
    int ret = (int) read(fd, &f, sizeof(f));
    if (ret < 0) {
        if (errno == EAGAIN || errno == EWOULDBLOCK) {
            // 常见：超时，不打印成 error 以免刷屏
            LOGD("readFrame timeout");
        } else {
            LOGE("readFrame errno=%d(%s)", errno, strerror(errno));
        }
        return -errno;
    }

    jint id = (jint)(f.can_id & 0x7FF);
    (*env)->SetIntArrayRegion(env, outId, 0, 1, &id);

    jsize cap = (*env)->GetArrayLength(env, outData);
    if (cap < 8) {
        LOGE("readFrame outData too small: cap=%d", cap);
        return -3;
    }
    (*env)->SetByteArrayRegion(env, outData, 0, 8, (jbyte*)f.data);

    LOGD("readFrame OK id=0x%03X dlc=%d data=%02X %02X %02X %02X %02X %02X %02X %02X",
         id, f.can_dlc,
         f.data[0],f.data[1],f.data[2],f.data[3],f.data[4],f.data[5],f.data[6],f.data[7]);
    return (jint) f.can_dlc; // 0..8
}

JNIEXPORT void JNICALL
Java_com_sik_comm_impl_1can_NativeCan_closeSocket(JNIEnv* env, jobject thiz, jint fd) {
    if (fd >= 0) {
        LOGI("closeSocket fd=%d", fd);
        close(fd);
    }
}

JNIEXPORT jint JNICALL
Java_com_sik_comm_impl_1can_NativeCan_setRawLoopback(JNIEnv* env, jobject thiz, jint fd, jboolean enable) {
    int v = enable ? 1 : 0;
    int r = setsockopt(fd, SOL_CAN_RAW, CAN_RAW_LOOPBACK, &v, sizeof(v));
    if (r < 0) { LOGW("setLoopback(%d) errno=%d(%s)", v, errno, strerror(errno)); return -errno; }
    return 0;
}

JNIEXPORT jint JNICALL
Java_com_sik_comm_impl_1can_NativeCan_setRecvOwnMsgs(JNIEnv* env, jobject thiz, jint fd, jboolean enable) {
    int v = enable ? 1 : 0;
    int r = setsockopt(fd, SOL_CAN_RAW, CAN_RAW_RECV_OWN_MSGS, &v, sizeof(v));
    if (r < 0) { LOGW("setRecvOwn(%d) errno=%d(%s)", v, errno, strerror(errno)); return -errno; }
    return 0;
}