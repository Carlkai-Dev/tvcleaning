package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ReplacementSpan;

/** Optical correction for Outfit's round O/O pair, without changing the text or other tracking. */
final class OpticalGreeting {
    static CharSequence format(String text) {
        SpannableString result = new SpannableString(text);
        for (int i = 0; i + 1 < text.length(); i++) {
            if (Character.toUpperCase(text.charAt(i)) == 'O'
                    && Character.toUpperCase(text.charAt(i + 1)) == 'O') {
                result.setSpan(new RoundPair(), i, i + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return result;
    }

    private static final class RoundPair extends ReplacementSpan {
        @Override public int getSize(Paint paint, CharSequence text, int start, int end,
                                     Paint.FontMetricsInt metrics) {
            if (metrics != null) paint.getFontMetricsInt(metrics);
            return Math.max(1, Math.round(paint.measureText(text, start, end) - paint.getTextSize() * 0.045f));
        }

        @Override public void draw(Canvas canvas, CharSequence text, int start, int end, float x,
                                   int top, int baseline, int bottom, Paint paint) {
            canvas.drawText(text, start, end, x, baseline, paint);
        }
    }
}
