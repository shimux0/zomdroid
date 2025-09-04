package com.zomdroid.input;

import android.content.Context;
import android.hardware.input.InputManager;
import android.util.Log;
import android.view.InputDevice;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages gamepad connections and disconnections.
 * Handles the visibility of virtual controller based on gamepad state.
 */
public class GamepadManager implements InputManager.InputDeviceListener {
    private static final String LOG_TAG = "GamepadManager";
    private static GamepadManager instance;
    
    private final InputManager inputManager;
    private final Map<Integer, InputDevice> connectedGamepads = new HashMap<>();
    private boolean isVirtualControllerVisible = true;
    
    private GamepadStateListener stateListener;
    
    public interface GamepadStateListener {
        void onGamepadConnected(int deviceId);
        void onGamepadDisconnected(int deviceId);
        void onVirtualControllerVisibilityChanged(boolean visible);
    }
    
    private GamepadManager(Context context) {
        inputManager = (InputManager) context.getSystemService(Context.INPUT_SERVICE);
        inputManager.registerInputDeviceListener(this, null);
        
        // Check for already connected gamepads
        scanForConnectedGamepads();
    }
    
    public static void init(Context context) {
        if (instance == null) {
            instance = new GamepadManager(context);
        }
    }
    
    public static GamepadManager getInstance() {
        return instance;
    }
    
    public void setStateListener(GamepadStateListener listener) {
        this.stateListener = listener;
    }
    
    private void scanForConnectedGamepads() {
        int[] deviceIds = inputManager.getInputDeviceIds();
        for (int deviceId : deviceIds) {
            InputDevice device = inputManager.getInputDevice(deviceId);
            if (isGamepad(device)) {
                onInputDeviceAdded(deviceId);
            }
        }
    }
    
    private boolean isGamepad(InputDevice device) {
        if (device == null) return false;
        
        int sources = device.getSources();
        return (sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }
    
    @Override
    public void onInputDeviceAdded(int deviceId) {
        InputDevice device = inputManager.getInputDevice(deviceId);
        if (isGamepad(device)) {
            Log.i(LOG_TAG, "Gamepad connected: " + device.getName() + " (ID: " + deviceId + ")");
            
            connectedGamepads.put(deviceId, device);
            updateVirtualControllerVisibility();
            
            // Notify native layer about gamepad connection
            InputNativeInterface.sendGamepadConnected(deviceId);
            
            if (stateListener != null) {
                stateListener.onGamepadConnected(deviceId);
            }
        }
    }
    
    @Override
    public void onInputDeviceRemoved(int deviceId) {
        if (connectedGamepads.containsKey(deviceId)) {
            InputDevice device = connectedGamepads.remove(deviceId);
            Log.i(LOG_TAG, "Gamepad disconnected: " + device.getName() + " (ID: " + deviceId + ")");
            
            updateVirtualControllerVisibility();
            
            // Notify native layer about gamepad disconnection
            InputNativeInterface.sendGamepadDisconnected(deviceId);
            
            if (stateListener != null) {
                stateListener.onGamepadDisconnected(deviceId);
            }
        }
    }
    
    @Override
    public void onInputDeviceChanged(int deviceId) {
        // Handle device changes if needed
        InputDevice device = inputManager.getInputDevice(deviceId);
        if (device != null && isGamepad(device)) {
            connectedGamepads.put(deviceId, device);
        }
    }
    
    private void updateVirtualControllerVisibility() {
        boolean shouldShowVirtualController = connectedGamepads.isEmpty();
        
        if (shouldShowVirtualController != isVirtualControllerVisible) {
            isVirtualControllerVisible = shouldShowVirtualController;
            Log.i(LOG_TAG, "Virtual controller visibility: " + isVirtualControllerVisible);
            
            if (stateListener != null) {
                stateListener.onVirtualControllerVisibilityChanged(isVirtualControllerVisible);
            }
        }
    }
    
    public boolean isVirtualControllerVisible() {
        return isVirtualControllerVisible;
    }
    
    public boolean hasGamepads() {
        return !connectedGamepads.isEmpty();
    }
    
    public Map<Integer, InputDevice> getConnectedGamepads() {
        return new HashMap<>(connectedGamepads);
    }
    
    public void destroy() {
        if (inputManager != null) {
            inputManager.unregisterInputDeviceListener(this);
        }
        connectedGamepads.clear();
    }
}
