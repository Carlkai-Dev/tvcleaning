package com.gathin.tvhome;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Centers the icon and label as one group inside equally sized header pills. */
public final class TopButton extends LinearLayout {
    private final TextView label;

    public TopButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);
        setBaselineAligned(false);
        ColorStateList colors = context.getColorStateList(R.drawable.pill_text);
        TypedArray attributes = context.obtainStyledAttributes(attrs,
                new int[]{android.R.attr.text, android.R.attr.drawableStart});
        ImageView icon = new ImageView(context);
        icon.setImageDrawable(attributes.getDrawable(1));
        icon.setImageTintList(colors);
        icon.setDuplicateParentStateEnabled(true);
        icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(icon, new LayoutParams((int) (22 * density), (int) (22 * density)));
        label = new TextView(context);
        label.setText(attributes.getText(0));
        attributes.recycle();
        label.setTypeface(getResources().getFont(R.font.outfit));
        label.setFontVariationSettings("'wght' 500");
        label.setTextSize(15);
        label.setTextColor(colors);
        label.setIncludeFontPadding(false);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setMaxWidth((int) (58 * density));
        label.setDuplicateParentStateEnabled(true);
        label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        LayoutParams labelLayout = new LayoutParams(-2, -2);
        labelLayout.leftMargin = (int) (8 * density);
        addView(label, labelLayout);
        setContentDescription(label.getText());
    }

    public void setText(CharSequence text) {
        label.setText(text);
        setContentDescription(text);
    }
}
