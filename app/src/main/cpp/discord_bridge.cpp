#define DISCORDPP_IMPLEMENTATION
#include "discord_bridge.h"
#include <android/log.h>
#include <cstring>

#define LOG_TAG "DiscordBridge"
#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

DiscordBridge g_discordBridge;

jclass DiscordBridge::discordRpcManagerClass_ = nullptr;
jmethodID DiscordBridge::onNativeStatusChangedMethod_ = nullptr;

DiscordBridge::DiscordBridge()
    : client_(nullptr), ready_(false), authorized_(false), appId_(0), javaVm_(nullptr) {
    LOGI("DiscordBridge constructed");
}

DiscordBridge::~DiscordBridge() {
    LOGI("DiscordBridge destructor");
    Destroy();
}

bool DiscordBridge::Init(int64_t appId) {
    LOGI("Init called with appId=%lld", (long long)appId);
    std::lock_guard<std::mutex> lock(mutex_);
    if (client_) {
        LOGI("Init: client already exists, destroying first");
        DestroyUnlocked();
    }

    appId_ = appId;
    try {
        client_ = new discordpp::Client();
        LOGI("Init: Client created, setting appId and callback");
        client_->SetApplicationId(static_cast<uint64_t>(appId));
        client_->SetStatusChangedCallback(
            [this](discordpp::Client::Status status,
                   discordpp::Client::Error error,
                   int32_t errorDetail) {
                std::lock_guard<std::mutex> lock(mutex_);
                const char* statusStr = "Unknown";
                switch (status) {
                    case discordpp::Client::Status::Connecting:    statusStr = "Connecting"; break;
                    case discordpp::Client::Status::Connected:     statusStr = "Connected"; break;
                    case discordpp::Client::Status::Ready:         statusStr = "Ready"; break;
                    case discordpp::Client::Status::Disconnected:  statusStr = "Disconnected"; break;
                    case discordpp::Client::Status::Reconnecting:  statusStr = "Reconnecting"; break;
                    case discordpp::Client::Status::Disconnecting: statusStr = "Disconnecting"; break;
                    case discordpp::Client::Status::HttpWait:      statusStr = "HttpWait"; break;
                }
                const char* errorStr = "None";
                switch (error) {
                    case discordpp::Client::Error::None:              errorStr = "None"; break;
                    case discordpp::Client::Error::ConnectionFailed:  errorStr = "ConnectionFailed"; break;
                    case discordpp::Client::Error::UnexpectedClose:   errorStr = "UnexpectedClose"; break;
                    case discordpp::Client::Error::ConnectionCanceled: errorStr = "ConnectionCanceled"; break;
                }
                LOGI("StatusChanged: status=%s(%d) error=%s(%d) errorDetail=%d ready_=%s authorized_=%s",
                     statusStr, static_cast<int>(status),
                     errorStr, static_cast<int>(error),
                     errorDetail,
                     ready_ ? "true" : "false",
                     authorized_ ? "true" : "false");
                if (status == discordpp::Client::Status::Ready) {
                    ready_ = true;
                    LOGI("STATUS: Ready! Connection established");
                } else if (status == discordpp::Client::Status::Disconnected) {
                    if (ready_) {
                        LOGW("STATUS: Disconnected while previously ready (err=%s)", errorStr);
                    }
                    ready_ = false;
                } else if (status == discordpp::Client::Status::Disconnecting) {
                    LOGI("STATUS: Disconnecting...");
                } else if (status == discordpp::Client::Status::Reconnecting) {
                    LOGW("STATUS: Reconnecting...");
                } else if (status == discordpp::Client::Status::HttpWait) {
                    LOGI("STATUS: HttpWait (rate limited?)");
                } else if (status == discordpp::Client::Status::Connecting) {
                    LOGI("STATUS: Connecting...");
                } else if (status == discordpp::Client::Status::Connected) {
                    LOGI("STATUS: Connected (not yet ready)");
                }
                FireNativeStatusCallback(static_cast<int>(status), ready_, authorized_);
            });
        LOGI("Init: success");
        return true;
    } catch (const std::exception& e) {
        LOGE("Init failed with exception: %s", e.what());
        delete client_;
        client_ = nullptr;
        return false;
    } catch (...) {
        LOGE("Init failed with unknown exception");
        delete client_;
        client_ = nullptr;
        return false;
    }
}

