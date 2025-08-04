package com.zomdroid;

import android.annotation.SuppressLint;
import android.content.pm.ActivityInfo;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.system.ErrnoException;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.zomdroid.input.GLFWBinding;
import com.zomdroid.input.InputNativeInterface;
import com.zomdroid.databinding.ActivityGameBinding;
import com.zomdroid.game.GameInstance;
import com.zomdroid.game.GameInstanceManager;

import org.fmod.FMOD;

import java.lang.ref.WeakReference;


public class GameActivity extends AppCompatActivity {
    public static final String EXTRA_GAME_INSTANCE_NAME = "com.zomdroid.GameActivity.EXTRA_GAME_INSTANCE_NAME";
    private static final String LOG_TAG = GameActivity.class.getName();

    private ActivityGameBinding binding;
    private Surface gameSurface;
    private static boolean isGameStarted = false;
    private GestureDetector gestureDetector;
    
    // Overlay for back gesture handling
    private FrameLayout overlayContainer;
    private View overlayView;
    private boolean isOverlayVisible = false;
    
    // Overlay mode management
    private LinearLayout mainMenuLayout;
    private LinearLayout mouseModeLayout;
    private LinearLayout keyboardModeLayout;
    private FrameLayout touchpadArea;
    
    // Mouse mode variables
    private float lastTouchX = 0f;
    private float lastTouchY = 0f;
    private float cursorSensitivity = 2.0f;

