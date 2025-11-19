#include <jni.h>
#include <string>
#include <fcntl.h>
#include <unistd.h>
#include <termios.h>
#include <poll.h>
#include <cstdio>
#include <errno.h>
#include <string.h>
#include <sys/stat.h>
#include <android/log.h>

#define LOG_TAG "NativeSerial"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)

/**
 * 使用 su（交互式，无 -c）执行 chmod
 * su 必须是无交互授权 / 已默认允许的那种，否则会卡住。
 */
static bool chmod_with_su(const std::string& path, mode_t mode) {
    // 起一个 su shell，往 stdin 写命令
    FILE* fp = popen("su", "w");
    if (!fp) {
        LOGE("popen(\"su\") failed");
        return false;
    }

    // 写 chmod 命令
    // 注意末尾一定要有换行，不然 shell 不执行
    fprintf(fp, "chmod %o \"%s\"\n", mode, path.c_str());
    // 不放心可以顺带再 ls 一下：
    // fprintf(fp, "ls -l \"%s\"\n", path.c_str());
    // 退出 su
    fprintf(fp, "exit\n");
    fflush(fp);

    int status = pclose(fp);
    if (status == -1) {
        LOGE("pclose(su) failed");
        return false;
    }

    if (WIFEXITED(status)) {
        int code = WEXITSTATUS(status);
        LOGI("su chmod exit code = %d", code);
        return code == 0;
    } else {
        LOGW("su terminated abnormally");
        return false;
    }
}

// jstring → std::string
static std::string JStringToString(JNIEnv* env, jstring jstr) {
    if (jstr == nullptr) return {};
    const char* utf = env->GetStringUTFChars(jstr, nullptr);
    if (utf == nullptr) return {};
    std::string res(utf);
    env->ReleaseStringUTFChars(jstr, utf);
    return res;
}

// 波特率映射
static speed_t GetBaudrate(jint baudRate) {
    switch (baudRate) {
        case 0: return B0;
        case 50: return B50;
        case 75: return B75;
        case 110: return B110;
        case 134: return B134;
        case 150: return B150;
        case 200: return B200;
        case 300: return B300;
        case 600: return B600;
        case 1200: return B1200;
        case 1800: return B1800;
        case 2400: return B2400;
        case 4800: return B4800;
        case 9600: return B9600;
        case 19200: return B19200;
        case 38400: return B38400;
        case 57600: return B57600;
        case 115200: return B115200;
        case 230400: return B230400;
#ifdef B460800
        case 460800: return B460800;
#endif
#ifdef B921600
        case 921600: return B921600;
#endif
        default:
            return B0;
    }
}

// 串口参数配置
static int ConfigurePort(int fd, jint baudRate, jint dataBits, jint stopBits, jint parity) {
    struct termios options{};
    if (tcgetattr(fd, &options) != 0) {
        LOGE("tcgetattr failed: %s", strerror(errno));
        return -errno;
    }

    cfmakeraw(&options);

    speed_t speed = GetBaudrate(baudRate);
    if (speed == B0) {
        LOGE("Unsupported baudrate: %d", baudRate);
        return -EINVAL;
    }
    cfsetispeed(&options, speed);
    cfsetospeed(&options, speed);

    // 数据位
    options.c_cflag &= ~CSIZE;
    switch (dataBits) {
        case 5: options.c_cflag |= CS5; break;
        case 6: options.c_cflag |= CS6; break;
        case 7: options.c_cflag |= CS7; break;
        case 8:
        default: options.c_cflag |= CS8; break;
    }

    // 停止位
    if (stopBits == 2) {
        options.c_cflag |= CSTOPB;
    } else {
        options.c_cflag &= ~CSTOPB;
    }

    // 校验位：0 无，1 奇，2 偶
    options.c_cflag &= ~(PARENB | PARODD);
    if (parity == 1) {            // 奇
        options.c_cflag |= (PARENB | PARODD);
    } else if (parity == 2) {     // 偶
        options.c_cflag |= PARENB;
        options.c_cflag &= ~PARODD;
    }

    // 允许接收，忽略 modem 线
    options.c_cflag |= (CLOCAL | CREAD);

    // 不用软件流控
    options.c_iflag &= ~(IXON | IXOFF | IXANY);

    // 原样输出
    options.c_oflag &= ~OPOST;

    options.c_cc[VMIN]  = 0;
    options.c_cc[VTIME] = 0;

    if (tcsetattr(fd, TCSANOW, &options) != 0) {
        LOGE("tcsetattr failed: %s", strerror(errno));
        return -errno;
    }

    return 0;
}