void DiscordBridge::Authorize() {
    LOGI("Authorize called (client_=%s, authorized_=%s)",
         client_ ? "exists" : "null",
         authorized_ ? "true" : "false");
    std::lock_guard<std::mutex> lock(mutex_);
    if (!client_) {
        LOGE("Authorize: no client, aborting");
        return;
    }
    if (authorized_) {
        LOGW("Authorize: already authorized, aborting");
        return;
    }

    try {
        authorized_ = false;
        ready_ = false;

        LOGI("Authorize: creating code verifier");
        auto verifier = client_->CreateAuthorizationCodeVerifier();
        LOGI("Authorize: PKCE verifier created (challenge method=S256)");

        discordpp::AuthorizationArgs args;
        args.SetClientId(static_cast<uint64_t>(appId_));
        auto scopes = client_->GetDefaultPresenceScopes();
        LOGI("Authorize: scopes=%s", scopes.c_str());
        args.SetScopes(scopes);

        discordpp::AuthorizationCodeChallenge challenge;
        challenge.SetChallenge(verifier.Challenge().Challenge());
        challenge.SetMethod(discordpp::AuthenticationCodeChallengeMethod::S256);
        args.SetCodeChallenge(challenge);

        LOGI("Authorize: calling client_->Authorize()...");
        client_->Authorize(
            std::move(args),
            [this, ver = std::move(verifier)](
                discordpp::ClientResult result,
                std::string code,
                std::string redirectUri
            ) mutable {
                if (!result.Successful()) {
                    LOGE("Authorize callback FAILED: err=%s errCode=%d retryable=%s",
                         result.Error().c_str(),
                         result.ErrorCode(),
                         result.Retryable() ? "true" : "false");
                    return;
                }
                LOGI("Authorize callback SUCCEEDED");
                LOGI("Authorize: exchanging code for token...");
                DoGetToken(std::move(code), std::move(redirectUri), ver.Verifier());
            }
        );
        LOGI("Authorize: client_->Authorize() returned (async flow started)");
    } catch (const std::exception& e) {
        LOGE("Authorize threw exception: %s", e.what());
    } catch (...) {
        LOGE("Authorize threw unknown exception");
    }
}

void DiscordBridge::DoGetToken(
    std::string code, std::string redirectUri, std::string codeVerifier
) {
    LOGI("DoGetToken: exchanging authorization code for token");
    if (!client_) {
        LOGE("DoGetToken: no client");
        return;
    }
    try {
        LOGI("DoGetToken: calling client_->GetToken()...");
        client_->GetToken(
            static_cast<uint64_t>(appId_),
            code,
            codeVerifier,
            redirectUri,
            [this](discordpp::ClientResult result,
                   std::string accessToken,
                   std::string refreshToken,
                   discordpp::AuthorizationTokenType tokenType,
                   int32_t expiresIn,
                   std::string scopes) {
                if (!result.Successful()) {
                    LOGE("GetToken FAILED: err=%s errCode=%d",
                         result.Error().c_str(), result.ErrorCode());
                    return;
                }
                LOGI("GetToken SUCCEEDED: tokenType=%d expiresIn=%d scopes=%s",
                     static_cast<int>(tokenType), expiresIn, scopes.c_str());
                LOGI("GetToken: calling UpdateToken...");
                client_->UpdateToken(
                    tokenType, accessToken,
                    [this](discordpp::ClientResult r) {
                        if (!r.Successful()) {
                            LOGE("UpdateToken FAILED: err=%s errCode=%d",
                                 r.Error().c_str(), r.ErrorCode());
                            return;
                        }
                        authorized_ = true;
                        LOGI("UpdateToken SUCCEEDED, calling Connect...");
                        client_->Connect();
                        LOGI("Connect called");
                    }
                );
            }
        );
    } catch (const std::exception& e) {
        LOGE("DoGetToken threw exception: %s", e.what());
    } catch (...) {
        LOGE("DoGetToken threw unknown exception");
    }
}

void DiscordBridge::FireNativeStatusCallback(int statusCode, bool ready, bool authorized) {
    if (!javaVm_) return;
    JNIEnv* env = nullptr;
    int getEnvStat = javaVm_->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    bool needsDetach = false;
    if (getEnvStat == JNI_EDETACHED) {
        JavaVMAttachArgs args = {JNI_VERSION_1_6, "DiscordCallback", nullptr};
        if (javaVm_->AttachCurrentThread(&env, &args) != JNI_OK) return;
        needsDetach = true;
    } else if (getEnvStat != JNI_OK) {
        return;
    }

    if (discordRpcManagerClass_ && onNativeStatusChangedMethod_) {
        env->CallStaticVoidMethod(discordRpcManagerClass_, onNativeStatusChangedMethod_, statusCode, ready, authorized);
    }

    if (needsDetach) {
        javaVm_->DetachCurrentThread();
    }
}

