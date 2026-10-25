package com.patilweb.tvcam;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Advanced PiP placement: TV-screen mock + movable dummy preview.
 * D-pad / arrow buttons nudge position; 3×3 grid snaps to corners/edges.
 */
public class PipPositionActivity extends Activity implements CameraMonitor.Listener {
    private static final float STEP = 2.5f;

    private FrameLayout tvScreen;
    private View pipDummy;
    private TextView statusLabel;
    private TextView coordLabel;
    private CheckBox chkPipOn;
    private Button[] gridButtons;
    private TextView pipPosLabel;

    private float xPct;
    private float yPct;
    private int sizeIndex;
    private boolean dirty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pip_position);

        tvScreen = findViewById(R.id.tvScreen);
        pipDummy = findViewById(R.id.pipDummy);
        statusLabel = findViewById(R.id.pipStatusLabel);
        coordLabel = findViewById(R.id.pipCoordLabel);
        chkPipOn = findViewById(R.id.chkPipOn);
        Spinner spinSize = findViewById(R.id.spinPipSize);
        Button btnSave = findViewById(R.id.btnSave);
        Button btnCancel = findViewById(R.id.btnCancel);

        xPct = Prefs.pipXPct(this);
        yPct = Prefs.pipYPct(this);
        sizeIndex = Prefs.pipSizeIndex(this);
        chkPipOn.setChecked(Prefs.pipOverview(this));

        spinSize.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, Prefs.PIP_SIZE_LABELS));
        spinSize.setSelection(sizeIndex);
        spinSize.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                       int position, long id) {
                sizeIndex = position;
                dirty = true;
                layoutDummy();
                refreshStatus();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        findViewById(R.id.btnMoveLeft).setOnClickListener(v -> nudge(-STEP, 0));
        findViewById(R.id.btnMoveRight).setOnClickListener(v -> nudge(STEP, 0));
        findViewById(R.id.btnMoveUp).setOnClickListener(v -> nudge(0, -STEP));
        findViewById(R.id.btnMoveDown).setOnClickListener(v -> nudge(0, STEP));

        chkPipOn.setOnCheckedChangeListener((b, on) -> {
            dirty = true;
            refreshStatus();
        });

        bindGrid();
        btnCancel.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> saveAndFinish());

        tvScreen.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        tvScreen.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        layoutDummy();
                    }
                });

        tvScreen.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    nudge(-STEP, 0);
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    nudge(STEP, 0);
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    nudge(0, -STEP);
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    nudge(0, STEP);
                    return true;
                default:
                    return false;
            }
        });

        refreshStatus();
        highlightGrid();
        findViewById(R.id.btnMoveRight).requestFocus();
    }

    private void bindGrid() {
        pipPosLabel = null;
        int[] ids = {
                R.id.pipPos0, R.id.pipPos1, R.id.pipPos2,
                R.id.pipPos3, R.id.pipPos4, R.id.pipPos5,
                R.id.pipPos6, R.id.pipPos7, R.id.pipPos8
        };
        gridButtons = new Button[9];
        for (int i = 0; i < 9; i++) {
            gridButtons[i] = findViewById(ids[i]);
            final int pos = i;
            gridButtons[i].setOnClickListener(v -> {
                float[] pct = PipLayout.pctForGrid(pos);
                xPct = pct[0];
                yPct = pct[1];
                dirty = true;
                layoutDummy();
                highlightGrid();
                refreshStatus();
                Toast.makeText(this, Prefs.PIP_POSITION_LABELS[pos], Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void nudge(float dx, float dy) {
        xPct = PipLayout.clamp(xPct + dx);
        yPct = PipLayout.clamp(yPct + dy);
        dirty = true;
        layoutDummy();
        highlightGrid();
        refreshStatus();
    }

    private void layoutDummy() {
        if (tvScreen.getWidth() <= 0 || tvScreen.getHeight() <= 0) return;
        float density = getResources().getDisplayMetrics().density;
        int sizePx = Math.max(48, Math.round(Prefs.PIP_SIZE_DP[sizeIndex] * density
                * (tvScreen.getWidth() / (float) Math.max(1, PipLayout.screenSize(this)[0]))));
        // Keep dummy readable on the mock — clamp between 48 and 40% of mock width.
        int maxDummy = Math.max(64, tvScreen.getWidth() / 3);
        sizePx = Math.max(48, Math.min(maxDummy, sizePx));

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) pipDummy.getLayoutParams();
        PipLayout.placeInParent(lp, tvScreen.getWidth(), tvScreen.getHeight(), sizePx, xPct, yPct);
        pipDummy.setLayoutParams(lp);
    }

    private void highlightGrid() {
        int nearest = PipLayout.nearestGrid(xPct, yPct);
        for (int i = 0; i < gridButtons.length; i++) {
            gridButtons[i].setSelected(i == nearest);
        }
        if (pipPosLabel != null) {
            pipPosLabel.setText(Prefs.PIP_POSITION_LABELS[nearest]
                    + String.format(" · %.0f%% , %.0f%%", xPct, yPct));
        }
    }

    private void refreshStatus() {
        boolean cam = CameraMonitor.get() != null && CameraMonitor.get().isConnected();
        boolean want = chkPipOn.isChecked();
        boolean active = cam && want;
        String camTxt = cam ? getString(R.string.cam_connected) : getString(R.string.cam_disconnected);
        String pipTxt = active
                ? getString(R.string.pip_state_on)
                : (want ? getString(R.string.pip_state_off_no_cam) : getString(R.string.pip_state_off));
        statusLabel.setText(camTxt + "  ·  " + pipTxt);
        statusLabel.setTextColor(getColor(active ? R.color.accent_soft : R.color.text_secondary));
        coordLabel.setText(String.format("X %.1f%%   Y %.1f%%   ·   %s",
                xPct, yPct, Prefs.PIP_SIZE_LABELS[sizeIndex]));
        chkPipOn.setEnabled(cam);
        if (!cam && want) {
            // Pref can stay on, but show that PiP will stay off until camera returns.
            statusLabel.append("\n" + getString(R.string.pip_waits_for_camera));
        }
    }

    private void saveAndFinish() {
        Prefs.setPipXPct(this, xPct);
        Prefs.setPipYPct(this, yPct);
        Prefs.setPipSizeIndex(this, sizeIndex);
        Prefs.setPipPosition(this, PipLayout.nearestGrid(xPct, yPct));
        Prefs.setPipOverview(this, chkPipOn.isChecked());
        PipOverlay.refreshVisibility();
        FloatingPipService.sync(this);
        FloatingPipService.refreshLayout();
        CamHub.get().maybeStartService();
        Toast.makeText(this, R.string.pip_saved, Toast.LENGTH_SHORT).show();
        dirty = false;
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (CameraMonitor.get() != null) {
            CameraMonitor.get().addListener(this);
            CameraMonitor.get().refresh();
        }
        refreshStatus();
        layoutDummy();
    }

    @Override
    protected void onPause() {
        if (CameraMonitor.get() != null) {
            CameraMonitor.get().removeListener(this);
        }
        super.onPause();
    }

    @Override
    public void onCameraAvailabilityChanged(boolean connected) {
        refreshStatus();
        PipOverlay.refreshVisibility();
        FloatingPipService.sync(this);
    }

    @Override
    public void onBackPressed() {
        if (dirty) {
            Toast.makeText(this, R.string.pip_discard_hint, Toast.LENGTH_SHORT).show();
        }
        super.onBackPressed();
    }
}
