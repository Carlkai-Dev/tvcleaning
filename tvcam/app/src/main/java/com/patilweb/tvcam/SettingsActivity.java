package com.patilweb.tvcam;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;

public class SettingsActivity extends Activity implements CameraMonitor.Listener {
    private static final int REQ_STORAGE = 71;

    private ArrayList<ClipStore.Location> locations;
    private TextView storagePathLabel;
    private TextView pipCamStatus;
    private CheckBox chkPip;
    private Spinner spinStorage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        spinStorage = findViewById(R.id.spinStorage);
        storagePathLabel = findViewById(R.id.storagePathLabel);
        Spinner spinSegment = findViewById(R.id.spinSegment);
        Spinner spinRetain = findViewById(R.id.spinRetain);
        Spinner spinMotion = findViewById(R.id.spinMotion);
        CheckBox chkAudio = findViewById(R.id.chkAudio);
        CheckBox chkOverlay = findViewById(R.id.chkOverlay);
        chkPip = findViewById(R.id.chkPip);
        pipCamStatus = findViewById(R.id.pipCamStatus);
        CheckBox chkBoot = findViewById(R.id.chkBoot);
        Button back = findViewById(R.id.btnBack);
        Button btnPipAdvanced = findViewById(R.id.btnPipAdvanced);

        bindStorageSpinner();