void DiscordBridge::SetActivity(
    int activityType,
    const char* name, const char* state, const char* details,
    int64_t startSecs, int64_t endSecs,
    const char* largeImage, const char* largeText,
    const char* smallImage, const char* smallText,
    const char* button1Label, const char* button1Url,
    const char* button2Label, const char* button2Url
) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!client_) { LOGW("SetActivity: no client, skipping"); return; }
    if (!ready_) {
        LOGW("SetActivity: not ready (ready_=false), skipping activity for name=%s",
             name ? name : "null");
        return;
    }
    LOGI("SetActivity: type=%d name=%s state=%s details=%s startSecs=%lld endSecs=%lld",
         activityType,
         name ? name : "null", state ? state : "null", details ? details : "null",
         (long long)startSecs, (long long)endSecs);
    LOGI("SetActivity: largeImage=%s largeText=%s smallImage=%s smallText=%s",
         largeImage ? largeImage : "null", largeText ? largeText : "null",
         smallImage ? smallImage : "null", smallText ? smallText : "null");
    LOGI("SetActivity: btn1=%s/%s btn2=%s/%s",
         button1Label ? button1Label : "null", button1Url ? button1Url : "null",
         button2Label ? button2Label : "null", button2Url ? button2Url : "null");

    try {
        discordpp::Activity activity;
        activity.SetType(static_cast<discordpp::ActivityTypes>(activityType));
        if (name) activity.SetName(std::string(name));
        if (state) activity.SetState(std::string(state));
        if (details) activity.SetDetails(std::string(details));

        if (startSecs > 0 || endSecs > 0) {
            discordpp::ActivityTimestamps ts;
            if (startSecs > 0) ts.SetStart(static_cast<uint64_t>(startSecs));
            if (endSecs > 0) ts.SetEnd(static_cast<uint64_t>(endSecs));
            activity.SetTimestamps(std::move(ts));
            LOGI("SetActivity: timestamps start=%llu end=%llu",
                 (unsigned long long)startSecs, (unsigned long long)endSecs);
        }

        discordpp::ActivityAssets assets;
        if (largeImage) assets.SetLargeImage(std::string(largeImage));
        if (largeText) assets.SetLargeText(std::string(largeText));
        if (smallImage) assets.SetSmallImage(std::string(smallImage));
        if (smallText) assets.SetSmallText(std::string(smallText));
        activity.SetAssets(std::move(assets));

        if (button1Label && button1Url && strlen(button1Label) > 0 && strlen(button1Url) > 0) {
            discordpp::ActivityButton btn1;
            btn1.SetLabel(std::string(button1Label));
            btn1.SetUrl(std::string(button1Url));
            activity.AddButton(std::move(btn1));
            LOGI("SetActivity: added button1");
        }
        if (button2Label && button2Url && strlen(button2Label) > 0 && strlen(button2Url) > 0) {
            discordpp::ActivityButton btn2;
            btn2.SetLabel(std::string(button2Label));
            btn2.SetUrl(std::string(button2Url));
            activity.AddButton(std::move(btn2));
            LOGI("SetActivity: added button2");
        }

        LOGI("SetActivity: calling client_->UpdateRichPresence...");
        client_->UpdateRichPresence(
            std::move(activity),
            [](discordpp::ClientResult r) {
                if (!r.Successful()) {
                    LOGE("SetActivity: UpdateRichPresence FAILED: err=%s errCode=%d retryable=%s",
                         r.Error().c_str(), r.ErrorCode(),
                         r.Retryable() ? "true" : "false");
                } else {
                    LOGI("SetActivity: UpdateRichPresence succeeded");
                }
            }
        );
        LOGI("SetActivity: UpdateRichPresence call returned (async)");
    } catch (const std::exception& e) {
        LOGE("SetActivity threw exception: %s", e.what());
    } catch (...) {
        LOGE("SetActivity threw unknown exception");
    }
}

