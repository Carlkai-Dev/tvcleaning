package com.patilweb.tvcam;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import android.app.Activity;

import java.io.File;
import java.util.ArrayList;

public class GalleryActivity extends Activity {
    private ArrayList<File> clips;
    private TextView emptyLabel;
    private ListView listView;
    private ClipAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);
        emptyLabel = findViewById(R.id.emptyLabel);
        TextView pathLabel = findViewById(R.id.storagePathLabel);
        pathLabel.setText(getString(R.string.settings_storage_path, ClipStore.pathLabel(this)));
        listView = findViewById(R.id.clipList);
        Button back = findViewById(R.id.btnBack);
        back.setOnClickListener(v -> finish());
        adapter = new ClipAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            File f = clips.get(position);
            if (!ClipStore.isPlayableMp4(f)) {
                android.widget.Toast.makeText(this,
                        "Incomplete clip — press OK again after deleting, or re-record",
                        android.widget.Toast.LENGTH_LONG).show();
                return;
            }
            Intent i = new Intent(this, PlayerActivity.class);
            i.putExtra(PlayerActivity.EXTRA_PATH, f.getAbsolutePath());
            startActivity(i);
        });
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            File f = clips.get(position);
            //noinspection ResultOfMethodCallIgnored
            f.delete();
            reload();
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        ClipStore.deleteUnplayable(this);
        reload();
    }

    private void reload() {
        clips = ClipStore.listClips(this);
        adapter.notifyDataSetChanged();
        boolean empty = clips.isEmpty();
        emptyLabel.setVisibility(empty ? View.VISIBLE : View.GONE);
        listView.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (!empty) listView.requestFocus();
    }

    private class ClipAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return clips == null ? 0 : clips.size();
        }

        @Override
        public Object getItem(int position) {
            return clips.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = LayoutInflater.from(GalleryActivity.this)
                        .inflate(R.layout.item_clip, parent, false);
            }
            File f = clips.get(position);
            TextView name = v.findViewById(R.id.clipName);
            TextView meta = v.findViewById(R.id.clipMeta);
            boolean ok = ClipStore.isPlayableMp4(f);
            name.setText(f.getName());
            meta.setText(ClipStore.formatWhen(f.lastModified())
                    + "  ·  " + ClipStore.formatSize(f.length())
                    + (ok ? "" : "  ·  " + getString(R.string.clip_unplayable)));
            name.setTextColor(getColor(ok ? R.color.text_primary : R.color.accent));
            return v;
        }
    }
}
