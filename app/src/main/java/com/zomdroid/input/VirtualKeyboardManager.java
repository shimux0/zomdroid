package com.zomdroid.input;

import android.content.Context;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.zomdroid.R;

/**
 * Manages the virtual keyboard overlay with mouse/touchpad and keyboard modes.
 * Provides virtual input controls that overlay the game screen.
 */
public class VirtualKeyboardManager {
    private static final String LOG_TAG = VirtualKeyboardManager.class.getName();
    
    // Overlay container and main view
    private FrameLayout overlayContainer;
    private View overlayView;
    private boolean isOverlayVisible = false;
    
    // Overlay mode layouts
    private LinearLayout mouseModeLayout;
    private LinearLayout keyboardModeLayout;
    private FrameLayout touchpadArea;
    
    // Mouse mode variables
    private float lastTouchX = 0f;
    private float lastTouchY = 0f;
    private float cursorSensitivity = 2.0f;
    
    // Context reference
    private Context context;
    
    // Callback interface for virtual keyboard events
    public interface VirtualKeyboardCallback {
        void onOverlayVisibilityChanged(boolean visible);
        void onMouseClick(int button, boolean pressed);
        void onCursorMove(float deltaX, float deltaY);
        void onKeyPress(int keyCode);
    }
    
    private VirtualKeyboardCallback callback;

    public VirtualKeyboardManager(Context context, VirtualKeyboardCallback callback) {
        this.context = context;
        this.callback = callback;
        initializeOverlay();
    }

    /**
     * Initialize the overlay and all its components
     */
    private void initializeOverlay() {
        // Create overlay container
        overlayContainer = new FrameLayout(context);
        overlayContainer.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        overlayContainer.setVisibility(View.GONE);

        // Inflate overlay layout
        overlayView = LayoutInflater.from(context).inflate(R.layout.overlay_virtual_keyboard, overlayContainer, false);
        
        // Get references to layout components
        mouseModeLayout = overlayView.findViewById(R.id.mouse_mode_layout);
        keyboardModeLayout = overlayView.findViewById(R.id.keyboard_mode_layout);
        touchpadArea = overlayView.findViewById(R.id.touchpad_area);
        
        // Setup mouse mode
        setupMouseMode();
        
        // Setup keyboard mode
        setupKeyboardMode();
        
        overlayContainer.addView(overlayView);
        
        Log.d(LOG_TAG, "Input overlay initialized");
    }

    /**
     * Add the overlay to a parent view group
     */
    public void attachToParent(ViewGroup parentView) {
        if (overlayContainer.getParent() != null) {
            ((ViewGroup) overlayContainer.getParent()).removeView(overlayContainer);
        }
        parentView.addView(overlayContainer);
        Log.d(LOG_TAG, "Overlay attached to parent view");
    }

    /**
     * Show the overlay with touchpad mode (default)
     */
    public void showOverlay() {
        if (!isOverlayVisible) {
            overlayContainer.setVisibility(View.VISIBLE);
            showMouseMode();
            isOverlayVisible = true;
            
            // Disconnect joystick when overlay is shown
            InputNativeInterface.sendJoystickDisconnected();
            
            if (callback != null) {
                callback.onOverlayVisibilityChanged(true);
            }
            Log.d(LOG_TAG, "Overlay shown - joystick disconnected");
        }
    }

    /**
     * Hide the overlay
     */
    public void hideOverlay() {
        if (isOverlayVisible) {
            overlayContainer.setVisibility(View.GONE);
            isOverlayVisible = false;
            
            // Reconnect joystick when overlay is hidden
            InputNativeInterface.sendJoystickConnected();
            
            if (callback != null) {
                callback.onOverlayVisibilityChanged(false);
            }
            Log.d(LOG_TAG, "Overlay hidden - joystick reconnected");
        }
    }

    /**
     * Check if overlay is currently visible
     */
    public boolean isVisible() {
        return isOverlayVisible;
    }

