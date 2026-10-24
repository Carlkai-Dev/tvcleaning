package com.patilweb.tvcam;

import android.app.Activity;
import android.app.AlertDialog;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import android.widget.VideoView;

import java.io.File;
import java.io.FileInputStream;

public class PlayerActivity extends Activity {
    public static final String EXTRA_PATH = "path";

    private VideoView videoView;
    private File file;
    private Button playPause;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        String path = getIntent().getStringExtra(EXTRA_PATH);
        if (path == null) {
            finish();
            return;
        }
        file = new File(path);
        videoView = findViewById(R.id.videoView);
        playPause = findViewById(R.id.btnPlayPause);
        Button delete = findViewById(R.id.btnDelete);
        Button back = findViewById(R.id.btnBack);

        if (!file.exists()) {
            Toast.makeText(this, "Clip missing", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (!ClipStore.isPlayableMp4(file)) {
            Toast.makeText(this, "Clip is incomplete (not finalized). Delete and re-record.",
                    Toast.LENGTH_LONG).show();
        }

        videoView.setOnPreparedListener(mp -> {
            mp.setLooping(false);
            videoView.start();
            playPause.setText("Pause");
            playPause.requestFocus();
        });
        videoView.setOnCompletionListener(mp -> playPause.setText(R.string.play));
        videoView.setOnErrorListener((mp, what, extra) -> {
            Toast.makeText(this,
                    "Playback error (" + what + "/" + extra + "). Clip may be corrupt.",
                    Toast.LENGTH_LONG).show();
            return true;
        });

        playPause.setOnClickListener(v -> {
            if (videoView.isPlaying()) {
                videoView.pause();
                playPause.setText(R.string.play);
            } else {
                videoView.start();
                playPause.setText("Pause");
            }
        });
        delete.setOnClickListener(v -> confirmDelete());
        back.setOnClickListener(v -> finish());

        startPlayback();
    }

    private void startPlayback() {
        // Prefer path API; fall back to FileDescriptor for some TV MediaPlayers.
        try {
            videoView.setVideoPath(file.getAbsolutePath());
            videoView.requestFocus();
            videoView.start();
        } catch (Exception e) {
            try (FileInputStream fis = new FileInputStream(file)) {
                MediaPlayer mp = new MediaPlayer();
                mp.setDataSource(fis.getFD());
                mp.setDisplay(videoView.getHolder());
                mp.setOnPreparedListener(MediaPlayer::start);
                mp.setOnErrorListener((m, w, ex) -> {
                    Toast.makeText(this, "Cannot play this clip", Toast.LENGTH_LONG).show();
                    return true;
                });
                mp.prepareAsync();
            } catch (Exception e2) {
                Toast.makeText(this, "Cannot open clip: " + e2.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete)
                .setMessage(file.getName())
                .setPositiveButton(R.string.delete, (d, w) -> {
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                    Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    protected void onStop() {
        videoView.stopPlayback();
        super.onStop();
    }
}