void DiscordBridge::SetOnlineStatus(int statusType) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!client_) { LOGW("SetOnlineStatus: no client, skipping"); return; }
    if (!ready_) { LOGW("SetOnlineStatus: not ready, skipping"); return; }
    LOGI("SetOnlineStatus: setting status to %d", statusType);
    try {
        client_->SetOnlineStatus(
            static_cast<discordpp::StatusType>(statusType),
            [](discordpp::ClientResult r) {
                if (!r.Successful()) {
                    LOGE("SetOnlineStatus: FAILED: err=%s errCode=%d",
                         r.Error().c_str(), r.ErrorCode());
                } else {
                    LOGI("SetOnlineStatus: succeeded");
                }
            }
        );
    } catch (const std::exception& e) {
        LOGE("SetOnlineStatus threw exception: %s", e.what());
    } catch (...) {
        LOGE("SetOnlineStatus threw unknown exception");
    }
}

void DiscordBridge::Clear() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!client_) {
        LOGW("Clear: no client, skipping");
        return;
    }
    if (!ready_) {
        LOGW("Clear: not ready, skipping");
        return;
    }
    LOGI("Clear: clearing rich presence");
    try {
        discordpp::Activity activity;
        LOGI("Clear: calling client_->UpdateRichPresence with empty activity...");
        client_->UpdateRichPresence(
            std::move(activity),
            [](discordpp::ClientResult r) {
                if (!r.Successful()) {
                    LOGE("Clear: UpdateRichPresence failed: err=%s errCode=%d",
                         r.Error().c_str(), r.ErrorCode());
                } else {
                    LOGI("Clear: UpdateRichPresence succeeded");
                }
            }
        );
        LOGI("Clear: UpdateRichPresence call returned (async)");
    } catch (const std::exception& e) {
        LOGE("Clear threw exception: %s", e.what());
    } catch (...) {
        LOGE("Clear threw unknown exception");
    }
}

void DiscordBridge::Shutdown() {
    LOGI("Shutdown called (ready_=%s, authorized_=%s, client_=%s)",
         ready_ ? "true" : "false",
         authorized_ ? "true" : "false",
         client_ ? "exists" : "null");
    std::lock_guard<std::mutex> lock(mutex_);
    if (!client_) {
        LOGW("Shutdown: no client, nothing to do");
        return;
    }
    try {
        LOGI("Shutdown: calling client_->Disconnect()...");
        client_->Disconnect();
        LOGI("Shutdown: client_->Disconnect() returned");
    } catch (const std::exception& e) {
        LOGE("Shutdown threw exception: %s", e.what());
    } catch (...) {
        LOGE("Shutdown threw unknown exception");
    }
    ready_ = false;
    authorized_ = false;
    LOGI("Shutdown: complete (ready_=false, authorized_=false)");
}

void DiscordBridge::SetTokenAndConnect(const char* token) {
    LOGI("SetTokenAndConnect: token=%s, ready_=%s, authorized_=%s",
         token ? "provided" : "null",
         ready_ ? "true" : "false",
         authorized_ ? "true" : "false");
    if (!token) { LOGE("SetTokenAndConnect: null token"); return; }
    std::lock_guard<std::mutex> lock(mutex_);
    if (!client_) { LOGE("SetTokenAndConnect: no client"); return; }
    if (authorized_) {
        LOGW("SetTokenAndConnect: already authorized, skipping");
        return;
    }
    try {
        LOGI("SetTokenAndConnect: calling client_->UpdateToken(Bearer, token_len=%zu)...",
             strlen(token));
        client_->UpdateToken(
            discordpp::AuthorizationTokenType::Bearer,
            std::string(token),
            [this](discordpp::ClientResult result) {
                if (result.Successful()) {
                    std::lock_guard<std::mutex> lk(mutex_);
                    authorized_ = true;
                    LOGI("SetTokenAndConnect: UpdateToken succeeded, authorized_=true");
                } else {
                    LOGE("SetTokenAndConnect: UpdateToken FAILED: err=%s errCode=%d retryable=%s",
                         result.Error().c_str(), result.ErrorCode(),
                         result.Retryable() ? "true" : "false");
                }
            }
        );
        LOGI("SetTokenAndConnect: UpdateToken initiated (async)");
    } catch (const std::exception& e) {
        LOGE("SetTokenAndConnect threw exception: %s", e.what());
    } catch (...) {
        LOGE("SetTokenAndConnect threw unknown exception");
    }
}

