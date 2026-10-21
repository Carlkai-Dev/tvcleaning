package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Color;
import android.media.tv.TvContract;
import android.media.tv.TvView;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

/**
 * Plays a passthrough input (HDMI) in a TvView, for TVs where no app outside the firmware may start the input
 * player: on Fire OS, Amazon's PassthroughPlayerActivity needs com.amazon.tv.permission.TUNE_INPUT. A TvView
 * session goes through the TV input framework directly. Back leaves the input; the session is released in
 * onStop, so Home or another app frees the port too.
 */
public class HdmiPlayerActivity extends Activity {
    static final String EXTRA_INPUT_ID = "input_id";
    private static final String TAG = "HdmiPlayer";

    private TvView tvView;
    private String inputId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        inputId = getIntent().getStringExtra(EXTRA_INPUT_ID);
        if (inputId == null) {
            finish();
            return;
        }
        tvView = new TvView(this);
        tvView.setBackgroundColor(Color.BLACK);
        tvView.setCallback(new TvView.TvInputCallback() {
            @Override
            public void onConnectionFailed(String id) {
                Log.w(TAG, "connection failed: " + id);
                Toast.makeText(HdmiPlayerActivity.this, R.string.not_available, Toast.LENGTH_LONG).show();
                finish();
            }

            @Override
            public void onDisconnected(String id) {
                Log.w(TAG, "disconnected: " + id);
                finish();
            }

            @Override
            public void onVideoAvailable(String id) {
                Log.i(TAG, "video available: " + id);
            }

            @Override
            public void onVideoUnavailable(String id, int reason) {
                Log.i(TAG, "video unavailable: " + id + " reason " + reason);
            }
        });
        setContentView(tvView);
        tvView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (tvView == null) return;
        // Full stream volume, as the firmware's own player sets it; the TV's volume and mute still apply on top.
        tvView.setStreamVolume(1f);
        tvView.tune(inputId, TvContract.buildChannelUriForPassthroughInput(inputId));
    }

    @Override
    protected void onStop() {
        // Release the session so the port, its audio and the CEC active source go back to the system.
        if (tvView != null) tvView.reset();
        super.onStop();
    }
}
