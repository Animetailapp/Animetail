#pragma once
#include "discordpp.h"
#include <jni.h>
#include <string>
#include <mutex>
#include <atomic>

class DiscordBridge {
public:
    DiscordBridge();
    ~DiscordBridge();

    bool Init(int64_t appId);
    void Authorize();
    void Shutdown();
    void SetTokenAndConnect(const char* token);
    void Connect();
    void SetActivity(
        int activityType,
        const char* name, const char* state, const char* details,
        int64_t startSecs, int64_t endSecs,
        const char* largeImage, const char* largeText,
        const char* smallImage, const char* smallText,
        const char* button1Label, const char* button1Url,
        const char* button2Label, const char* button2Url
    );
    void SetOnlineStatus(int statusType);
    void Clear();
    void RunCallbacks();
    bool IsReady() const { return ready_; }
    bool IsAuthorized() const { return authorized_; }
    void SetJavaVM(JavaVM* vm);
    void Destroy();

    static jclass GetDiscordRpcManagerClass() { return discordRpcManagerClass_; }
    static jmethodID GetOnNativeStatusChangedMethod() { return onNativeStatusChangedMethod_; }
    static void SetDiscordRpcManagerClass(JNIEnv* env, jclass clazz) {
        if (discordRpcManagerClass_) env->DeleteGlobalRef(discordRpcManagerClass_);
        discordRpcManagerClass_ = clazz;
    }
    static void SetOnNativeStatusChangedMethod(jmethodID method) { onNativeStatusChangedMethod_ = method; }

private:
    void DestroyUnlocked();
    void DoGetToken(std::string code, std::string redirectUri, std::string codeVerifier);
    void FireNativeStatusCallback(int statusCode, bool ready, bool authorized);

    discordpp::Client* client_;
    std::atomic<bool> ready_;
    std::atomic<bool> authorized_;
    mutable std::mutex mutex_;
    int64_t appId_;
    JavaVM* javaVm_;

    static jclass discordRpcManagerClass_;
    static jmethodID onNativeStatusChangedMethod_;
};

extern DiscordBridge g_discordBridge;