        String[] segments = new String[Prefs.SEGMENT_MINUTES.length];
        for (int i = 0; i < segments.length; i++) {
            segments[i] = Prefs.SEGMENT_MINUTES[i] + " minutes";
        }
        spinSegment.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, segments));
        spinSegment.setSelection(indexOf(Prefs.SEGMENT_MINUTES, Prefs.segmentMinutes(this), 1));

        String[] retains = new String[Prefs.RETAIN_DAYS.length];
        for (int i = 0; i < retains.length; i++) {
            retains[i] = Prefs.RETAIN_DAYS[i] + " days";
        }
        spinRetain.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, retains));
        spinRetain.setSelection(indexOf(Prefs.RETAIN_DAYS, Prefs.retainDays(this), 2));

        spinMotion.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, Prefs.MOTION_LABELS));
        spinMotion.setSelection(Prefs.motionLevel(this));

        chkAudio.setChecked(Prefs.recordAudio(this));
        chkOverlay.setChecked(Prefs.timestampOverlay(this));
        chkPip.setChecked(Prefs.pipOverview(this));
        chkBoot.setChecked(Prefs.startOnBoot(this));
        Button btnOverlayPerm = findViewById(R.id.btnOverlayPerm);
        Spinner spinPipSize = findViewById(R.id.spinPipSize);
        bindPipPositionGrid();
        refreshOverlayButton(btnOverlayPerm);

        spinPipSize.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, Prefs.PIP_SIZE_LABELS));
        spinPipSize.setSelection(Prefs.pipSizeIndex(this));
        spinPipSize.setOnItemSelectedListener(simple((pos) -> {
            Prefs.setPipSizeIndex(this, pos);
            PipOverlay.refreshVisibility();
            FloatingPipService.refreshLayout();
            Toast.makeText(this, "PiP size · " + Prefs.PIP_SIZE_LABELS[pos],
                    Toast.LENGTH_SHORT).show();
        }));

        spinSegment.setOnItemSelectedListener(simple((pos) ->
                Prefs.setSegmentMinutes(this, Prefs.SEGMENT_MINUTES[pos])));
        spinRetain.setOnItemSelectedListener(simple((pos) ->
                Prefs.setRetainDays(this, Prefs.RETAIN_DAYS[pos])));
        spinMotion.setOnItemSelectedListener(simple((pos) ->
                Prefs.setMotionLevel(this, pos)));
        chkAudio.setOnCheckedChangeListener((b, on) -> Prefs.setRecordAudio(this, on));
        chkOverlay.setOnCheckedChangeListener((b, on) -> Prefs.setTimestampOverlay(this, on));
        chkPip.setOnCheckedChangeListener((b, on) -> {
            boolean cam = CameraMonitor.get() != null && CameraMonitor.get().isConnected();
            if (on && !cam) {
                chkPip.setChecked(false);
                Prefs.setPipOverview(this, false);
                Toast.makeText(this, R.string.pip_needs_camera, Toast.LENGTH_LONG).show();
                refreshPipStatus();
                return;
            }
            Prefs.setPipOverview(this, on);
            PipOverlay.refreshVisibility();
            if (on && !OverlayPermission.isGranted(this)) {
                Toast.makeText(this, R.string.pip_need_overlay, Toast.LENGTH_LONG).show();
                OverlayPermission.openSettings(this);
            }
            FloatingPipService.sync(this);
            CamHub.get().maybeStartService();
            refreshPipStatus();
        });
        btnOverlayPerm.setOnClickListener(v -> OverlayPermission.openSettings(this));
        btnPipAdvanced.setOnClickListener(v ->
                startActivity(new Intent(this, PipPositionActivity.class)));
        chkBoot.setOnCheckedChangeListener((b, on) -> Prefs.setStartOnBoot(this, on));
        back.setOnClickListener(v -> finish());
        back.requestFocus();
        refreshPipStatus();

        // Auto-jump if PiP is already on but overlay was never allowed.
        if (Prefs.pipActive(this) && !OverlayPermission.isGranted(this)) {
            OverlayPermission.openSettings(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (CameraMonitor.get() != null) {
            CameraMonitor.get().addListener(this);
            CameraMonitor.get().refresh();
        }
        refreshOverlayButton(findViewById(R.id.btnOverlayPerm));
        refreshPipStatus();
        // Re-sync label after returning from advanced picker.
        TextView pipPosLabel = findViewById(R.id.pipPosLabel);
        if (pipPosLabel != null) {
            int cur = Prefs.pipPosition(this);
            pipPosLabel.setText(Prefs.PIP_POSITION_LABELS[cur]
                    + String.format(" · %.0f%% , %.0f%%",
                    Prefs.pipXPct(this), Prefs.pipYPct(this)));
        }
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
        refreshPipStatus();
        PipOverlay.refreshVisibility();
        FloatingPipService.sync(this);
    }

    private void refreshPipStatus() {
        if (pipCamStatus == null || chkPip == null) return;
        boolean cam = CameraMonitor.get() != null && CameraMonitor.get().isConnected();
        boolean want = Prefs.pipOverview(this);
        boolean active = Prefs.pipActive(this);
        chkPip.setEnabled(cam);
        if (!cam && want) {
            // Keep preference, but UI shows PiP cannot run without camera.
        }
        String camTxt = cam ? getString(R.string.cam_connected) : getString(R.string.cam_disconnected);
        String pipTxt = active
                ? getString(R.string.pip_state_on)
                : (want ? getString(R.string.pip_state_off_no_cam) : getString(R.string.pip_state_off));
        pipCamStatus.setText(camTxt + "  ·  " + pipTxt);
        pipCamStatus.setTextColor(getColor(active ? R.color.accent_soft : R.color.text_secondary));
    }

    private void refreshOverlayButton(Button btn) {
        if (btn == null) return;
        boolean ok = OverlayPermission.isGranted(this);
        btn.setText(ok
                ? "Overlay permission: ON · tap to reopen"
                : "OPEN → Display over other apps");
        btn.setSelected(ok);
    }

    private void bindPipPositionGrid() {
        TextView pipPosLabel = findViewById(R.id.pipPosLabel);
        int[] ids = {
                R.id.pipPos0, R.id.pipPos1, R.id.pipPos2,
                R.id.pipPos3, R.id.pipPos4, R.id.pipPos5,
                R.id.pipPos6, R.id.pipPos7, R.id.pipPos8
        };
        Button[] buttons = new Button[9];
        for (int i = 0; i < 9; i++) {
            buttons[i] = findViewById(ids[i]);
            final int pos = i;
            buttons[i].setOnClickListener(v -> {
                Prefs.setPipPosition(this, pos);
                highlightPipButtons(buttons, pos);
                pipPosLabel.setText(Prefs.PIP_POSITION_LABELS[pos]);
                PipOverlay.refreshVisibility();
                FloatingPipService.refreshLayout();
                Toast.makeText(this, "PiP → " + Prefs.PIP_POSITION_LABELS[pos],
                        Toast.LENGTH_SHORT).show();
            });
        }
        int cur = Prefs.pipPosition(this);
        highlightPipButtons(buttons, cur);
        pipPosLabel.setText(Prefs.PIP_POSITION_LABELS[cur]);
    }

    private void highlightPipButtons(Button[] buttons, int selected) {
        for (int i = 0; i < buttons.length; i++) {
            buttons[i].setSelected(i == selected);
        }
    }

    private void bindStorageSpinner() {
        locations = ClipStore.availableLocations(this);
        String[] labels = new String[locations.size()];
        int selected = 0;
        String currentId = Prefs.storageId(this);
        for (int i = 0; i < locations.size(); i++) {
            labels[i] = locations.get(i).label;
            if (locations.get(i).id.equals(currentId)) selected = i;
        }
        spinStorage.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, labels));
        spinStorage.setSelection(selected);
        updatePathLabel(locations.get(selected));

        spinStorage.setOnItemSelectedListener(simple((pos) -> {
            ClipStore.Location loc = locations.get(pos);
            if (needsStoragePermission(loc) && !hasStoragePermission()) {
                requestStoragePermission(pos);
                return;
            }
            applyStorage(loc);
        }));
    }

    private void applyStorage(ClipStore.Location loc) {
        Prefs.setStorageId(this, loc.id);
        // Ensure folder exists after selection.
        //noinspection ResultOfMethodCallIgnored
        loc.root.mkdirs();
        updatePathLabel(loc);
        Toast.makeText(this, "Saving to " + loc.root.getAbsolutePath(), Toast.LENGTH_SHORT).show();
    }

    private void updatePathLabel(ClipStore.Location loc) {
        storagePathLabel.setText(getString(R.string.settings_storage_path, loc.root.getAbsolutePath()));
    }

    private boolean needsStoragePermission(ClipStore.Location loc) {
        return ClipStore.ID_PUBLIC.equals(loc.id)
                || loc.id.startsWith(ClipStore.ID_REMOVABLE_PREFIX);
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= 33) return true;
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private int pendingStoragePos = -1;

    private void requestStoragePermission(int pos) {
        pendingStoragePos = pos;
        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                },
                REQ_STORAGE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_STORAGE || pendingStoragePos < 0) return;
        int pos = pendingStoragePos;
        pendingStoragePos = -1;
        if (hasStoragePermission() && pos < locations.size()) {
            applyStorage(locations.get(pos));
            spinStorage.setSelection(pos);
        } else {
            Toast.makeText(this, "Storage permission needed for that location",
                    Toast.LENGTH_LONG).show();
            // Revert spinner to current saved choice.
            for (int i = 0; i < locations.size(); i++) {
                if (locations.get(i).id.equals(Prefs.storageId(this))) {
                    spinStorage.setSelection(i);
                    updatePathLabel(locations.get(i));
                    break;
                }
            }
        }
    }

    private static int indexOf(int[] arr, int value, int fallback) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == value) return i;
        }
        return fallback;
    }

    private interface PosConsumer {
        void accept(int pos);
    }

    private static android.widget.AdapterView.OnItemSelectedListener simple(PosConsumer c) {
        return new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view,
                                       int position, long id) {
                c.accept(position);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        };
    }
}