    @SuppressLint({"UnsafeDynamicallyLoadedCode", "ClickableViewAccessibility"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityGameBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize overlay for back gesture handling
        initializeOverlay();

        getWindow().setDecorFitsSystemWindows(false);
        final WindowInsetsController controller = getWindow().getInsetsController();
        if (controller != null) {
            controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            controller.setSystemBarsBehavior(
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

        String gameInstanceName = getIntent().getStringExtra(EXTRA_GAME_INSTANCE_NAME);
        if (gameInstanceName == null)
            throw new RuntimeException("Expected game instance name to be passed as intent extra");
        GameInstance gameInstance = GameInstanceManager.requireSingleton().getInstanceByName(gameInstanceName);
        if (gameInstance == null)
            throw new RuntimeException("Game instance with name " + gameInstanceName + " not found");

        System.loadLibrary("zomdroid");

        System.load(AppStorage.requireSingleton().getHomePath() + "/" + gameInstance.getFmodLibraryPath() + "/libfmod.so");
        System.load(AppStorage.requireSingleton().getHomePath() + "/" + gameInstance.getFmodLibraryPath() + "/libfmodstudio.so");
/*        System.loadLibrary("fmod");
        System.loadLibrary("fmodstudio");*/

        FMOD.init(this);

/*        gestureDetector = new GestureDetector(this, new GestureDetector.OnGestureListener() {
            private boolean showPress = false;
            @Override
            public boolean onDown(@NonNull MotionEvent e) {
                Log.v("", "onDown " + e.getX() + " " + e.getY());
                //InputBridge.sendMouseButton(GLFWConstants.GLFW_MOUSE_BUTTON_LEFT, GLFWConstants.GLFW_PRESS, event.getX(), event.getY());
                return true;
            }

            @Override
            public void onShowPress(@NonNull MotionEvent e) {
                Log.v("", "onShowPress " + e.getX() + " " + e.getY());
                showPress = true;
                InputNativeInterface.sendCursorPos(e.getX(), e.getY());
                InputNativeInterface.sendMouseButton(GLFWBinding.MOUSE_BUTTON_LEFT.code, true);
            }

            @Override
            public boolean onSingleTapUp(@NonNull MotionEvent e) {
                Log.v("", "onSingleTapUp " + e.getX() + " " + e.getY());
                InputNativeInterface.sendCursorPos(e.getX(), e.getY());
                if (showPress) {
                    InputNativeInterface.sendMouseButton(GLFWBinding.MOUSE_BUTTON_LEFT.code, false);
                }
                showPress = false;
                return true;
            }

            @Override
            public boolean onScroll(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
                InputNativeInterface.sendCursorPos(e2.getX(), e2.getY());
                Log.v("", "onScroll " + (e1 == null ? "0" : e1.getX()) + " " + (e1 == null ? "0" : e1.getY()) + " " + e2.getX() + " " + e2.getY());
                return true;
            }

            @Override
            public void onLongPress(@NonNull MotionEvent e) {
                Log.v("", "onLongPress " + e.getX() + " " + e.getY());
            }

            @Override
            public boolean onFling(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
                Log.v("", "onFling " + velocityX + " " + velocityY);
                return true;
            }
        });
        gestureDetector.setIsLongpressEnabled(false);*/

        binding.gameSv.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                Log.d(LOG_TAG, "Game surface created.");
                float renderScale = LauncherPreferences.requireSingleton().getRenderScale();
                int width = (int) (binding.gameSv.getWidth() * renderScale);
                int height = (int) (binding.gameSv.getHeight() * renderScale);
                binding.gameSv.getHolder().setFixedSize(width, height);
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
                Log.d(LOG_TAG, "Game surface changed.");


                gameSurface = binding.gameSv.getHolder().getSurface();
                if (gameSurface == null) throw new RuntimeException();

                if (format != PixelFormat.RGBA_8888) {
                    Log.w(LOG_TAG, "Using unsupported pixel format " + format); // LIAMELUI seems like default is RGB_565
                }

                GameLauncher.setSurface(gameSurface, width, height);
                if (!isGameStarted) {
                    Thread thread = new Thread(() -> {
                        try {
                            GameLauncher.launch(gameInstance);
                        } catch (ErrnoException e) {
                            throw new RuntimeException(e);
                        }
                    });
                    thread.start();
                    isGameStarted = true;
                }
            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                Log.d(LOG_TAG, "Game surface destroyed.");
                GameLauncher.destroySurface();
            }
        });

        binding.gameSv.setOnTouchListener(new View.OnTouchListener() {
            float renderScale = LauncherPreferences.requireSingleton().getRenderScale();
            int pointerId = -1;

            @Override
            public boolean onTouch(View v, MotionEvent e) { // this should be in InputControlsView
                int action = e.getActionMasked();
                int actionIndex = e.getActionIndex();
                int pointerId = e.getPointerId(actionIndex);
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                    case MotionEvent.ACTION_POINTER_DOWN: {
                        float x = e.getX(actionIndex);
                        float y = e.getY(actionIndex);
                        this.pointerId = pointerId;
                        InputNativeInterface.sendCursorPos(x * this.renderScale, y * this.renderScale);
                        InputNativeInterface.sendMouseButton(GLFWBinding.MOUSE_BUTTON_LEFT.code, true);
                        return true;
                    }
                    case MotionEvent.ACTION_MOVE: {
                        if (this.pointerId < 0) return false;
                        int pointerIndex = e.findPointerIndex(this.pointerId);
                        if (pointerIndex < 0) {
                            this.pointerId = -1;
                            return false;
                        }
                        float x = e.getX(pointerIndex);
                        float y = e.getY(pointerIndex);
                        InputNativeInterface.sendCursorPos(x * this.renderScale, y * this.renderScale);
                        return false;
                    }
                    case MotionEvent.ACTION_UP: {
                        if (pointerId != this.pointerId) return false;
                        this.pointerId = -1;
                        InputNativeInterface.sendMouseButton(GLFWBinding.MOUSE_BUTTON_LEFT.code, false);
                        return true;
                    }
                }
                return false;
            }
        });
    }

    private void initializeOverlay() {
        // Create overlay container
        overlayContainer = new FrameLayout(this);
        overlayContainer.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        overlayContainer.setVisibility(View.GONE);

        // Inflate overlay layout
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_test, overlayContainer, false);
        
        // Get references to layout components
        mainMenuLayout = overlayView.findViewById(R.id.main_menu_layout);
        mouseModeLayout = overlayView.findViewById(R.id.mouse_mode_layout);
        keyboardModeLayout = overlayView.findViewById(R.id.keyboard_mode_layout);
        touchpadArea = overlayView.findViewById(R.id.touchpad_area);
        
        // Setup main menu buttons
        Button closeButton = overlayView.findViewById(R.id.btn_close_overlay);
        Button mouseModeButton = overlayView.findViewById(R.id.btn_mouse_mode);
        Button keyboardModeButton = overlayView.findViewById(R.id.btn_keyboard_mode);
        
        closeButton.setOnClickListener(v -> hideOverlay());
        mouseModeButton.setOnClickListener(v -> showMouseMode());
        keyboardModeButton.setOnClickListener(v -> showKeyboardMode());
        
        // Setup mouse mode
        setupMouseMode();
        
        // Setup keyboard mode
        setupKeyboardMode();
        
        overlayContainer.addView(overlayView);
        
        // Add overlay to the root view
        ViewGroup rootView = (ViewGroup) binding.getRoot();
        rootView.addView(overlayContainer);
    }

    private void showOverlay() {
        if (!isOverlayVisible) {
            overlayContainer.setVisibility(View.VISIBLE);
            showMainMenu();
            isOverlayVisible = true;
            Log.d(LOG_TAG, "Overlay shown");
        }
    }

    private void hideOverlay() {
        if (isOverlayVisible) {
            overlayContainer.setVisibility(View.GONE);
            isOverlayVisible = false;
            Log.d(LOG_TAG, "Overlay hidden");
        }
    }

    private void showMainMenu() {
        mainMenuLayout.setVisibility(View.VISIBLE);
        mouseModeLayout.setVisibility(View.GONE);
        keyboardModeLayout.setVisibility(View.GONE);
    }

    private void showMouseMode() {
        mainMenuLayout.setVisibility(View.GONE);
        mouseModeLayout.setVisibility(View.VISIBLE);
        keyboardModeLayout.setVisibility(View.GONE);
        Log.d(LOG_TAG, "Mouse mode activated");
    }

    private void showKeyboardMode() {
        mainMenuLayout.setVisibility(View.GONE);
        mouseModeLayout.setVisibility(View.GONE);
        keyboardModeLayout.setVisibility(View.VISIBLE);
        Log.d(LOG_TAG, "Keyboard mode activated");
    }

    private void setupMouseMode() {
        Button leftClickButton = overlayView.findViewById(R.id.btn_left_click);
        Button rightClickButton = overlayView.findViewById(R.id.btn_right_click);
        Button backToMenuButton = overlayView.findViewById(R.id.btn_back_to_menu);

        leftClickButton.setOnClickListener(v -> {
            InputNativeInterface.sendMouseButton(GLFWBinding.MOUSE_BUTTON_LEFT.code, true);
            // Release after short delay
            v.postDelayed(() -> InputNativeInterface.sendMouseButton(GLFWBinding.MOUSE_BUTTON_LEFT.code, false), 100);
            Log.d(LOG_TAG, "Left click");
        });

        rightClickButton.setOnClickListener(v -> {
            InputNativeInterface.sendMouseButton(GLFWBinding.MOUSE_BUTTON_RIGHT.code, true);
            // Release after short delay  
            v.postDelayed(() -> InputNativeInterface.sendMouseButton(GLFWBinding.MOUSE_BUTTON_RIGHT.code, false), 100);
            Log.d(LOG_TAG, "Right click");
        });

        backToMenuButton.setOnClickListener(v -> showMainMenu());

        // Setup touchpad area
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
                        
                        // Send relative mouse movement (simulated)
                        // Note: This is a simplified implementation
                        InputNativeInterface.sendCursorPos(deltaX, deltaY);
                        
                        lastTouchX = event.getX();
                        lastTouchY = event.getY();
                        return true;
                }
                return false;
            }
        });
    }

    private void setupKeyboardMode() {
        Button backToMenuKeyboardButton = overlayView.findViewById(R.id.btn_back_to_menu_keyboard);
        EditText textInput = overlayView.findViewById(R.id.text_input);

        backToMenuKeyboardButton.setOnClickListener(v -> showMainMenu());

        // Setup quick keys
        setupQuickKey(R.id.key_escape, KeyEvent.KEYCODE_ESCAPE);
        setupQuickKey(R.id.key_tab, KeyEvent.KEYCODE_TAB);
        setupQuickKey(R.id.key_enter, KeyEvent.KEYCODE_ENTER);
        setupQuickKey(R.id.key_space, KeyEvent.KEYCODE_SPACE);
        setupQuickKey(R.id.key_w, KeyEvent.KEYCODE_W);
        setupQuickKey(R.id.key_a, KeyEvent.KEYCODE_A);
        setupQuickKey(R.id.key_s, KeyEvent.KEYCODE_S);
        setupQuickKey(R.id.key_d, KeyEvent.KEYCODE_D);
    }

    private void setupQuickKey(int buttonId, int keyCode) {
        Button button = overlayView.findViewById(buttonId);
        if (button != null) {
            button.setOnClickListener(v -> {
                // Send key press event to the game
                sendKeyEvent(keyCode);
                Log.d(LOG_TAG, "Key pressed: " + keyCode);
            });
        }
    }

    private void sendKeyEvent(int keyCode) {
        // This is a simplified key event sender
        // In a full implementation, you might need to send this through
        // the input native interface or handle it differently
        Log.d(LOG_TAG, "Sending key event: " + keyCode);
        // InputNativeInterface.sendKey(keyCode, true);
        // InputNativeInterface.sendKey(keyCode, false);
    }

    @Override
    public void onBackPressed() {
        // Intercept back gesture and show overlay instead of closing app
        if (isOverlayVisible) {
            // If overlay is visible, hide it
            hideOverlay();
        } else {
            // If overlay is not visible, show it
            showOverlay();
        }
        // Don't call super.onBackPressed() to prevent app from closing
    }
}