void DiscordBridge::Connect() {
    LOGI("Connect called (ready_=%s, authorized_=%s)",
         ready_ ? "true" : "false", authorized_ ? "true" : "false");
    std::lock_guard<std::mutex> lock(mutex_);
    if (!client_) { LOGE("Connect: no client"); return; }
    if (ready_) {
        LOGW("Connect: already ready, skipping");
        return;
    }
    try {
        LOGI("Connect: calling client_->Connect()...");
        client_->Connect();
        LOGI("Connect: client_->Connect() returned (async)");
    } catch (const std::exception& e) {
        LOGE("Connect threw exception: %s", e.what());
    } catch (...) {
        LOGE("Connect threw unknown exception");
    }
}

void DiscordBridge::RunCallbacks() {
    LOGV("RunCallbacks: entering");
    try {
        discordpp::RunCallbacks();
        LOGV("RunCallbacks: completed successfully");
    } catch (const std::exception& e) {
        LOGE("RunCallbacks threw exception: %s", e.what());
    } catch (...) {
        LOGE("RunCallbacks threw unknown exception");
    }
}

void DiscordBridge::SetJavaVM(JavaVM* vm) {
    javaVm_ = vm;
}

void DiscordBridge::DestroyUnlocked() {
    ready_ = false;
    authorized_ = false;
    if (client_) {
        try {
            LOGI("DestroyUnlocked: disconnecting client...");
            client_->Disconnect();
            LOGI("DestroyUnlocked: disconnected");
        } catch (const std::exception& e) {
            LOGW("DestroyUnlocked: Disconnect threw: %s (ignored)", e.what());
        } catch (...) {
            LOGW("DestroyUnlocked: Disconnect threw unknown (ignored)");
        }
        LOGI("DestroyUnlocked: deleting client...");
        delete client_;
        client_ = nullptr;
        LOGI("DestroyUnlocked: client deleted successfully");
    } else {
        LOGW("DestroyUnlocked: no client to destroy");
    }
    javaVm_ = nullptr;
}

