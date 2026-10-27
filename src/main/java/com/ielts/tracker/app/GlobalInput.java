package com.ielts.tracker.app;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.NativeInputEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseInputListener;
import com.github.kwhat.jnativehook.mouse.NativeMouseWheelEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseWheelListener;
import com.ielts.tracker.timer.IdleMonitor;
import com.ielts.tracker.util.ErrorLog;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * System-wide keyboard and mouse hook. It does two things only: it reports that some input happened (for the idle check;
 * it never looks at which keys) and it fires the global shortcut Ctrl+Alt+Space. If the hook cannot start, for example
 * because the operating system blocks it, the app keeps working without the shortcut and the idle check.
 */
final class GlobalInput {
    private static boolean running;

    private GlobalInput() {}

    static boolean start(IdleMonitor idle, Runnable onShortcut) {
        try {
            Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
            logger.setLevel(Level.OFF);
            logger.setUseParentHandlers(false);
            GlobalScreen.registerNativeHook();
            GlobalScreen.addNativeKeyListener(new NativeKeyListener() {
                private boolean down;

                @Override public void nativeKeyPressed(NativeKeyEvent e) {
                    idle.onInput();
                    int mods = e.getModifiers();
                    boolean combo = e.getKeyCode() == NativeKeyEvent.VC_SPACE
                            && (mods & NativeInputEvent.CTRL_MASK) != 0 && (mods & NativeInputEvent.ALT_MASK) != 0;
                    if (combo && !down) {
                        down = true;
                        onShortcut.run();
                    }
                }

                @Override public void nativeKeyReleased(NativeKeyEvent e) {
                    if (e.getKeyCode() == NativeKeyEvent.VC_SPACE) down = false;
                }
            });
            NativeMouseInputListener mouse = new NativeMouseInputListener() {
                @Override public void nativeMouseClicked(NativeMouseEvent e) { idle.onInput(); }
                @Override public void nativeMousePressed(NativeMouseEvent e) { idle.onInput(); }
                @Override public void nativeMouseReleased(NativeMouseEvent e) { idle.onInput(); }
                @Override public void nativeMouseMoved(NativeMouseEvent e) { idle.onInput(); }
                @Override public void nativeMouseDragged(NativeMouseEvent e) { idle.onInput(); }
            };
            GlobalScreen.addNativeMouseListener(mouse);
            GlobalScreen.addNativeMouseMotionListener(mouse);
            GlobalScreen.addNativeMouseWheelListener(new NativeMouseWheelListener() {
                @Override public void nativeMouseWheelMoved(NativeMouseWheelEvent e) { idle.onInput(); }
            });
            running = true;
            return true;
        } catch (NativeHookException | UnsatisfiedLinkError | RuntimeException e) {
            ErrorLog.log("Global input hook unavailable", e);
            return false;
        }
    }

    static void stop() {
        if (!running) return;
        running = false;
        try {
            GlobalScreen.unregisterNativeHook();
        } catch (NativeHookException | RuntimeException ignored) {
            // Shutting down anyway.
        }
    }
}
