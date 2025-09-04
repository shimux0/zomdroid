package com.zomdroid.input;

import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

/**
 * Handles input from gamepads and translates them to game events.
 * Maps Xbox controller layout to game controls.
 */
public class GamepadInputHandler {
    private static final String LOG_TAG = "GamepadInputHandler";
    
    // Xbox controller button mappings to GLFW gamepad buttons
    private static final int BUTTON_A = KeyEvent.KEYCODE_BUTTON_A;           // GLFW 0
    private static final int BUTTON_B = KeyEvent.KEYCODE_BUTTON_B;           // GLFW 1  
    private static final int BUTTON_X = KeyEvent.KEYCODE_BUTTON_X;           // GLFW 2
    private static final int BUTTON_Y = KeyEvent.KEYCODE_BUTTON_Y;           // GLFW 3
    private static final int BUTTON_LB = KeyEvent.KEYCODE_BUTTON_L1;         // GLFW 4
    private static final int BUTTON_RB = KeyEvent.KEYCODE_BUTTON_R1;         // GLFW 5
    private static final int BUTTON_BACK = KeyEvent.KEYCODE_BUTTON_SELECT;   // GLFW 6
    private static final int BUTTON_START = KeyEvent.KEYCODE_BUTTON_START;   // GLFW 7
    private static final int BUTTON_GUIDE = KeyEvent.KEYCODE_BUTTON_MODE;    // GLFW 8
    private static final int BUTTON_LSTICK = KeyEvent.KEYCODE_BUTTON_THUMBL; // GLFW 9
    private static final int BUTTON_RSTICK = KeyEvent.KEYCODE_BUTTON_THUMBR; // GLFW 10
    
    // D-Pad mappings
    private static final int DPAD_UP = KeyEvent.KEYCODE_DPAD_UP;
    private static final int DPAD_DOWN = KeyEvent.KEYCODE_DPAD_DOWN;
    private static final int DPAD_LEFT = KeyEvent.KEYCODE_DPAD_LEFT;
    private static final int DPAD_RIGHT = KeyEvent.KEYCODE_DPAD_RIGHT;
    
    // Axis mappings (match Xbox controller layout)
    private static final int AXIS_LX = MotionEvent.AXIS_X;          // Left stick X (GLFW 0)
    private static final int AXIS_LY = MotionEvent.AXIS_Y;          // Left stick Y (GLFW 1)
    private static final int AXIS_RX = MotionEvent.AXIS_Z;          // Right stick X (GLFW 2)
    private static final int AXIS_RY = MotionEvent.AXIS_RZ;         // Right stick Y (GLFW 3)
    private static final int AXIS_LT = MotionEvent.AXIS_LTRIGGER;   // Left trigger (GLFW 4)
    private static final int AXIS_RT = MotionEvent.AXIS_RTRIGGER;   // Right trigger (GLFW 5)
    
    private int deviceId;
    private float lastLTriggerValue = 0.0f;
    private float lastRTriggerValue = 0.0f;
    private int lastDpadState = 0; // Bitmask for D-pad state
    
    public GamepadInputHandler(int deviceId) {
        this.deviceId = deviceId;
    }
    
    /**
     * Handle key events from gamepad
     */
    public boolean handleKeyEvent(KeyEvent event) {
        if (event.getDeviceId() != deviceId) {
            return false;
        }
        
        int keyCode = event.getKeyCode();
        boolean isPressed = event.getAction() == KeyEvent.ACTION_DOWN;
        
        int glfwButton = mapKeyCodeToGLFWButton(keyCode);
        if (glfwButton >= 0) {
            Log.d(LOG_TAG, "Gamepad button " + keyCode + " -> GLFW " + glfwButton + " " + (isPressed ? "pressed" : "released"));
            InputNativeInterface.sendJoystickButton(glfwButton, isPressed);
            return true;
        }
        
        // Handle D-pad
        if (isDpadKey(keyCode)) {
            handleDpadEvent(keyCode, isPressed);
            return true;
        }
        
        return false;
    }
    
    /**
     * Handle motion events from gamepad (analog sticks, triggers)
     */
    public boolean handleMotionEvent(MotionEvent event) {
        if (event.getDeviceId() != deviceId) {
            return false;
        }
        
        InputDevice device = event.getDevice();
        if (device == null) {
            return false;
        }
        
        // Handle analog sticks
        handleAnalogStick(event, AXIS_LX, 0); // Left stick X -> GLFW axis 0
        handleAnalogStick(event, AXIS_LY, 1); // Left stick Y -> GLFW axis 1 (inverted)
        handleAnalogStick(event, AXIS_RX, 2); // Right stick X -> GLFW axis 2
        handleAnalogStick(event, AXIS_RY, 3); // Right stick Y -> GLFW axis 3 (inverted)
        
        // Handle triggers
        handleTrigger(event, AXIS_LT, 4); // Left trigger -> GLFW axis 4
        handleTrigger(event, AXIS_RT, 5); // Right trigger -> GLFW axis 5
        
        return true;
    }
    
