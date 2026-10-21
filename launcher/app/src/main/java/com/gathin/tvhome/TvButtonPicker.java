package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.util.List;

/** A bounded TV list: one focus target per row, mirrored by the UI locale. */
final class TvButtonPicker {
    interface Selection { void choose(int position); }
    static void show(Activity activity, List<String> labels, int selected, Selection selection) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        View content = activity.getLayoutInflater().inflate(R.layout.dialog_tv_button_picker, null);
        // The launcher deliberately does not opt its existing layouts into automatic mirroring.
        // Mirror this isolated picker explicitly without changing the rest of the app.
        boolean rtl = android.text.TextUtils.getLayoutDirectionFromLocale(
                activity.getResources().getConfiguration().getLocales().get(0)) == View.LAYOUT_DIRECTION_RTL;
        ((TextView) content.findViewById(R.id.tvButtonPickerTitle)).setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
        LinearLayout.LayoutParams cancelParams = (LinearLayout.LayoutParams)
                content.findViewById(R.id.tvButtonPickerCancel).getLayoutParams();
        cancelParams.gravity = rtl ? Gravity.LEFT : Gravity.RIGHT;
        content.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        dialog.setContentView(content);
        ListView list = content.findViewById(R.id.tvButtonPickerList);
        list.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return labels.size(); }
            @Override public Object getItem(int position) { return labels.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position, View recycled, android.view.ViewGroup parent) {
                View row = recycled == null ? activity.getLayoutInflater().inflate(R.layout.row_tv_button_picker, parent, false) : recycled;
                LinearLayout line = (LinearLayout) row;
                View radio = row.findViewById(R.id.tvButtonPickerSelected);
                line.removeView(radio);
                line.addView(radio, rtl ? 1 : 0);
                TextView label = row.findViewById(R.id.tvButtonPickerLabel);
                label.setGravity((rtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
                label.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
                label.setText(labels.get(position));
                ((RadioButton) row.findViewById(R.id.tvButtonPickerSelected)).setChecked(position == selected);
                return row;
            }
        });
        list.setOnItemClickListener((parent, view, position, id) -> {
            selection.choose(position);
            dialog.dismiss();
        });
        content.findViewById(R.id.tvButtonPickerCancel).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.rgb(24, 28, 33)));
            android.util.DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
            window.setLayout((int)(metrics.widthPixels * .76f), (int)(metrics.heightPixels * .8f));
            window.setGravity(Gravity.CENTER);
        }
        list.requestFocus();
        list.setSelection(Math.max(0, selected));
    }
}