extern "C" {

/**
 * jlong open(String path, int baudRate, int dataBits, int stopBits, int parity)
 */
JNIEXPORT jlong JNICALL
Java_com_sik_comm_NativeSerial_open(
        JNIEnv* env,
        jclass,
        jstring jPath,
        jint baudRate,
        jint dataBits,
        jint stopBits,
        jint parity
) {
    std::string path = JStringToString(env, jPath);
    if (path.empty()) {
        LOGE("open: path is empty");
        return -EINVAL;
    }

    auto do_open = [&](const char* tag) -> int {
        int fd = ::open(path.c_str(), O_RDWR | O_NOCTTY | O_NONBLOCK);
        if (fd < 0) {
            int err = errno;
            LOGE("%s open(%s) failed: %s", tag, path.c_str(), strerror(err));
            return -err;   // 约定：负 errno
        }
        LOGI("%s open(%s) success, fd=%d", tag, path.c_str(), fd);
        return fd;
    };

    // 1️⃣ 先尝试直接 open
    int fd = do_open("first");
    if (fd < 0) {
        int err = -fd;

        // 如果不是权限错误，没必要改权限，直接返回
        if (err != EACCES && err != EPERM) {
            return fd; // 负 errno
        }

        LOGW("open failed with permission error (%d: %s), try chmod_with_su...",
             err, strerror(err));

        // 2️⃣ 用 su 去把权限改成 0666
        if (!chmod_with_su(path, 0666)) {
            LOGE("chmod_with_su(%s) failed", path.c_str());
            return fd;    // 还是原来的权限错误
        }

        // 3️⃣ 权限改完再试一次 open
        fd = do_open("after_chmod");
        if (fd < 0) {
            // 改完权限还不行，那就真没辙了
            return fd;
        }
    }

    // 走到这里，fd > 0，说明 open 已经成功了

    // 清掉非阻塞
    int flags = fcntl(fd, F_GETFL, 0);
    if (flags != -1) {
        fcntl(fd, F_SETFL, flags & ~O_NONBLOCK);
    }

    int cfg = ConfigurePort(fd, baudRate, dataBits, stopBits, parity);
    if (cfg != 0) {
        int err = -cfg;
        LOGE("ConfigurePort failed: %d (%s)", cfg, strerror(err));
        ::close(fd);
        return cfg;  // 按你原来的约定：cfg 已经是负 errno
    }

    LOGI("ConfigurePort success on %s, fd=%d", path.c_str(), fd);
    return static_cast<jlong>(fd);
}

/**
 * int write(long handle, byte[] data, int offset, int length, int timeoutMs)
 */
JNIEXPORT jint JNICALL
Java_com_sik_comm_NativeSerial_write(
        JNIEnv* env,
        jclass,
        jlong handle,
        jbyteArray jData,
        jint offset,
        jint length,
        jint timeoutMs
) {
    int fd = static_cast<int>(handle);
    if (fd < 0) return -EBADF;
    if (jData == nullptr || length <= 0) return -EINVAL;

    jsize arrayLen = env->GetArrayLength(jData);
    if (offset < 0 || length < 0 || offset + length > arrayLen) return -EINVAL;

    struct pollfd pfd{};
    pfd.fd = fd;
    pfd.events = POLLOUT;

    int ret = poll(&pfd, 1, timeoutMs);
    if (ret < 0) {
        int err = errno;
        LOGE("write poll failed: %s", strerror(err));
        return -err;
    } else if (ret == 0) {
        return 0; // 超时
    }

    jbyte* buf = env->GetByteArrayElements(jData, nullptr);
    if (buf == nullptr) return -ENOMEM;

    ssize_t written = ::write(fd, buf + offset, static_cast<size_t>(length));
    int savedErr = errno;
    env->ReleaseByteArrayElements(jData, buf, JNI_ABORT);

    if (written < 0) {
        LOGE("write failed: %s", strerror(savedErr));
        return -savedErr;
    }
    return static_cast<jint>(written);
}

/**
 * int read(long handle, byte[] buffer, int offset, int length, int timeoutMs)
 */
JNIEXPORT jint JNICALL
Java_com_sik_comm_NativeSerial_read(
        JNIEnv* env,
        jclass,
        jlong handle,
        jbyteArray jBuffer,
        jint offset,
        jint length,
        jint timeoutMs
) {
    int fd = static_cast<int>(handle);
    if (fd < 0) return -EBADF;
    if (jBuffer == nullptr || length <= 0) return -EINVAL;

    jsize arrayLen = env->GetArrayLength(jBuffer);
    if (offset < 0 || length < 0 || offset + length > arrayLen) return -EINVAL;

    struct pollfd pfd{};
    pfd.fd = fd;
    pfd.events = POLLIN;

    int ret = poll(&pfd, 1, timeoutMs);
    if (ret < 0) {
        int err = errno;
        LOGE("read poll failed: %s", strerror(err));
        return -err;
    } else if (ret == 0) {
        return 0; // 超时无数据
    }

    jbyte* buf = env->GetByteArrayElements(jBuffer, nullptr);
    if (buf == nullptr) return -ENOMEM;

    ssize_t n = ::read(fd, buf + offset, static_cast<size_t>(length));
    int savedErr = errno;

    env->ReleaseByteArrayElements(jBuffer, buf, 0);

    if (n < 0) {
        LOGE("read failed: %s", strerror(savedErr));
        return -savedErr;
    }
    return static_cast<jint>(n);
}

/**
 * void close(long handle)
 */
JNIEXPORT void JNICALL
Java_com_sik_comm_NativeSerial_close(
        JNIEnv*,
        jclass,
        jlong handle
) {
    int fd = static_cast<int>(handle);
    if (fd >= 0) {
        ::close(fd);
        LOGI("close fd=%d", fd);
    }
}

} // extern "C"