    private void handleAnalogStick(MotionEvent event, int androidAxis, int glfwAxis) {
        float value = event.getAxisValue(androidAxis);
        
        // Invert Y axes to match expected game behavior
        if (androidAxis == AXIS_LY || androidAxis == AXIS_RY) {
            value = -value;
        }
        
        // Apply deadzone
        if (Math.abs(value) < 0.1f) {
            value = 0.0f;
        }
        
        InputNativeInterface.sendJoystickAxis(glfwAxis, value);
    }
    
    private void handleTrigger(MotionEvent event, int androidAxis, int glfwAxis) {
        float value = event.getAxisValue(androidAxis);
        float lastValue = (androidAxis == AXIS_LT) ? lastLTriggerValue : lastRTriggerValue;
        
        // Only send if value changed significantly
        if (Math.abs(value - lastValue) > 0.01f) {
            InputNativeInterface.sendJoystickAxis(glfwAxis, value);
            
            if (androidAxis == AXIS_LT) {
                lastLTriggerValue = value;
            } else {
                lastRTriggerValue = value;
            }
        }
    }
    
    private void handleDpadEvent(int keyCode, boolean isPressed) {
        int dpadBit = getDpadBit(keyCode);
        if (dpadBit == 0) return;
        
        if (isPressed) {
            lastDpadState |= dpadBit;
        } else {
            lastDpadState &= ~dpadBit;
        }
        
        // Convert to GLFW hat value
        char hatValue = convertDpadStateToHatValue(lastDpadState);
        InputNativeInterface.sendJoystickDpad(0, hatValue); // Hat 0
    }
    
    private int mapKeyCodeToGLFWButton(int keyCode) {
        if (keyCode == BUTTON_A) return 0;      // GLFW_GAMEPAD_BUTTON_A
        if (keyCode == BUTTON_B) return 1;      // GLFW_GAMEPAD_BUTTON_B
        if (keyCode == BUTTON_X) return 2;      // GLFW_GAMEPAD_BUTTON_X
        if (keyCode == BUTTON_Y) return 3;      // GLFW_GAMEPAD_BUTTON_Y
        if (keyCode == BUTTON_LB) return 4;     // GLFW_GAMEPAD_BUTTON_LEFT_BUMPER
        if (keyCode == BUTTON_RB) return 5;     // GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER
        if (keyCode == BUTTON_BACK) return 6;   // GLFW_GAMEPAD_BUTTON_BACK
        if (keyCode == BUTTON_START) return 7;  // GLFW_GAMEPAD_BUTTON_START
        if (keyCode == BUTTON_GUIDE) return 8;  // GLFW_GAMEPAD_BUTTON_GUIDE
        if (keyCode == BUTTON_LSTICK) return 9; // GLFW_GAMEPAD_BUTTON_LEFT_THUMB
        if (keyCode == BUTTON_RSTICK) return 10;// GLFW_GAMEPAD_BUTTON_RIGHT_THUMB
        return -1;
    }
    
    private boolean isDpadKey(int keyCode) {
        return keyCode == DPAD_UP || keyCode == DPAD_DOWN || 
               keyCode == DPAD_LEFT || keyCode == DPAD_RIGHT;
    }
    
    private int getDpadBit(int keyCode) {
        if (keyCode == DPAD_UP) return 1;
        if (keyCode == DPAD_RIGHT) return 2;
        if (keyCode == DPAD_DOWN) return 4;
        if (keyCode == DPAD_LEFT) return 8;
        return 0;
    }
    
    private char convertDpadStateToHatValue(int dpadState) {
        // GLFW hat values: 0=center, 1=up, 2=right, 3=down, 4=left
        // Diagonal combinations: 5=up-right, 6=down-right, 7=down-left, 8=up-left
        if (dpadState == 0) return 0;   // Center
        if (dpadState == 1) return 1;   // Up
        if (dpadState == 2) return 2;   // Right  
        if (dpadState == 3) return 5;   // Up + Right
        if (dpadState == 4) return 3;   // Down
        if (dpadState == 5) return 1;   // Up + Down (impossible, prefer Up)
        if (dpadState == 6) return 6;   // Down + Right
        if (dpadState == 7) return 5;   // Up + Down + Right (prefer Up + Right)
        if (dpadState == 8) return 4;   // Left
        if (dpadState == 9) return 8;   // Up + Left
        if (dpadState == 10) return 2;  // Right + Left (impossible, prefer Right)
        if (dpadState == 11) return 5;  // Up + Right + Left (prefer Up + Right)
        if (dpadState == 12) return 7;  // Down + Left
        if (dpadState == 13) return 8;  // Up + Down + Left (prefer Up + Left)
        if (dpadState == 14) return 6;  // Down + Right + Left (prefer Down + Right)
        if (dpadState == 15) return 5;  // All directions (prefer Up + Right)
        return 0; // Default to center
    }
}
