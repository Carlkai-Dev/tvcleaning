package com.gathin.tvhome;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

/** A TextView set in Outfit (assets copy; res/font needs API 26 - see Outfit). */
public class OutfitTextView extends TextView {

    public OutfitTextView(Context context) {
        super(context);
        init(context);
    }

    public OutfitTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public OutfitTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setTypeface(Outfit.get(context));
    }
}
