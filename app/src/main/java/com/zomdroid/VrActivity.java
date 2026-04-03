package com.zomdroid;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;

/**
 * Immersive VR activity for OpenXR rendering on Quest 3.
 * All rendering is handled natively via OpenXR — no Android surface needed.
 */
public class VrActivity extends Activity {
    private static final String LOG_TAG = VrActivity.class.getName();

    static {
        System.loadLibrary("zomdroid_xr");
    }

    private native void nativeXrCreate();
    private native void nativeXrDestroy();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Log.i(LOG_TAG, "VrActivity created");
        nativeXrCreate();
    }

    @Override
    protected void onDestroy() {
        Log.i(LOG_TAG, "VrActivity destroying");
        nativeXrDestroy();
        super.onDestroy();
    }
}
