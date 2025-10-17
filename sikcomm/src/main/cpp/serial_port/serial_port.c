//
// Created by hp on 2025/10/17.
//
#include <jni.h>
#include <termios.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <string.h>
#include <errno.h>
#include <android/log.h>
#include <sys/wait.h>
#include <stdio.h>

#define LOG_TAG "serial_port"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// -------------- 波特率映射 --------------
static speed_t getBaudrate(jint baudrate) {
    switch (baudrate) {
        case 0: return B0; case 50: return B50; case 75: return B75; case 110: return B110;
        case 134: return B134; case 150: return B150; case 200: return B200; case 300: return B300;
        case 600: return B600; case 1200: return B1200; case 1800: return B1800; case 2400: return B2400;
        case 4800: return B4800; case 9600: return B9600; case 19200: return B19200; case 38400: return B38400;
        case 57600: return B57600; case 115200: return B115200; case 230400: return B230400;
        case 460800: return B460800; case 500000: return B500000; case 576000: return B576000;
        case 921600: return B921600; case 1000000: return B1000000; case 1152000: return B1152000;
        case 1500000: return B1500000; case 2000000: return B2000000; case 2500000: return B2500000;
        case 3000000: return B3000000; case 3500000: return B3500000; case 4000000: return B4000000;
        default: return (speed_t)-1;
    }
}

// -------------- su 提权：chmod --------------

JNIEXPORT jboolean JNICALL
Java_com_sik_comm_impl_1modbus_SerialPortNative_ensurePermWithSu(JNIEnv *env, jclass clazz,
                                                                 jstring jpath, jint jmode) {
    const char* path = (*env)->GetStringUTFChars(env, jpath, NULL);
    char cmd[256];
    snprintf(cmd, sizeof(cmd), "chmod %o %s", (unsigned int)jmode, path);
    LOGI("exec su -c: %s", cmd);

    pid_t pid = fork();
    if (pid == 0) {
        execlp("su", "su", "-c", cmd, (char*)NULL);
        _exit(127);
    }
    int status = -1;
    waitpid(pid, &status, 0);
    (*env)->ReleaseStringUTFChars(env, jpath, path);

    if (WIFEXITED(status) && WEXITSTATUS(status) == 0) {
        LOGI("chmod via su OK");
        return JNI_TRUE;
    } else {
        LOGE("chmod via su FAILED, status=%d", status);
        return JNI_FALSE;
    }
}
// -------------- 打开串口：返回 FileDescriptor --------------
JNIEXPORT jobject JNICALL
Java_com_sik_comm_impl_1modbus_SerialPortNative_open(JNIEnv* env, jobject thiz,
                                          jstring jpath, jint baudRate,
                                          jint dataBits, jint parity,
                                          jint stopBits, jint flags) {
    const char* path = (*env)->GetStringUTFChars(env, jpath, NULL);
    speed_t speed = getBaudrate(baudRate);
    if (speed == (speed_t)-1) {
        LOGE("invalid baudrate %d", baudRate);
        (*env)->ReleaseStringUTFChars(env, jpath, path);
        return NULL;
    }

    int oflags = O_RDWR | O_NOCTTY;            // 不要成为控制终端
    if ((flags & 0x80000000) != 0) oflags |= O_NONBLOCK; // 最高位表示 NONBLOCK 可选

    int fd = open(path, oflags);
    if (fd < 0) {
        LOGE("open %s failed: %s", path, strerror(errno));
        (*env)->ReleaseStringUTFChars(env, jpath, path);
        return NULL;
    }

    // 如果用了 NONBLOCK，这里清掉，改回阻塞
    int fl = fcntl(fd, F_GETFL);
    if (fl >= 0) {
        fl &= ~O_NONBLOCK;
        fcntl(fd, F_SETFL, fl);
    }

    struct termios cfg;
    if (tcgetattr(fd, &cfg) != 0) {
        LOGE("tcgetattr failed: %s", strerror(errno));
        close(fd);
        (*env)->ReleaseStringUTFChars(env, jpath, path);
        return NULL;
    }

    cfmakeraw(&cfg);
    cfsetispeed(&cfg, speed);
    cfsetospeed(&cfg, speed);

    // 数据位
    cfg.c_cflag &= ~CSIZE;
    switch (dataBits) {
        case 5: cfg.c_cflag |= CS5; break;
        case 6: cfg.c_cflag |= CS6; break;
        case 7: cfg.c_cflag |= CS7; break;
        case 8:
        default: cfg.c_cflag |= CS8; break;
    }

    // 奇偶
    if (parity == 0) {
        cfg.c_cflag &= ~PARENB;
    } else if (parity == 1) { // ODD
        cfg.c_cflag |= (PARENB | PARODD);
    } else { // EVEN
        cfg.c_cflag |= PARENB;
        cfg.c_cflag &= ~PARODD;
        cfg.c_iflag |= INPCK;
        cfg.c_iflag &= ~(IGNPAR | PARMRK);
    }

    // 停止位
    if (stopBits == 2) cfg.c_cflag |= CSTOPB;
    else               cfg.c_cflag &= ~CSTOPB;

    // 关闭硬件流控，设 VMIN/VTIME
    cfg.c_cflag &= ~CRTSCTS;
    cfg.c_cc[VMIN]  = 0;
    cfg.c_cc[VTIME] = 1; // 100ms

    if (tcsetattr(fd, TCSANOW, &cfg) != 0) {
        LOGE("tcsetattr failed: %s", strerror(errno));
        close(fd);
        (*env)->ReleaseStringUTFChars(env, jpath, path);
        return NULL;
    }

    (*env)->ReleaseStringUTFChars(env, jpath, path);

    // 返回 FileDescriptor
    jclass cFileDescriptor = (*env)->FindClass(env, "java/io/FileDescriptor");
    jmethodID iFileDescriptor = (*env)->GetMethodID(env, cFileDescriptor, "<init>", "()V");
    jfieldID descriptorID = (*env)->GetFieldID(env, cFileDescriptor, "descriptor", "I");
    jobject mFileDescriptor = (*env)->NewObject(env, cFileDescriptor, iFileDescriptor);
    (*env)->SetIntField(env, mFileDescriptor, descriptorID, (jint) fd);
    return mFileDescriptor;
}

// -------------- 关闭串口：读 Java 成员 mFd --------------
JNIEXPORT void JNICALL
Java_com_sik_comm_impl_1modbus_SerialPortNative_close(JNIEnv* env, jobject thiz) {
    jclass cls = (*env)->GetObjectClass(env, thiz);
    jclass fdCls = (*env)->FindClass(env, "java/io/FileDescriptor");
    jfieldID mFdID = (*env)->GetFieldID(env, cls, "mFd", "Ljava/io/FileDescriptor;");
    jfieldID descriptorID = (*env)->GetFieldID(env, fdCls, "descriptor", "I");

    jobject mFd = (*env)->GetObjectField(env, thiz, mFdID);
    if (!mFd) return;
    jint descriptor = (*env)->GetIntField(env, mFd, descriptorID);
    if (descriptor >= 0) {
        LOGI("close(fd=%d)", descriptor);
        close(descriptor);
        (*env)->SetIntField(env, mFd, descriptorID, (jint)-1);
    }
}
