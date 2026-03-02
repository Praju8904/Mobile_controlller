package com.prajwal.myfirstapp.connectivity;

import android.content.Context;
import android.graphics.Color;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.CompoundButton;

public class TouchpadHandler implements View.OnTouchListener {

    private final ConnectionManager connectionManager;
    private final GestureDetector doubleTapDetector;
    private final View touchPadView;
    private final CompoundButton modeSwitch; // To check if we are in gesture mode
    
    private float lastX = 0, lastY = 0;
    private float startX, startY;
    private boolean isMoving = false;
    private final StringBuilder gesturePath = new StringBuilder();

    public TouchpadHandler(Context context, View touchPadView, CompoundButton modeSwitch, ConnectionManager connectionManager) {
        this.touchPadView = touchPadView;
        this.modeSwitch = modeSwitch;
        this.connectionManager = connectionManager;

        this.doubleTapDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                connectionManager.sendCommand("MEDIA_PLAY_PAUSE");
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                connectionManager.sendCommand("MOUSE_LEFT_DOWN");
                touchPadView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            }
        });
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        doubleTapDetector.onTouchEvent(event);
        float x = event.getX();
        float y = event.getY();
        int pointerCount = event.getPointerCount();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startX = x; startY = y; lastX = x; lastY = y;
                isMoving = false;
                if (pointerCount == 1) {
                    gesturePath.setLength(0);
                    gesturePath.append(x).append(",").append(y);
                }
                break;
                
            case MotionEvent.ACTION_MOVE:
                float dx = x - lastX; 
                float dy = y - lastY;
                
                if (Math.abs(x - startX) > 5 || Math.abs(y - startY) > 5) isMoving = true;
                
                if (pointerCount == 2) {
                    // 2-finger scroll
                    if (Math.abs(dy) > 10) {
                        connectionManager.sendCommand("MOUSE_SCROLL:" + (dy > 0 ? "1" : "-1"));
                    }
                } else if (pointerCount == 1) {
                    if (!modeSwitch.isChecked()) {
                        // Mouse Mode
                        connectionManager.sendCommand("MOUSE_MOVE:" + dx + "," + dy);
                    } else {
                        // Gesture Mode
                        if (Math.abs(dy) > 5) {
                            // Also scroll in gesture mode if strictly vertical? 
                            // (Preserving original logic: "if (Math.abs(dy) > 5) sendCommand("MOUSE_SCROLL:" ...")" was inside else block)
                            connectionManager.sendCommand("MOUSE_SCROLL:" + (dy > 0 ? "1" : "-1"));
                        }
                        gesturePath.append("|").append(x).append(",").append(y);
                    }
                }
                lastX = x; lastY = y;
                break;
                
            case MotionEvent.ACTION_UP:
                if (modeSwitch.isChecked() && pointerCount == 1) {
                    connectionManager.sendCommand("GESTURE:" + gesturePath.toString());
                } else if (!isMoving && pointerCount == 1) {
                    connectionManager.sendCommand("MOUSE_CLICK");
                }
                break;
        }
        return true;
    }
}
