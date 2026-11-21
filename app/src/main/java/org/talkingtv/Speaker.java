package org.talkingtv;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.view.accessibility.AccessibilityManager;

import java.util.Locale;

/** Speaks out loud with the TV's text-to-speech voice, whether or not TalkBack is on. */
public final class Speaker implements TextToSpeech.OnInitListener {
    private final TextToSpeech tts;
    private final AccessibilityManager a11y;
    private boolean ready;
    private String pending;
    private float rate = 1.0f;

    public Speaker(Context context) {
        a11y = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        tts = new TextToSpeech(context.getApplicationContext(), this);
    }

    @Override public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) return;
        tts.setLanguage(Locale.getDefault());
        tts.setSpeechRate(rate);
        ready = true;
        if (pending != null) {
            say(pending);
            pending = null;
        }
    }

    public void setRate(float rate) {
        this.rate = rate;
        if (ready) tts.setSpeechRate(rate);
    }

    /** True when TalkBack (or another spoken-feedback reader) is reading the screen itself. */
    public boolean screenReaderOn() {
        return a11y != null && a11y.isEnabled() && a11y.isTouchExplorationEnabled();
    }

    /** Interrupts whatever was being said. */
    public void say(String text) {
        if (!ready) {
            pending = text;
            return;
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "say");
    }

    /** Speaks after whatever is being said now. */
    public void then(String text) {
        if (!ready) {
            pending = pending == null ? text : pending + ". " + text;
            return;
        }
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, "then");
    }

    /** For focus moves: TalkBack already reads those, so stay quiet to avoid talking twice. */
    public void sayUnlessScreenReader(String text) {
        if (!screenReaderOn()) say(text);
    }

    public void shutdown() {
        tts.stop();
        tts.shutdown();
    }
}
