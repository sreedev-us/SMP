/*
 * Copyright (c) 2019, 2025, Gluon
 */
package com.gluonhq.helloandroid;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputType;
import android.text.Selection;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.util.TimeZone;
import javafx.scene.input.KeyCode;

public class MainActivity extends Activity implements SurfaceHolder.Callback, SurfaceHolder.Callback2 {

    private static final String TAG = "GraalActivity";
    private static final int PRESS = 111;
    private static final int RELEASE = 112;
    private static final int TYPED = 113;
    private static final int MODIFIER_SHIFT = 1;
    private static final int MODIFIER_CONTROL = 2;
    private static final int MODIFIER_ALT = 4;
    private static final int MODIFIER_WINDOWS = 8;

    private static MainActivity instance;
    private static FrameLayout mViewGroup;
    private static SurfaceView mView;
    private static InputMethodManager imm;

    private long nativeWindowPtr;
    private float density;
    private boolean graalStarted;
    private int deadKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "onCreate start, using Android Logging v1");

        getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
                        | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        getWindow().setFormat(PixelFormat.RGBA_8888);

        mView = new InternalSurfaceView(this);
        mView.getHolder().addCallback(this);
        mViewGroup = new FrameLayout(this);
        mViewGroup.addView(mView);
        setContentView(mViewGroup);
        instance = this;

        ViewCompat.setOnApplyWindowInsetsListener(mView, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        Log.v(TAG, "onCreate done");
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.v(TAG, "surfaceCreated for " + this);
        System.loadLibrary("substrate");
        nativeSetSurface(holder.getSurface());
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        density = metrics.density;
        nativeWindowPtr = surfaceReady(holder.getSurface(), density);
        Rect currentBounds = new Rect();
        mView.getRootView().getWindowVisibleDisplayFrame(currentBounds);

        if (!graalStarted) {
            final String[] launchArgs = {
                    "-Duser.home=" + getApplicationInfo().dataDir,
                    "-Dandroid.tmpdir=" + getApplicationInfo().dataDir,
                    "-Djava.io.tmpdir=" + getApplicationInfo().dataDir,
                    "-Duser.timezone=" + TimeZone.getDefault().getID(),
                    "-DLaunch.URL=" + System.getProperty("Launch.URL", ""),
                    "-DLaunch.LocalNotification=" + System.getProperty("Launch.LocalNotification", ""),
                    "-DLaunch.PushNotification=" + System.getProperty("Launch.PushNotification", "")
            };
            Thread thread = new Thread(() -> startGraalApp(launchArgs));
            thread.start();
            graalStarted = true;
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        nativeSetSurface(holder.getSurface());
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        density = metrics.density;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        nativeSetSurface(null);
    }

    @Override
    public void surfaceRedrawNeeded(SurfaceHolder holder) {
        nativeSurfaceRedrawNeeded();
        new Handler().postDelayed(this::nativeSurfaceRedrawNeeded, 100);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        nativeDispatchActivityResult(requestCode, resultCode, intent);
    }

    static MainActivity getInstance() {
        return instance;
    }

    static ViewGroup getViewGroup() {
        return mViewGroup;
    }

    private static void showIME() {
        instance.runOnUiThread(() -> {
            mView.requestFocus();
            imm.showSoftInput(mView, 0);
        });
    }

    private static void hideIME() {
        instance.runOnUiThread(() -> {
            mView.requestFocus();
            imm.hideSoftInputFromWindow(mView.getWindowToken(), 0);
        });
    }

    private native void startGraalApp(String[] launchArgs);
    private native long surfaceReady(Surface surface, float density);
    private native void nativeSetSurface(Surface surface);
    private native void nativeSurfaceRedrawNeeded();
    private native void nativeGotTouchEvent(int pcount, int[] actions, int[] ids, int[] touchXs, int[] touchYs);
    private native void nativeDispatchKeyEvent(int type, int key, char[] chars, int charCount, int modifiers);
    private native void nativeDispatchLifecycleEvent(String event);
    private native void nativeDispatchActivityResult(int requestCode, int resultCode, Intent intent);
    private native void nativeNotifyMenu(int x, int y, int xAbs, int yAbs, boolean isKeyboardTrigger);

    class InternalSurfaceView extends SurfaceView {
        private static final int ACTION_POINTER_STILL = -1;
        private final KeyEvent backDownEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL);
        private final KeyEvent backUpEvent = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL);
        private final String enterString = new String(new byte[]{10});
        private final KeyEvent enterDownEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER);
        private final KeyEvent enterUpEvent = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER);
        private final Handler handler = new Handler();
        private final LongPress longPress = new LongPress();

        InternalSurfaceView(Context context) {
            super(context);
            setFocusableInTouchMode(true);
            setFocusable(true);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            int action = event.getAction();
            int actionCode = action & MotionEvent.ACTION_MASK;
            int pointerCount = event.getPointerCount();
            int[] actions = new int[pointerCount];
            int[] ids = new int[pointerCount];
            int[] touchXs = new int[pointerCount];
            int[] touchYs = new int[pointerCount];
            if (pointerCount > 1) {
                if (actionCode == MotionEvent.ACTION_POINTER_DOWN || actionCode == MotionEvent.ACTION_POINTER_UP) {
                    int pointerIndex = event.getActionIndex();
                    for (int i = 0; i < pointerCount; i++) {
                        actions[i] = pointerIndex == i ? actionCode : ACTION_POINTER_STILL;
                        ids[i] = event.getPointerId(i);
                        touchXs[i] = (int) (event.getX(i) / density);
                        touchYs[i] = (int) (event.getY(i) / density);
                    }
                } else if (actionCode == MotionEvent.ACTION_MOVE) {
                    for (int i = 0; i < pointerCount; i++) {
                        touchXs[i] = (int) (event.getX(i) / density);
                        touchYs[i] = (int) (event.getY(i) / density);
                        actions[i] = MotionEvent.ACTION_MOVE;
                        ids[i] = event.getPointerId(i);
                    }
                }
            } else {
                actions[0] = actionCode;
                ids[0] = event.getPointerId(0);
                touchXs[0] = (int) (event.getX() / density);
                touchYs[0] = (int) (event.getY() / density);
                if (action == MotionEvent.ACTION_DOWN) {
                    longPress.setX(touchXs[0]);
                    longPress.setY(touchYs[0]);
                    handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                }
                if (action == MotionEvent.ACTION_UP) {
                    handler.removeCallbacks(longPress);
                }
            }
            if (!isFocused()) {
                requestFocus();
            }
            nativeGotTouchEvent(pointerCount, actions, ids, touchXs, touchYs);
            return true;
        }

        @Override
        public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
            outAttrs.inputType = InputType.TYPE_CLASS_TEXT;
            outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI;

            return new BaseInputConnection(this, true) {
                @Override
                public boolean setComposingText(CharSequence text, int newCursorPosition) {
                    replaceText();
                    boolean result = super.setComposingText(text, newCursorPosition);
                    processText(text.toString());
                    return result;
                }

                @Override
                public boolean commitText(CharSequence text, int newCursorPosition) {
                    replaceText();
                    boolean result = super.commitText(text, newCursorPosition);
                    processText(text.toString());
                    return result;
                }

                @Override
                public boolean finishComposingText() {
                    boolean result = super.finishComposingText();
                    Editable content = getEditable();
                    if (content != null) {
                        content.clear();
                    }
                    return result;
                }

                @Override
                public boolean deleteSurroundingText(int beforeLength, int afterLength) {
                    boolean result = super.deleteSurroundingText(beforeLength, afterLength);
                    resetText(beforeLength - afterLength);
                    return result;
                }

                private void processText(String text) {
                    if (enterString.equals(text)) {
                        processAndroidKeyEvent(enterDownEvent);
                        processAndroidKeyEvent(enterUpEvent);
                    } else {
                        processAndroidKeyEvent(new KeyEvent(SystemClock.uptimeMillis(), text, -1, 0));
                    }
                }

                private void replaceText() {
                    Editable content = getEditable();
                    if (content == null) {
                        return;
                    }

                    int a = getComposingSpanStart(content);
                    int b = getComposingSpanEnd(content);
                    if (b < a) {
                        int tmp = a;
                        a = b;
                        b = tmp;
                    }

                    if (a == -1 || b == -1) {
                        a = Selection.getSelectionStart(content);
                        b = Selection.getSelectionEnd(content);
                        if (a < 0) a = 0;
                        if (b < 0) b = 0;
                        if (b < a) {
                            int tmp = a;
                            a = b;
                            b = tmp;
                        }
                    }
                    resetText(b - a);
                }

                private void resetText(int length) {
                    for (int i = 0; i < length; i++) {
                        processAndroidKeyEvent(backDownEvent);
                        processAndroidKeyEvent(backUpEvent);
                    }
                }
            };
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            boolean consume = true;
            if (event.getAction() == KeyEvent.ACTION_DOWN &&
                    (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP
                            || event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN
                            || event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_MUTE)) {
                consume = false;
            }
            processAndroidKeyEvent(event);
            return consume;
        }

        private class LongPress implements Runnable {
            int x;
            int y;

            void setX(int x) {
                this.x = x;
            }

            void setY(int y) {
                this.y = y;
            }

            @Override
            public void run() {
                nativeNotifyMenu(x, y, x, y, false);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        notifyLifecycleEvent("destroy");
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    @Override
    protected void onPause() {
        super.onPause();
        notifyLifecycleEvent("pause");
    }

    @Override
    protected void onResume() {
        super.onResume();
        notifyLifecycleEvent("resume");
    }

    @Override
    protected void onStart() {
        super.onStart();
        notifyLifecycleEvent("start");
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        notifyLifecycleEvent("restart");
    }

    @Override
    protected void onStop() {
        super.onStop();
        notifyLifecycleEvent("stop");
    }

    private void notifyLifecycleEvent(String event) {
        if (graalStarted) {
            nativeDispatchLifecycleEvent(event);
        }
    }

    void processAndroidKeyEvent(KeyEvent event) {
        int jfxModifiers = mapAndroidModifierToJfx(event.getMetaState());
        switch (event.getAction()) {
            case KeyEvent.ACTION_DOWN -> {
                KeyCode jfxKeyCode = mapAndroidKeyCodeToJfx(event.getKeyCode());
                nativeDispatchKeyEvent(PRESS, jfxKeyCode.impl_getCode(),
                        jfxKeyCode.impl_getChar().toCharArray(),
                        jfxKeyCode.impl_getChar().toCharArray().length, jfxModifiers);
            }
            case KeyEvent.ACTION_UP -> {
                KeyCode jfxKeyCode = mapAndroidKeyCodeToJfx(event.getKeyCode());
                nativeDispatchKeyEvent(RELEASE, jfxKeyCode.impl_getCode(),
                        jfxKeyCode.impl_getChar().toCharArray(),
                        jfxKeyCode.impl_getChar().toCharArray().length, jfxModifiers);
                int unicodeChar = event.getUnicodeChar();
                if ((unicodeChar & KeyCharacterMap.COMBINING_ACCENT) != 0) {
                    deadKey = unicodeChar & KeyCharacterMap.COMBINING_ACCENT_MASK;
                    return;
                }
                if (deadKey != 0 && unicodeChar != 0) {
                    unicodeChar = KeyCharacterMap.getDeadChar(deadKey, unicodeChar);
                    deadKey = 0;
                }
                if (unicodeChar != 0) {
                    nativeDispatchKeyEvent(TYPED, KeyCode.UNDEFINED.impl_getCode(),
                            Character.toChars(unicodeChar), 1, jfxModifiers);
                }
            }
            case KeyEvent.ACTION_MULTIPLE -> {
                if (event.getKeyCode() == KeyEvent.KEYCODE_UNKNOWN) {
                    nativeDispatchKeyEvent(TYPED, KeyCode.UNDEFINED.impl_getCode(),
                            event.getCharacters().toCharArray(),
                            event.getCharacters().toCharArray().length, jfxModifiers);
                } else {
                    KeyCode jfxKeyCode = mapAndroidKeyCodeToJfx(event.getKeyCode());
                    for (int i = 0; i < event.getRepeatCount(); i++) {
                        nativeDispatchKeyEvent(PRESS, jfxKeyCode.impl_getCode(), null, 0, jfxModifiers);
                        nativeDispatchKeyEvent(RELEASE, jfxKeyCode.impl_getCode(), null, 0, jfxModifiers);
                        nativeDispatchKeyEvent(TYPED, jfxKeyCode.impl_getCode(), null, 0, jfxModifiers);
                    }
                }
            }
            default -> System.err.println("DalvikInput.onKeyEvent Unknown Action " + event.getAction());
        }
    }

    private static int mapAndroidModifierToJfx(int androidMetaStates) {
        int jfxModifiers = 0;
        if ((androidMetaStates & KeyEvent.META_SHIFT_MASK) != 0) jfxModifiers += MODIFIER_SHIFT;
        if ((androidMetaStates & KeyEvent.META_CTRL_MASK) != 0) jfxModifiers += MODIFIER_CONTROL;
        if ((androidMetaStates & KeyEvent.META_ALT_MASK) != 0) jfxModifiers += MODIFIER_ALT;
        if ((androidMetaStates & KeyEvent.META_META_ON) != 0) jfxModifiers += MODIFIER_WINDOWS;
        return jfxModifiers;
    }

    private static KeyCode mapAndroidKeyCodeToJfx(int keycode) {
        switch (keycode) {
            case KeyEvent.KEYCODE_UNKNOWN: return KeyCode.UNDEFINED;
            case KeyEvent.KEYCODE_HOME: return KeyCode.HOME;
            case KeyEvent.KEYCODE_BACK: return KeyCode.ESCAPE;
            case KeyEvent.KEYCODE_0: return KeyCode.DIGIT0;
            case KeyEvent.KEYCODE_1: return KeyCode.DIGIT1;
            case KeyEvent.KEYCODE_2: return KeyCode.DIGIT2;
            case KeyEvent.KEYCODE_3: return KeyCode.DIGIT3;
            case KeyEvent.KEYCODE_4: return KeyCode.DIGIT4;
            case KeyEvent.KEYCODE_5: return KeyCode.DIGIT5;
            case KeyEvent.KEYCODE_6: return KeyCode.DIGIT6;
            case KeyEvent.KEYCODE_7: return KeyCode.DIGIT7;
            case KeyEvent.KEYCODE_8: return KeyCode.DIGIT8;
            case KeyEvent.KEYCODE_9: return KeyCode.DIGIT9;
            case KeyEvent.KEYCODE_STAR: return KeyCode.STAR;
            case KeyEvent.KEYCODE_POUND: return KeyCode.POUND;
            case KeyEvent.KEYCODE_DPAD_UP: return KeyCode.UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: return KeyCode.DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT: return KeyCode.LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return KeyCode.RIGHT;
            case KeyEvent.KEYCODE_VOLUME_UP: return KeyCode.VOLUME_UP;
            case KeyEvent.KEYCODE_VOLUME_DOWN: return KeyCode.VOLUME_DOWN;
            case KeyEvent.KEYCODE_POWER: return KeyCode.POWER;
            case KeyEvent.KEYCODE_CLEAR: return KeyCode.CLEAR;
            case KeyEvent.KEYCODE_A: return KeyCode.A;
            case KeyEvent.KEYCODE_B: return KeyCode.B;
            case KeyEvent.KEYCODE_C: return KeyCode.C;
            case KeyEvent.KEYCODE_D: return KeyCode.D;
            case KeyEvent.KEYCODE_E: return KeyCode.E;
            case KeyEvent.KEYCODE_F: return KeyCode.F;
            case KeyEvent.KEYCODE_G: return KeyCode.G;
            case KeyEvent.KEYCODE_H: return KeyCode.H;
            case KeyEvent.KEYCODE_I: return KeyCode.I;
            case KeyEvent.KEYCODE_J: return KeyCode.J;
            case KeyEvent.KEYCODE_K: return KeyCode.K;
            case KeyEvent.KEYCODE_L: return KeyCode.L;
            case KeyEvent.KEYCODE_M: return KeyCode.M;
            case KeyEvent.KEYCODE_N: return KeyCode.N;
            case KeyEvent.KEYCODE_O: return KeyCode.O;
            case KeyEvent.KEYCODE_P: return KeyCode.P;
            case KeyEvent.KEYCODE_Q: return KeyCode.Q;
            case KeyEvent.KEYCODE_R: return KeyCode.R;
            case KeyEvent.KEYCODE_S: return KeyCode.S;
            case KeyEvent.KEYCODE_T: return KeyCode.T;
            case KeyEvent.KEYCODE_U: return KeyCode.U;
            case KeyEvent.KEYCODE_V: return KeyCode.V;
            case KeyEvent.KEYCODE_W: return KeyCode.W;
            case KeyEvent.KEYCODE_X: return KeyCode.X;
            case KeyEvent.KEYCODE_Y: return KeyCode.Y;
            case KeyEvent.KEYCODE_Z: return KeyCode.Z;
            case KeyEvent.KEYCODE_COMMA: return KeyCode.COMMA;
            case KeyEvent.KEYCODE_PERIOD: return KeyCode.PERIOD;
            case KeyEvent.KEYCODE_ALT_LEFT: return KeyCode.ALT;
            case KeyEvent.KEYCODE_ALT_RIGHT: return KeyCode.ALT;
            case KeyEvent.KEYCODE_SHIFT_LEFT: return KeyCode.SHIFT;
            case KeyEvent.KEYCODE_SHIFT_RIGHT: return KeyCode.SHIFT;
            case KeyEvent.KEYCODE_TAB: return KeyCode.TAB;
            case KeyEvent.KEYCODE_SPACE: return KeyCode.SPACE;
            case KeyEvent.KEYCODE_ENTER: return KeyCode.ENTER;
            case KeyEvent.KEYCODE_DEL: return KeyCode.BACK_SPACE;
            case KeyEvent.KEYCODE_GRAVE: return KeyCode.DEAD_GRAVE;
            case KeyEvent.KEYCODE_MINUS: return KeyCode.MINUS;
            case KeyEvent.KEYCODE_EQUALS: return KeyCode.EQUALS;
            case KeyEvent.KEYCODE_LEFT_BRACKET: return KeyCode.BRACELEFT;
            case KeyEvent.KEYCODE_RIGHT_BRACKET: return KeyCode.BRACERIGHT;
            case KeyEvent.KEYCODE_BACKSLASH: return KeyCode.BACK_SLASH;
            case KeyEvent.KEYCODE_SEMICOLON: return KeyCode.SEMICOLON;
            case KeyEvent.KEYCODE_SLASH: return KeyCode.SLASH;
            case KeyEvent.KEYCODE_AT: return KeyCode.AT;
            case KeyEvent.KEYCODE_PLUS: return KeyCode.PLUS;
            case KeyEvent.KEYCODE_MENU: return KeyCode.CONTEXT_MENU;
            case KeyEvent.KEYCODE_SEARCH: return KeyCode.FIND;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: return KeyCode.PLAY;
            case KeyEvent.KEYCODE_MEDIA_STOP: return KeyCode.STOP;
            case KeyEvent.KEYCODE_MEDIA_NEXT: return KeyCode.TRACK_NEXT;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS: return KeyCode.TRACK_PREV;
            case KeyEvent.KEYCODE_MEDIA_REWIND: return KeyCode.REWIND;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD: return KeyCode.FAST_FWD;
            case KeyEvent.KEYCODE_MUTE: return KeyCode.MUTE;
            case KeyEvent.KEYCODE_PAGE_UP: return KeyCode.PAGE_UP;
            case KeyEvent.KEYCODE_PAGE_DOWN: return KeyCode.PAGE_DOWN;
            case KeyEvent.KEYCODE_BUTTON_A: return KeyCode.GAME_A;
            case KeyEvent.KEYCODE_BUTTON_B: return KeyCode.GAME_B;
            case KeyEvent.KEYCODE_BUTTON_C: return KeyCode.GAME_C;
            case KeyEvent.KEYCODE_BUTTON_X: return KeyCode.GAME_D;
            case KeyEvent.KEYCODE_BUTTON_MODE: return KeyCode.MODECHANGE;
            case KeyEvent.KEYCODE_ESCAPE: return KeyCode.ESCAPE;
            case KeyEvent.KEYCODE_CTRL_LEFT: return KeyCode.CONTROL;
            case KeyEvent.KEYCODE_CTRL_RIGHT: return KeyCode.CONTROL;
            case KeyEvent.KEYCODE_CAPS_LOCK: return KeyCode.CAPS;
            case KeyEvent.KEYCODE_SCROLL_LOCK: return KeyCode.SCROLL_LOCK;
            case KeyEvent.KEYCODE_META_LEFT: return KeyCode.META;
            case KeyEvent.KEYCODE_META_RIGHT: return KeyCode.META;
            case KeyEvent.KEYCODE_SYSRQ: return KeyCode.PRINTSCREEN;
            case KeyEvent.KEYCODE_BREAK: return KeyCode.PAUSE;
            case KeyEvent.KEYCODE_MOVE_HOME: return KeyCode.BEGIN;
            case KeyEvent.KEYCODE_MOVE_END: return KeyCode.END;
            case KeyEvent.KEYCODE_INSERT: return KeyCode.INSERT;
            case KeyEvent.KEYCODE_MEDIA_PLAY: return KeyCode.PLAY;
            case KeyEvent.KEYCODE_MEDIA_EJECT: return KeyCode.EJECT_TOGGLE;
            case KeyEvent.KEYCODE_MEDIA_RECORD: return KeyCode.RECORD;
            case KeyEvent.KEYCODE_F1: return KeyCode.F1;
            case KeyEvent.KEYCODE_F2: return KeyCode.F2;
            case KeyEvent.KEYCODE_F3: return KeyCode.F3;
            case KeyEvent.KEYCODE_F4: return KeyCode.F4;
            case KeyEvent.KEYCODE_F5: return KeyCode.F5;
            case KeyEvent.KEYCODE_F6: return KeyCode.F6;
            case KeyEvent.KEYCODE_F7: return KeyCode.F7;
            case KeyEvent.KEYCODE_F8: return KeyCode.F8;
            case KeyEvent.KEYCODE_F9: return KeyCode.F9;
            case KeyEvent.KEYCODE_F10: return KeyCode.F10;
            case KeyEvent.KEYCODE_F11: return KeyCode.F11;
            case KeyEvent.KEYCODE_F12: return KeyCode.F12;
            case KeyEvent.KEYCODE_NUM_LOCK: return KeyCode.NUM_LOCK;
            case KeyEvent.KEYCODE_NUMPAD_0: return KeyCode.NUMPAD0;
            case KeyEvent.KEYCODE_NUMPAD_1: return KeyCode.NUMPAD1;
            case KeyEvent.KEYCODE_NUMPAD_2: return KeyCode.NUMPAD2;
            case KeyEvent.KEYCODE_NUMPAD_3: return KeyCode.NUMPAD3;
            case KeyEvent.KEYCODE_NUMPAD_4: return KeyCode.NUMPAD4;
            case KeyEvent.KEYCODE_NUMPAD_5: return KeyCode.NUMPAD5;
            case KeyEvent.KEYCODE_NUMPAD_6: return KeyCode.NUMPAD6;
            case KeyEvent.KEYCODE_NUMPAD_7: return KeyCode.NUMPAD7;
            case KeyEvent.KEYCODE_NUMPAD_8: return KeyCode.NUMPAD8;
            case KeyEvent.KEYCODE_NUMPAD_9: return KeyCode.NUMPAD9;
            case KeyEvent.KEYCODE_NUMPAD_DIVIDE: return KeyCode.DIVIDE;
            case KeyEvent.KEYCODE_NUMPAD_MULTIPLY: return KeyCode.MULTIPLY;
            case KeyEvent.KEYCODE_NUMPAD_SUBTRACT: return KeyCode.SUBTRACT;
            case KeyEvent.KEYCODE_NUMPAD_ADD: return KeyCode.ADD;
            case KeyEvent.KEYCODE_NUMPAD_DOT: return KeyCode.PERIOD;
            case KeyEvent.KEYCODE_NUMPAD_COMMA: return KeyCode.COMMA;
            case KeyEvent.KEYCODE_NUMPAD_ENTER: return KeyCode.ENTER;
            case KeyEvent.KEYCODE_NUMPAD_EQUALS: return KeyCode.EQUALS;
            case KeyEvent.KEYCODE_NUMPAD_LEFT_PAREN: return KeyCode.LEFT_PARENTHESIS;
            case KeyEvent.KEYCODE_NUMPAD_RIGHT_PAREN: return KeyCode.RIGHT_PARENTHESIS;
            case KeyEvent.KEYCODE_VOLUME_MUTE: return KeyCode.MUTE;
            case KeyEvent.KEYCODE_INFO: return KeyCode.INFO;
            case KeyEvent.KEYCODE_CHANNEL_UP: return KeyCode.CHANNEL_UP;
            case KeyEvent.KEYCODE_CHANNEL_DOWN: return KeyCode.CHANNEL_DOWN;
            case KeyEvent.KEYCODE_PROG_RED: return KeyCode.COLORED_KEY_0;
            case KeyEvent.KEYCODE_PROG_GREEN: return KeyCode.COLORED_KEY_1;
            case KeyEvent.KEYCODE_PROG_YELLOW: return KeyCode.COLORED_KEY_2;
            case KeyEvent.KEYCODE_PROG_BLUE: return KeyCode.COLORED_KEY_3;
            case KeyEvent.KEYCODE_KATAKANA_HIRAGANA: return KeyCode.JAPANESE_HIRAGANA;
            case KeyEvent.KEYCODE_KANA: return KeyCode.KANA;
            default: return KeyCode.UNDEFINED;
        }
    }
}
