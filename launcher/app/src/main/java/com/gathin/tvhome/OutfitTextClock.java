package com.gathin.tvhome;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextClock;

/** A TextClock set in Outfit (assets copy; res/font needs API 26 - see Outfit). */
public class OutfitTextClock extends TextClock {

    public OutfitTextClock(Context context) {
        super(context);
        init(context);
    }

    public OutfitTextClock(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public OutfitTextClock(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setTypeface(Outfit.get(context));
    }
}