    /**
     * Toggle overlay visibility
     */
    public void toggleOverlay() {
        if (isOverlayVisible) {
            hideOverlay();
        } else {
            showOverlay();
        }
    }

    /**
     * Show the mouse/touchpad mode
     */
    private void showMouseMode() {
        mouseModeLayout.setVisibility(View.VISIBLE);
        keyboardModeLayout.setVisibility(View.GONE);
        Log.d(LOG_TAG, "Mouse mode activated");
    }

    /**
     * Show the keyboard mode
     */
    private void showKeyboardMode() {
        mouseModeLayout.setVisibility(View.GONE);
        keyboardModeLayout.setVisibility(View.VISIBLE);
        Log.d(LOG_TAG, "Virtual keyboard mode activated");
    }

    /**
     * Setup mouse/touchpad mode functionality
     */
    private void setupMouseMode() {
        Button leftClickButton = overlayView.findViewById(R.id.btn_left_click);
        Button rightClickButton = overlayView.findViewById(R.id.btn_right_click);
        Button keyboardModeQuickButton = overlayView.findViewById(R.id.btn_keyboard_mode_quick);

        if (leftClickButton != null) {
            leftClickButton.setOnClickListener(v -> {
                if (callback != null) {
                    callback.onMouseClick(GLFWBinding.MOUSE_BUTTON_LEFT.code, true);
                    // Release after short delay
                    v.postDelayed(() -> callback.onMouseClick(GLFWBinding.MOUSE_BUTTON_LEFT.code, false), 100);
                }
                Log.d(LOG_TAG, "Left click");
            });
        }

        if (rightClickButton != null) {
            rightClickButton.setOnClickListener(v -> {
                if (callback != null) {
                    callback.onMouseClick(GLFWBinding.MOUSE_BUTTON_RIGHT.code, true);
                    // Release after short delay  
                    v.postDelayed(() -> callback.onMouseClick(GLFWBinding.MOUSE_BUTTON_RIGHT.code, false), 100);
                }
                Log.d(LOG_TAG, "Right click");
            });
        }

        if (keyboardModeQuickButton != null) {
            keyboardModeQuickButton.setOnClickListener(v -> showKeyboardMode());
        }

        // Setup touchpad area
        if (touchpadArea != null) {
            touchpadArea.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            lastTouchX = event.getX();
                            lastTouchY = event.getY();
                            return true;

                        case MotionEvent.ACTION_MOVE:
                            float deltaX = (event.getX() - lastTouchX) * cursorSensitivity;
                            float deltaY = (event.getY() - lastTouchY) * cursorSensitivity;
                            
                            if (callback != null) {
                                callback.onCursorMove(deltaX, deltaY);
                            }
                            
                            lastTouchX = event.getX();
                            lastTouchY = event.getY();
                            return true;
                    }
                    return false;
                }
            });
        }
    }

    /**
     * Setup keyboard mode functionality
     */
    private void setupKeyboardMode() {
        // Setup all keyboard buttons
        setupKeyboardButton(R.id.key_0, KeyEvent.KEYCODE_0);
        setupKeyboardButton(R.id.key_1, KeyEvent.KEYCODE_1);
        setupKeyboardButton(R.id.key_2, KeyEvent.KEYCODE_2);
        setupKeyboardButton(R.id.key_3, KeyEvent.KEYCODE_3);
        setupKeyboardButton(R.id.key_4, KeyEvent.KEYCODE_4);
        setupKeyboardButton(R.id.key_5, KeyEvent.KEYCODE_5);
        setupKeyboardButton(R.id.key_6, KeyEvent.KEYCODE_6);
        setupKeyboardButton(R.id.key_7, KeyEvent.KEYCODE_7);
        setupKeyboardButton(R.id.key_8, KeyEvent.KEYCODE_8);
        setupKeyboardButton(R.id.key_9, KeyEvent.KEYCODE_9);
        
        setupKeyboardButton(R.id.key_a, KeyEvent.KEYCODE_A);
        setupKeyboardButton(R.id.key_b, KeyEvent.KEYCODE_B);
        setupKeyboardButton(R.id.key_c, KeyEvent.KEYCODE_C);
        setupKeyboardButton(R.id.key_d, KeyEvent.KEYCODE_D);
        setupKeyboardButton(R.id.key_e, KeyEvent.KEYCODE_E);
        setupKeyboardButton(R.id.key_f, KeyEvent.KEYCODE_F);
        setupKeyboardButton(R.id.key_g, KeyEvent.KEYCODE_G);
        setupKeyboardButton(R.id.key_h, KeyEvent.KEYCODE_H);
        setupKeyboardButton(R.id.key_i, KeyEvent.KEYCODE_I);
        setupKeyboardButton(R.id.key_j, KeyEvent.KEYCODE_J);
        setupKeyboardButton(R.id.key_k, KeyEvent.KEYCODE_K);
        setupKeyboardButton(R.id.key_l, KeyEvent.KEYCODE_L);
        setupKeyboardButton(R.id.key_m, KeyEvent.KEYCODE_M);
        setupKeyboardButton(R.id.key_n, KeyEvent.KEYCODE_N);
        setupKeyboardButton(R.id.key_o, KeyEvent.KEYCODE_O);
        setupKeyboardButton(R.id.key_p, KeyEvent.KEYCODE_P);
        setupKeyboardButton(R.id.key_q, KeyEvent.KEYCODE_Q);
        setupKeyboardButton(R.id.key_r, KeyEvent.KEYCODE_R);
        setupKeyboardButton(R.id.key_s, KeyEvent.KEYCODE_S);
        setupKeyboardButton(R.id.key_t, KeyEvent.KEYCODE_T);
        setupKeyboardButton(R.id.key_u, KeyEvent.KEYCODE_U);
        setupKeyboardButton(R.id.key_v, KeyEvent.KEYCODE_V);
        setupKeyboardButton(R.id.key_w, KeyEvent.KEYCODE_W);
        setupKeyboardButton(R.id.key_x, KeyEvent.KEYCODE_X);
        setupKeyboardButton(R.id.key_y, KeyEvent.KEYCODE_Y);
        setupKeyboardButton(R.id.key_z, KeyEvent.KEYCODE_Z);
        
        setupKeyboardButton(R.id.key_space, KeyEvent.KEYCODE_SPACE);
        setupKeyboardButton(R.id.key_enter, KeyEvent.KEYCODE_ENTER);
        setupKeyboardButton(R.id.key_backspace, KeyEvent.KEYCODE_DEL);
        
        // Back to touchpad button
        Button backButton = overlayView.findViewById(R.id.btn_back_to_touchpad);
        if (backButton != null) {
            backButton.setOnClickListener(v -> showMouseMode());
        }
    }

    /**
     * Setup a keyboard button with its corresponding key code
     */
    private void setupKeyboardButton(int buttonId, int keyCode) {
        Button button = overlayView.findViewById(buttonId);
        if (button != null) {
            button.setOnClickListener(v -> {
                if (callback != null) {
                    callback.onKeyPress(keyCode);
                }
                Log.d(LOG_TAG, "Virtual key pressed: " + keyCode);
            });
        }
    }

    /**
     * Set cursor sensitivity for touchpad
     */
    public void setCursorSensitivity(float sensitivity) {
        this.cursorSensitivity = Math.max(0.1f, Math.min(5.0f, sensitivity));
        Log.d(LOG_TAG, "Cursor sensitivity set to: " + this.cursorSensitivity);
    }

    /**
     * Get current cursor sensitivity
     */
    public float getCursorSensitivity() {
        return cursorSensitivity;
    }

    /**
     * Cleanup resources
     */
    public void destroy() {
        if (overlayContainer != null && overlayContainer.getParent() != null) {
            ((ViewGroup) overlayContainer.getParent()).removeView(overlayContainer);
        }
        context = null;
        callback = null;
        Log.d(LOG_TAG, "Input overlay destroyed");
    }
}