void DiscordBridge::Destroy() {
    LOGI("Destroy called (ready_=%s, authorized_=%s, client_=%s)",
         ready_ ? "true" : "false",
         authorized_ ? "true" : "false",
         client_ ? "exists" : "null");
    std::lock_guard<std::mutex> lock(mutex_);
    DestroyUnlocked();
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_eu_kanade_tachiyomi_data_connections_discord_DiscordRpcManager_nativeInit(
    JNIEnv* env, jobject thiz, jlong appId
) {
    JavaVM* vm;
    env->GetJavaVM(&vm);
    g_discordBridge.SetJavaVM(vm);

    jclass localClass = env->FindClass("eu/kanade/tachiyomi/data/connections/discord/DiscordRpcManager");
    if (localClass) {
        jclass globalClass = static_cast<jclass>(env->NewGlobalRef(localClass));
        DiscordBridge::SetDiscordRpcManagerClass(env, globalClass);
        jmethodID method = env->GetStaticMethodID(localClass, "onNativeStatusChanged", "(IZZ)V");
        if (method) {
            DiscordBridge::SetOnNativeStatusChangedMethod(method);
        }
        env->DeleteLocalRef(localClass);
    }

    return g_discordBridge.Init(static_cast<int64_t>(appId)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_eu_kanade_tachiyomi_data_connections_discord_DiscordRpcManager_nativeIsAuthorized(
    JNIEnv* env, jobject thiz
) {
    return g_discordBridge.IsAuthorized() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_eu_kanade_tachiyomi_data_connections_discord_DiscordRpcManager_nativeIsReady(
    JNIEnv* env, jobject thiz
) {
    return g_discordBridge.IsReady() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_eu_kanade_tachiyomi_data_connections_discord_DiscordRpcManager_nativeDisconnect(
    JNIEnv* env, jobject thiz
) {
    g_discordBridge.Shutdown();
}

JNIEXPORT void JNICALL
Java_eu_kanade_tachiyomi_data_connections_discord_DiscordRpcManager_nativeSetTokenAndConnect(
    JNIEnv* env, jobject thiz, jstring token
) {
    const char* tokenStr = token ? env->GetStringUTFChars(token, nullptr) : nullptr;
    if (tokenStr) {
        g_discordBridge.SetTokenAndConnect(tokenStr);
        env->ReleaseStringUTFChars(token, tokenStr);
    }
}

JNIEXPORT void JNICALL
Java_eu_kanade_tachiyomi_data_connections_discord_DiscordRpcManager_nativeConnect(
    JNIEnv* env, jobject thiz
) {
    g_discordBridge.Connect();
}

JNIEXPORT void JNICALL
Java_eu_kanade_tachiyomi_data_connections_discord_DiscordRpcManager_nativeSetActivity(
    JNIEnv* env, jobject thiz,
    jint activityType,
    jstring name, jstring state, jstring details,
    jlong startSecs, jlong endSecs,
    jstring largeImage, jstring largeText,
    jstring smallImage, jstring smallText,
    jstring button1Label, jstring button1Url,
    jstring button2Label, jstring button2Url
) {
    const char* cName = name ? env->GetStringUTFChars(name, nullptr) : nullptr;
    const char* cState = state ? env->GetStringUTFChars(state, nullptr) : nullptr;
    const char* cDetails = details ? env->GetStringUTFChars(details, nullptr) : nullptr;
    const char* cLargeImage = largeImage ? env->GetStringUTFChars(largeImage, nullptr) : nullptr;
    const char* cLargeText = largeText ? env->GetStringUTFChars(largeText, nullptr) : nullptr;
    const char* cSmallImage = smallImage ? env->GetStringUTFChars(smallImage, nullptr) : nullptr;
    const char* cSmallText = smallText ? env->GetStringUTFChars(smallText, nullptr) : nullptr;
    const char* cBtn1Label = button1Label ? env->GetStringUTFChars(button1Label, nullptr) : nullptr;
    const char* cBtn1Url = button1Url ? env->GetStringUTFChars(button1Url, nullptr) : nullptr;
    const char* cBtn2Label = button2Label ? env->GetStringUTFChars(button2Label, nullptr) : nullptr;
    const char* cBtn2Url = button2Url ? env->GetStringUTFChars(button2Url, nullptr) : nullptr;

    g_discordBridge.SetActivity(
        static_cast<int>(activityType),
        cName, cState, cDetails,
        static_cast<int64_t>(startSecs), static_cast<int64_t>(endSecs),
        cLargeImage, cLargeText, cSmallImage, cSmallText,
        cBtn1Label, cBtn1Url, cBtn2Label, cBtn2Url
    );

    if (cName) env->ReleaseStringUTFChars(name, cName);
    if (cState) env->ReleaseStringUTFChars(state, cState);
    if (cDetails) env->ReleaseStringUTFChars(details, cDetails);
    if (cLargeImage) env->ReleaseStringUTFChars(largeImage, cLargeImage);
    if (cLargeText) env->ReleaseStringUTFChars(largeText, cLargeText);
    if (cSmallImage) env->ReleaseStringUTFChars(smallImage, cSmallImage);
    if (cSmallText) env->ReleaseStringUTFChars(smallText, cSmallText);
    if (cBtn1Label) env->ReleaseStringUTFChars(button1Label, cBtn1Label);
    if (cBtn1Url) env->ReleaseStringUTFChars(button1Url, cBtn1Url);
    if (cBtn2Label) env->ReleaseStringUTFChars(button2Label, cBtn2Label);
    if (cBtn2Url) env->ReleaseStringUTFChars(button2Url, cBtn2Url);
}

JNIEXPORT void JNICALL
Java_eu_kanade_tachiyomi_data_connections_discord_DiscordRpcManager_nativeSetOnlineStatus(
    JNIEnv* env, jobject thiz, jint statusType
) {
    g_discordBridge.SetOnlineStatus(static_cast<int>(statusType));
}

JNIEXPORT void JNICALL
Java_eu_kanade_tachiyomi_data_connections_discord_DiscordRpcManager_nativeClear(
    JNIEnv* env, jobject thiz
) {
    g_discordBridge.Clear();
}

JNIEXPORT void JNICALL
Java_eu_kanade_tachiyomi_data_connections_discord_DiscordRpcManager_nativeRunCallbacks(
    JNIEnv* env, jobject thiz
) {
    g_discordBridge.RunCallbacks();
}

JNIEXPORT void JNICALL
Java_eu_kanade_tachiyomi_data_connections_discord_DiscordRpcManager_nativeDestroy(
    JNIEnv* env, jobject thiz
) {
    g_discordBridge.Destroy();
}

} // extern "C"
