package mihon.app.shizuku;

import android.content.IntentSender;
import android.content.res.AssetFileDescriptor;

interface IShellInterface {
    void install(in AssetFileDescriptor apk, in IntentSender intentSender) = 1;

    void destroy() = 16777114;
}
