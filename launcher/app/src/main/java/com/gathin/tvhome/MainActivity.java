package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.widget.EditText;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * tvOS-inspired home: a glass dock, an expanding app grid, folders, and app-colour focus lighting.
 */
public class MainActivity extends Activity implements AppAdapter.TileListener {

    /** Tiles per row and dock slots (res/values/config.xml, columns). Set in onCreate. */
    static int COLUMNS = 4;
    private static final long MINUTE_MS = 60_000L;
    private String liveTvPkg;
    private String liveTvAction;
    private String sourcePanelService;
    private String sourcePanelAction;

    private AppAdapter adapter;
    private RecyclerView grid;
    private FrameLayout root;
    private GlassView glass;
    private View ambient;
    private TextView heroName;
    private GradientDrawable ambientGlow;
    private int ambientColor;
    private boolean heroShown;
    private TextView greeting;
    private View btnInputs, btnSettings;
    private TextView btnWifi;
    private TextView shamsiDate;
    private boolean shamsi;
    private String vpnApp;
    private float glassTargetX, glassTargetY;
    private boolean glassShown;
    // Static so they survive the activity being destroyed while another app is open (low-memory TVs,
    // or Developer options → "Don't keep activities"); they only reset when the process dies.
    private static int lastFocused = 0;
    /** App of the last focused tile, so focus follows it when the app list changes. */
    private static String lastFocusedKey;
    private float density;
    private PopupWindow tileMenu;
    private boolean restoreMenuFocus = true;
    private boolean menuReleaseGuard;
    private String movingKey;
    private List<String> moveSnapshot;
    private HomeLayout savingLayout;
    private MoveIndicator moveIndicator;
    private HomeLayoutStore layoutStore;
    private DockDecoration dockDecoration;
    private View topBar, gridHost, atmosphere, folderPanel;
    private TextView folderTitle;
    private ImageView folderBackdrop;
    private boolean expanded;
    private boolean gridTransition;
    private int gridTransitionRequest;
    private ValueAnimator gridSlide;
    private String folderReturnKey;
    private AlertDialog editDialog;
    private int rowsBelowDock;
    private boolean savingOrderNow, paused = true, refreshPending;
    private int focusRequest;
    private String requestedFocusKey;
    private boolean confirmDown;
    private int moveAnimationRequest;
    private final BroadcastReceiver packageChanges = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                    && Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())) return;
            if (savingOrderNow) { refreshPending = true; return; }
            dismissTileMenu(false);
            if (movingKey != null) finishMove(false, false);
            String key = lastFocusedKey;
            int slot = lastFocused;
            adapter.reload();
            syncFolderPresentation();
            if (!paused) focusKey(key, slot);
        }
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    /** Refreshes the network pill when the connection changes: at boot Wi-Fi often connects after Home is shown. */
    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }

        @Override
        public void onLost(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }
    };
    /** Shows the Inputs pill once the TV registers an input: at boot HDMI inputs can appear after Home is shown. */
    private final TvInputManager.TvInputCallback inputCallback = new TvInputManager.TvInputCallback() {
        @Override
        public void onInputAdded(String inputId) {
            updateInputsButton();
        }

        @Override
        public void onInputRemoved(String inputId) {
            updateInputsButton();
        }
    };
    /** Listens for any VPN coming up or going down, even one this app's traffic is excluded from. */
    private final ConnectivityManager.NetworkCallback vpnCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }

        @Override
        public void onLost(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }
    };
    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            updateGreeting();
            updateWifiLabel();
            handler.postDelayed(this, MINUTE_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        density = getResources().getDisplayMetrics().density;
        // config.xml, overlaid by config.json from a pushed theme (see Config and tools/theme.sh).
        Config cfg = Config.load(this);
        COLUMNS = cfg.columns;
        liveTvPkg = cfg.liveTvPackage;
        liveTvAction = cfg.liveTvAction;
        sourcePanelService = cfg.sourcePanelService;
        sourcePanelAction = cfg.sourcePanelAction;

        root = findViewById(R.id.root);
        root.setFocusableInTouchMode(true);
        topBar = findViewById(R.id.topBar);
        gridHost = findViewById(R.id.gridHost);
        atmosphere = findViewById(R.id.atmosphere);
        folderPanel = findViewById(R.id.folderPanel);
        folderTitle = findViewById(R.id.folderTitle);
        folderBackdrop = findViewById(R.id.folderBackdrop);
        folderTitle.setTextColor(getResources().getColorStateList(R.color.app_action_text, getTheme()));
        folderPanel.setBackground(new GlassPanelDrawable(density, 36));
        folderTitle.setOnClickListener(v -> renameFolder(adapter.activeFolder()));
        folderTitle.setOnKeyListener((v, code, event) -> {
            if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (event.getAction() == KeyEvent.ACTION_DOWN)
                    focusTile(Math.min(lastFocused, adapter.columns() - 1));
                return true;
            }
            return code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_LEFT
                    || code == KeyEvent.KEYCODE_DPAD_RIGHT;
        });
        root.setBackgroundColor(cfg.backgroundColor);
        if (cfg.wallpaper != null) showWallpaper(cfg.wallpaper);
        glass = findViewById(R.id.glass);
        ambient = findViewById(R.id.ambient);
        heroName = findViewById(R.id.heroName);
        ambientGlow = new GradientDrawable();
        ambientGlow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        ambientGlow.setGradientCenter(0.32f, 0.42f);
        ambient.setBackground(ambientGlow);
        ambient.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                ambientGlow.setGradientRadius((r - l) * 0.62f));
        greeting = findViewById(R.id.greeting);
        btnWifi = findViewById(R.id.btnWifi);
        shamsiDate = findViewById(R.id.shamsiDate);
        shamsi = cfg.shamsiDate;
        if (shamsi) {
            shamsiDate.setVisibility(View.VISIBLE);
            TextView date = findViewById(R.id.date);
            date.setTextSize(11f);
            date.setPadding(0, (int) (4 * density), 0, 0);
        }
        vpnApp = cfg.vpnApp;

        grid = findViewById(R.id.grid);
        grid.setLayoutManager(new GridLayoutManager(this, COLUMNS) {
            @Override public boolean onRequestChildFocus(@NonNull RecyclerView parent,
                    @NonNull RecyclerView.State state, @NonNull View child, View focused) {
                // Focus must not scroll the grid underneath its dock-to-grid slide. Our explicit
                // navigation reveals off-screen rows; attached opening rows stay on the same anchor.
                return gridTransition || (adapter != null && adapter.activeFolder() == null && !expanded)
                        || super.onRequestChildFocus(parent, state, child, focused);
            }
        });
        grid.setHasFixedSize(true);
        // Focus restoration belongs to component keys below, not RecyclerView's old child position.
        grid.setPreserveFocusAfterLayout(false);
        grid.setItemAnimator(null);
        dockDecoration = new DockDecoration(COLUMNS, density);
        grid.addItemDecoration(dockDecoration);
        adapter = new AppAdapter(this, this, COLUMNS, cfg);
        grid.setAdapter(adapter);
        layoutStore = new HomeLayoutStore(this);
        moveIndicator = new MoveIndicator(this);
        moveIndicator.setVisibility(View.GONE);
        root.addView(moveIndicator, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        IntentFilter packages = new IntentFilter();
        packages.addAction(Intent.ACTION_PACKAGE_ADDED);
        packages.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packages.addAction(Intent.ACTION_PACKAGE_CHANGED);
        packages.addDataScheme("package");
        registerReceiver(packageChanges, packages);

        // tvOS layout: push the dock row towards the bottom of the screen, leaving room for
        // rows_below_dock grid rows under it; the rest of the grid stays below until you press Down.
        rowsBelowDock = cfg.rowsBelowDock;
        grid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> updateGridPadding());

        // Keep the glass glued to its tile while the grid scrolls.
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                alignFocusedTile();
            }
        });

        btnInputs = findViewById(R.id.btnInputs);
        btnSettings = findViewById(R.id.btnSettings);
        btnInputs.setOnClickListener(v -> showInputs());
        btnWifi.setOnClickListener(v -> openNetworkPill());
        btnWifi.setOnLongClickListener(v -> {
            openSettings(Settings.ACTION_WIFI_SETTINGS);
            return true;
        });
        btnSettings.setOnClickListener(v -> openSettings(Settings.ACTION_SETTINGS));

        // Header and grid directions share the activity key router. Do not send Down through
        // TextView/Android focus search to the RecyclerView container instead of an actual card.

        // Hide the glass when focus leaves the tiles.
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((oldF, newF) -> {
            boolean onTile = newF != null && newF.getId() == R.id.card;
            if (!onTile) hideGlass();
        });

        // Folder/span/padding changes move children without emitting a scroll delta. Realign the
        // floating highlight after layout as well as on focus and scroll events.
        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            alignFocusedTile();
        });

        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim != null) tim.registerCallback(inputCallback, handler);
        if (cm != null) {
            cm.registerDefaultNetworkCallback(networkCallback);
            cm.registerNetworkCallback(new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    .build(), vpnCallback);
        }
    }

    /**
     * A theme's full-screen picture behind everything. Decoded once, downsampled to the screen so a 4K
     * JPEG costs a 1080p bitmap at most (about 8 MB), which is the price of a wallpaper on a 1 GB TV.
     */
    private void showWallpaper(File file) {
        try {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(file.getPath(), opts);
            int sample = 1;
            while (opts.outWidth / (sample * 2) >= dm.widthPixels && opts.outHeight / (sample * 2) >= dm.heightPixels) sample *= 2;
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sample;
            opts.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565;   // no alpha needed, half the memory
            android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeFile(file.getPath(), opts);
            if (bmp == null) return;
            android.widget.ImageView wall = new android.widget.ImageView(this);
            wall.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            wall.setImageBitmap(bmp);
            root.addView(wall, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } catch (Exception | OutOfMemoryError e) {
            // A bad or huge picture must never take the home screen down; the plain colour stays.
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        paused = false;
        glassShown = false;
        glass.setAlpha(0f);
        heroShown = false;
        heroName.setAlpha(0f);
        ambient.setAlpha(0f);
        // Read these before reload(): rebinding can move focus to the first tile and overwrite them.
        final String returnKey = lastFocusedKey;
        final int returnSlot = lastFocused;
        adapter.reload();
        if (savingOrderNow && savingLayout != null) adapter.restoreLayout(savingLayout);
        syncFolderPresentation();
        updateGreeting();
        updateWifiLabel();
        updateInputsButton();
        handler.postDelayed(minuteTick, MINUTE_MS);

        // Return to the app we left from, or the same slot if that app is gone. Always refocus: after
        // reload() the view that holds focus may no longer show the app it had.
        int pos = adapter.positionOf(returnKey);
        focusTile(pos >= 0 ? pos : returnSlot);
    }

    @Override
    protected void onPause() {
        super.onPause();
        paused = true;
        cancelGridTransition();
        focusRequest++;
        requestedFocusKey = null;
        dismissTileMenu(false);
        if (editDialog != null) editDialog.dismiss();
        if (movingKey != null && !savingOrderNow) finishMove(false, false);
        handler.removeCallbacks(minuteTick);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(packageChanges);
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim != null) tim.unregisterCallback(inputCallback);
        if (cm != null) {
            cm.unregisterNetworkCallback(networkCallback);
            cm.unregisterNetworkCallback(vpnCallback);
        }
    }

    @Override
    public void onBackPressed() {
        if (tileMenu != null) dismissTileMenu(true);
        else if (movingKey != null && !savingOrderNow) finishMove(false, true);
        else if (!savingOrderNow && adapter.activeFolder() != null) closeFolder();
        else if (!savingOrderNow && expanded) returnHome();
    }

    // ---- glass highlight -------------------------------------------------------------------------

    @Override
    public void onTileFocused(int position, View card) {
        if (requestedFocusKey != null && !requestedFocusKey.equals(adapter.keyAt(position))) return;
        lastFocused = position;
        lastFocusedKey = adapter.keyAt(position);
        glass.setTint(adapter.ambientColorAt(position));
        alignTilePresentation(position, card);
        if (gridTransition) return;
        ensureTileVisible(position, card);
    }

    /** Presentation callbacks never request scrolling or layout for the RecyclerView. */
    private void alignFocusedTile() {
        if (paused || tileMenu != null || editDialog != null) return;
        View card = getCurrentFocus();
        if (card == null || card.getId() != R.id.card) return;
        RecyclerView.ViewHolder holder = grid.findContainingViewHolder(card);
        if (holder == null) return;
        int position = holder.getBindingAdapterPosition();
        if (position == RecyclerView.NO_POSITION
                || (requestedFocusKey != null && !requestedFocusKey.equals(adapter.keyAt(position)))) return;
        alignTilePresentation(position, card);
    }

    private void alignTilePresentation(int position, View card) {
        // Tile rect in root coordinates, using layout positions (ignores the scale animation).
        Rect r = new Rect(0, 0, card.getWidth(), card.getHeight());
        root.offsetDescendantRectToMyCoords(card, r);
        // offsetDescendantRectToMyCoords uses layout bounds, not the host's GPU translation.
        r.offset(0, Math.round(gridHost.getTranslationY()));
        float pad = GlassView.HALO_INSET_DP * density;
        float w = card.getWidth() * AppAdapter.FOCUS_SCALE + 2 * pad;
        float h = card.getHeight() * AppAdapter.FOCUS_SCALE + 2 * pad;
        float x = r.exactCenterX() - w / 2f;
        float y = r.exactCenterY() - h / 2f;

        ViewGroup.LayoutParams lp = glass.getLayoutParams();
        if (lp.width != (int) w || lp.height != (int) h) {
            lp.width = (int) w;
            lp.height = (int) h;
            glass.setLayoutParams(lp);
            glass.setRadius(AppAdapter.cornerRadius(card.getWidth(), density) * AppAdapter.FOCUS_SCALE);
        }
        glassTargetX = x;
        glassTargetY = y;
        if (!glassShown) {
            glass.setTranslationX(x);
            glass.setTranslationY(y);
            glass.animate().alpha(1f).setDuration(180).start();
            glassShown = true;
        } else {
            // Coloured light belongs to the selected tile, so it never slides across empty space.
            glass.setTranslationX(x);
            glass.setTranslationY(y);
        }
        onTileFocusFrame(card);
        if (adapter.activeFolder() == null && !expanded && !gridTransition && position < COLUMNS)
            showHero(position, r.top);
        else hideHero();
    }

    private void ensureTileVisible(int position, View card) {
        View item = (View) card.getParent();
        if (adapter.activeFolder() == null && !expanded && position < COLUMNS) {
            // The collapsed dock shares the expanded layout. Only the host translates; do not
            // start another smooth scroll from focus/global-layout callbacks.
            GridLayoutManager layout = (GridLayoutManager) grid.getLayoutManager();
            View first = layout.findViewByPosition(0);
            if (first == null || Math.abs(layout.getDecoratedTop(first) - grid.getPaddingTop()) > 1)
                layout.scrollToPositionWithOffset(0, 0);
        } else {
            if ((expanded || adapter.activeFolder() != null) && position < adapter.columns() * 2) {
                // With a long app list, focus-search can carry the old collapsed offset into the
                // expanded layout. Keep both opening rows, including the entire dock, on screen.
                GridLayoutManager layout = (GridLayoutManager) grid.getLayoutManager();
                View first = layout.findViewByPosition(0);
                if (first == null || Math.abs(layout.getDecoratedTop(first) - grid.getPaddingTop()) > 1)
                    layout.scrollToPositionWithOffset(0, 0);
            } else {
                float pad = GlassView.HALO_INSET_DP * density;
                Rect want = new Rect(0, -(int) pad * 2, item.getWidth(), item.getHeight() + (int) pad);
                grid.requestChildRectangleOnScreen(item, want, false);
            }
        }
    }

    @Override public void onTileFocusFrame(View card) {
        if (!card.isFocused() || !glassShown) return;
        // Scale the cached light with the icon's existing GPU animation; no bitmap rebuild per frame.
        glass.setScaleX(card.getScaleX() / AppAdapter.FOCUS_SCALE);
        glass.setScaleY(card.getScaleY() / AppAdapter.FOCUS_SCALE);
        if (movingKey != null) {
            int position = adapter.positionOf(movingKey);
            moveIndicator.follow(glassTargetX + glass.getLayoutParams().width / 2f,
                    glassTargetY + glass.getLayoutParams().height / 2f,
                    card.getWidth() * card.getScaleX(), card.getHeight() * card.getScaleY(),
                    position, adapter.getItemCount(), adapter.columns());
        }
    }

    // ---- top shelf -------------------------------------------------------------------------------

    private void showHero(int position, float dockTopInRoot) {
        String label = adapter.labelAt(position);
        if (label.trim().isEmpty()) { hideHero(); return; }
        int color = adapter.ambientColorAt(position);
        float y = dockTopInRoot - 14 * density - 26 * density - heroName.getHeight();
        boolean changed = !label.contentEquals(heroName.getText());

        if (!heroShown) {
            heroName.setText(label);
            heroName.setY(y);
            heroName.setTranslationX(0f);
            heroName.animate().alpha(1f).setDuration(260).start();
            setAmbient(color);
            ambient.animate().alpha(1f).setDuration(600).start();
            heroShown = true;
        } else if (changed) {
            heroName.animate().alpha(0f).translationX(-12 * density).setDuration(120).withEndAction(() -> {
                heroName.setText(label);
                heroName.setY(y);
                heroName.setTranslationX(12 * density);
                heroName.animate().alpha(1f).translationX(0f).setDuration(200).start();
            }).start();
            if (color != ambientColor) {
                ambient.animate().alpha(0.35f).setDuration(200).withEndAction(() -> {
                    setAmbient(color);
                    ambient.animate().alpha(1f).setDuration(500).start();
                }).start();
            }
        } else {
            heroName.setY(y);
        }
    }

    private void setAmbient(int color) {
        ambientColor = color;
        int c = color & 0x00FFFFFF;
        ambientGlow.setColors(new int[]{0x5C000000 | c, 0x26000000 | c, 0x00000000 | c});
    }

    private void hideHero() {
        if (!heroShown) return;
        heroShown = false;
        // Cancel label/colour end actions before fading away so they cannot revive the shelf
        // halfway through expansion.
        heroName.animate().cancel();
        ambient.animate().cancel();
        heroName.animate().alpha(0f).setDuration(160).start();
        ambient.animate().alpha(0f).setDuration(300).start();
    }

    private void hideGlass() {
        if (!glassShown) return;
        glassShown = false;
        glass.animate().alpha(0f).setDuration(160).start();
    }

    // ---- navigation ------------------------------------------------------------------------------

    @Override
    public boolean onTileVerticalKey(int position, int keyCode) {
        int columns = adapter.columns(), count = adapter.getItemCount();
        AppOrder.Direction direction;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT: direction = AppOrder.Direction.LEFT; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: direction = AppOrder.Direction.RIGHT; break;
            case KeyEvent.KEYCODE_DPAD_UP: direction = AppOrder.Direction.UP; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: direction = AppOrder.Direction.DOWN; break;
            default: return false;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP && position < columns) {
            if (adapter.activeFolder() != null) {
                if (folderTitle.getVisibility() != View.VISIBLE) return true;
                focusRequest++;
                requestedFocusKey = null;
                folderTitle.requestFocusFromTouch();
                return true;
            }
            if (expanded) { setExpanded(false); focusTile(position); return true; }
            focusRequest++;
            requestedFocusKey = null;
            (btnInputs.getVisibility() == View.VISIBLE ? btnInputs : btnWifi).requestFocusFromTouch();
            return true;
        }
        int target = AppOrder.target(position, count, columns, direction);
        if (target != position && adapter.activeFolder() == null && !expanded && target >= columns)
            setExpanded(true);
        focusTile(target);
        return true;
    }

    private void updateGridPadding() {
        int want = dp(adapter.activeFolder() != null ? 18 : 24);
        if (grid.getPaddingTop() != want) grid.setPadding(0, want, 0, grid.getPaddingBottom());
        if (!gridTransition) gridHost.setTranslationY(adapter.activeFolder() == null && !expanded
                ? collapsedGridOffset() : 0f);
    }

    private float collapsedGridOffset() {
        int inner = grid.getWidth() - grid.getPaddingLeft() - grid.getPaddingRight();
        int itemH = AppAdapter.itemHeightPx(AppAdapter.tileHeightPx(
                AppAdapter.tileWidthPx(inner, COLUMNS, density)), density);
        return Math.max(0, grid.getHeight() - grid.getPaddingTop()
                - itemH * (1 + rowsBelowDock) - dp(48));
    }

    private void cancelGridTransition() {
        gridTransitionRequest++;
        if (gridSlide != null) {
            gridSlide.removeAllListeners();
            gridSlide.removeAllUpdateListeners();
            gridSlide.cancel();
            gridSlide = null;
        }
        gridTransition = false;
    }

    private void setExpanded(boolean value) {
        if (expanded == value) return;
        // Home and the expanded grid use identical bounds, padding, and row positions. One GPU
        // translation owns the slide; reversals start at its current value without a relayout snap.
        cancelGridTransition();
        expanded = value;
        gridTransition = true;
        int transitionRequest = gridTransitionRequest;
        grid.stopScroll();
        hideHero();
        topBar.animate().cancel();
        topBar.setVisibility(View.VISIBLE);
        topBar.animate().alpha(value ? 0f : 1f).setDuration(160).withEndAction(() -> {
            if (transitionRequest == gridTransitionRequest && expanded) topBar.setVisibility(View.INVISIBLE);
        }).start();
        atmosphere.animate().alpha(value ? .92f : .42f).setDuration(320).start();
        float target = value ? 0f : collapsedGridOffset();
        gridSlide = ValueAnimator.ofFloat(gridHost.getTranslationY(), target);
        gridSlide.setDuration(320);
        gridSlide.setInterpolator(new android.view.animation.DecelerateInterpolator(1.4f));
        gridSlide.addUpdateListener(animation -> {
            if (transitionRequest != gridTransitionRequest || gridSlide != animation) return;
            gridHost.setTranslationY((Float) animation.getAnimatedValue());
            alignFocusedTile();
        });
        gridSlide.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (transitionRequest != gridTransitionRequest) return;
                gridSlide = null;
                gridTransition = false;
                View card = getCurrentFocus();
                RecyclerView.ViewHolder holder = card == null ? null : grid.findContainingViewHolder(card);
                if (holder != null && holder.getBindingAdapterPosition() != RecyclerView.NO_POSITION
                        && requestedFocusKey == null) onTileFocused(holder.getBindingAdapterPosition(), card);
                else alignFocusedTile();
            }
        });
        gridSlide.start();
    }

    @Override public void onFolderOpened(String key) {
        if (savingOrderNow || adapter.folder(key) == null) return;
        folderReturnKey = key;
        try {
            folderBackdrop.setImageBitmap(FolderBackdrop.capture(root));
            folderBackdrop.setVisibility(View.VISIBLE);
        } catch (RuntimeException ignored) {
            // The static atmosphere remains if a vendor cannot draw a home snapshot.
        }
        adapter.showFolder(key);
        syncFolderPresentation();
        focusTile(0);
    }

    private void syncFolderPresentation() {
        cancelGridTransition();
        topBar.animate().cancel();
        boolean inFolder = adapter.activeFolder() != null;
        HomeLayout.Folder folder = adapter.folder(adapter.activeFolder());
        folderPanel.setVisibility(inFolder ? View.VISIBLE : View.GONE);
        if (!inFolder) { folderBackdrop.setVisibility(View.GONE); folderBackdrop.setImageDrawable(null); }
        folderTitle.setVisibility(folder != null && !folder.name.isEmpty() ? View.VISIBLE : View.GONE);
        if (folder != null) folderTitle.setText(folder.name);
        topBar.setVisibility(inFolder || expanded ? View.INVISIBLE : View.VISIBLE);
        topBar.setAlpha(inFolder || expanded ? 0f : 1f);
        dockDecoration.setEnabled(!inFolder);
        ((GridLayoutManager) grid.getLayoutManager()).setSpanCount(adapter.columns());
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) gridHost.getLayoutParams();
        lp.setMarginStart(dp(inFolder ? 180 : 24));
        lp.setMarginEnd(dp(inFolder ? 180 : 24));
        lp.topMargin = dp(inFolder ? 94 : 12);
        lp.bottomMargin = dp(inFolder ? 40 : 0);
        gridHost.setLayoutParams(lp);
        atmosphere.animate().alpha(inFolder ? 1f : expanded ? .92f : .42f).setDuration(260).start();
        hideHero();
        hideGlass();
        grid.invalidateItemDecorations();
        ((GridLayoutManager) grid.getLayoutManager()).scrollToPositionWithOffset(0, 0);
        grid.post(() -> {
            updateGridPadding();
        });
    }

    private void closeFolder() {
        String key = folderReturnKey;
        adapter.showFolder(null);
        syncFolderPresentation();
        focusKey(key, 0);
    }

    private void returnHome() {
        // Older RecyclerView/Android TV focus recovery can pull the viewport back to a recycled
        // bottom-row child while Home is laying out. Release that child before resetting the grid.
        root.requestFocusFromTouch();
        adapter.showFolder(null);
        // Android 9 delivers a new Home intent while paused, then calls onResume. Persist the
        // reset now so onResume cannot restore the app from the old, scrolled row.
        lastFocused = 0;
        lastFocusedKey = adapter.keyAt(0);
        expanded = false;
        syncFolderPresentation();
        ((GridLayoutManager) grid.getLayoutManager()).scrollToPositionWithOffset(0, 0);
        focusTile(0);
    }

    private void focusTile(int position) {
        if (adapter.getItemCount() == 0 || paused) return;
        int pos = Math.max(0, Math.min(position, adapter.getItemCount() - 1));
        String key = adapter.keyAt(pos);
        lastFocused = pos;
        lastFocusedKey = key;
        requestedFocusKey = key;
        int request = ++focusRequest;
        grid.stopScroll();
        RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(pos);
        if (holder == null) grid.scrollToPosition(pos);
        requestTileFocus(key, request, 0);
    }

    private void requestTileFocus(String key, int request, int attempt) {
        if (paused || request != focusRequest || tileMenu != null || editDialog != null) return;
        if (attempt > 24) { requestedFocusKey = null; return; }
        int pos = adapter.positionOf(key);
        if (pos < 0) { requestedFocusKey = null; return; }
        RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(pos);
        if (holder != null && !grid.isComputingLayout() && !grid.hasPendingAdapterUpdates()) {
            View card = holder.itemView.findViewById(R.id.card);
            // A remote/mouse click on a dialog can leave the window in touch mode. Explicitly
            // return focus to the requested tile before OK can activate a different app.
            if (card.requestFocusFromTouch()) {
                // A successful request is not proof that focus survived layout. Older TV builds
                // can restore the previous header button later in the same frame. Keep the intent
                // until the next traversal finishes, then retry if the card lost focus.
                grid.postOnAnimation(() -> grid.post(() -> {
                    if (paused || request != focusRequest || tileMenu != null || editDialog != null) return;
                    int currentPosition = adapter.positionOf(key);
                    RecyclerView.ViewHolder current = currentPosition < 0 ? null
                            : grid.findViewHolderForAdapterPosition(currentPosition);
                    if (current == holder && holder.getBindingAdapterPosition() == currentPosition
                            && card.isFocused() && !grid.hasPendingAdapterUpdates()) {
                        requestedFocusKey = null;
                        onTileFocused(currentPosition, card);
                    } else {
                        requestTileFocus(key, request, attempt + 1);
                    }
                }));
                return;
            }
        }
        // Attached views can briefly refuse focus during a rebuild as well. Do not leave the
        // pending key stuck forever just because a holder happened to exist on the first attempt.
        if (attempt < 24) {
            grid.postOnAnimation(() -> requestTileFocus(key, request, attempt + 1));
        } else {
            requestedFocusKey = null;
        }
    }

    private void focusKey(String key, int fallback) {
        if (paused || tileMenu != null) return;
        int pos = adapter.positionOf(key);
        focusTile(pos >= 0 ? pos : fallback);
    }

    // ---- app actions and remote reordering -------------------------------------------------------

    private boolean handleTopBarDirection(View focused, KeyEvent event) {
        if (focused != btnInputs && focused != btnWifi && focused != btnSettings) return false;
        if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
            focusTile(Math.min(lastFocused, COLUMNS - 1));
        } else {
            // Explicit header navigation also cancels any deferred tile restoration from resume.
            focusRequest++;
            requestedFocusKey = null;
            View next = focused;
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (focused == btnSettings) next = btnWifi;
                else if (focused == btnWifi && btnInputs.getVisibility() == View.VISIBLE) next = btnInputs;
            } else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (focused == btnInputs) next = btnWifi;
                else if (focused == btnWifi) next = btnSettings;
            }
            next.requestFocusFromTouch();
        }
        return true;
    }

    private int dp(float value) { return Math.round(value * density); }

    private static boolean isConfirm(int key) {
        return key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER
                || key == KeyEvent.KEYCODE_NUMPAD_ENTER || key == KeyEvent.KEYCODE_BUTTON_A;
    }

    @Override
    public boolean onTileClicked() {
        if (movingKey != null) { saveMove(); return true; }
        return tileMenu != null || savingOrderNow;
    }

    @android.annotation.SuppressLint("RtlHardcoded")
    @Override
    public void onTileLongPressed(int position, View card) {
        if (movingKey != null || tileMenu != null || savingOrderNow || paused) return;
        String key = adapter.keyAt(position);
        if (key == null) return;
        final int fallback = position;
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(8), dp(8), dp(8), dp(8));
        TextView title = new TextView(this);
        title.setText(adapter.labelAt(position));
        title.setTextSize(13);
        title.setTextColor(0xCFFFFFFF);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setPadding(dp(14), dp(8), dp(14), dp(12));
        panel.addView(title);
        PopupWindow popup = new PopupWindow(panel, dp(264), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setElevation(dp(16));
        popup.setOutsideTouchable(true);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setAnimationStyle(0);
        tileMenu = popup;
        restoreMenuFocus = true;
        menuReleaseGuard = true;
        TextView move = addAppAction(panel, R.string.move_app, R.drawable.ic_move, () -> {
            dismissTileMenu(false);
            beginMove(key);
        });
        if (adapter.isFolder(position)) {
            addAppAction(panel, R.string.rename_folder, R.drawable.ic_folder, () -> {
                dismissTileMenu(false);
                renameFolder(key);
            });
        } else if (adapter.isApp(position)) {
            addAppAction(panel, R.string.move_to, R.drawable.ic_folder, () -> {
                dismissTileMenu(false);
                showMoveTo(key);
            });
            addAppAction(panel, R.string.app_settings, R.drawable.ic_settings, () -> {
                dismissTileMenu(false);
                adapter.openAppSettings(key);
            });
        }
        popup.setOnDismissListener(() -> {
            if (tileMenu == popup) tileMenu = null;
            adapter.setMenuKey(null);
            if (restoreMenuFocus && !paused) focusKey(key, fallback);
        });
        // Use transformed screen bounds so the gap starts at the focused tile's visible edge.
        Rect tile = new Rect();
        card.getGlobalVisibleRect(tile);
        int[] screen = new int[2], window = new int[2];
        root.getLocationOnScreen(screen);
        root.getLocationInWindow(window);
        int inset = dp(16), gap = dp(12);
        int leftSpace = tile.left - screen[0] - inset - gap;
        int rightSpace = screen[0] + root.getWidth() - inset - tile.right - gap;
        boolean right = rightSpace >= dp(264) || (leftSpace < dp(264) && rightSpace >= leftSpace);
        int width = Math.min(dp(264), Math.max(1, right ? rightSpace : leftSpace));
        panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(root.getHeight() - inset * 2, View.MeasureSpec.AT_MOST));
        int height = panel.getMeasuredHeight();
        popup.setWidth(width);
        popup.setHeight(height);
        int x = right ? tile.right + gap : tile.left - gap - width;
        int y = Math.max(screen[1] + inset, Math.min(tile.centerY() - height / 2,
                screen[1] + root.getHeight() - inset - height));
        panel.setBackground(new FrostedGlassDrawable(root,
                new Rect(x - screen[0], y - screen[1], x - screen[0] + width, y - screen[1] + height),
                density, 26));
        adapter.setMenuKey(key);
        popup.showAtLocation(root, Gravity.TOP | Gravity.LEFT,
                x - screen[0] + window[0], y - screen[1] + window[1]);
        panel.setAlpha(0f);
        panel.setPivotX(right ? 0 : width);
        panel.setPivotY(height / 2f);
        panel.setScaleX(.94f); panel.setScaleY(.94f);
        panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        move.requestFocusFromTouch();
    }

    private void showMoveTo(String key) {
        if (savingOrderNow) return;
        HomeLayout layout = adapter.snapshotLayout();
        List<String> labels = new ArrayList<>(), destinations = new ArrayList<>();
        labels.add(getString(R.string.new_folder));
        destinations.add("new");
        if (adapter.activeFolder() != null) {
            labels.add(getString(R.string.move_to_home));
            destinations.add(null);
        }
        for (HomeLayout.Folder folder : layout.folders.values()) {
            if (folder.id.equals(adapter.activeFolder())) continue;
            labels.add(folder.name.isEmpty() ? getString(R.string.unnamed_folder) : folder.name);
            destinations.add(folder.id);
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.move_to)
                .setAdapter(new android.widget.ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, labels) {
                    @Override public View getView(int position, View convertView, ViewGroup parent) {
                        TextView row = (TextView) super.getView(position, convertView, parent);
                        row.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
                        row.setTextSize(17);
                        row.setTextColor(getResources().getColorStateList(R.color.glass_action_text, getTheme()));
                        row.setBackgroundResource(R.drawable.glass_menu_row);
                        row.setMinHeight(dp(52));
                        row.setPadding(dp(18), dp(12), dp(18), dp(12));
                        android.graphics.drawable.Drawable icon = getDrawable(R.drawable.ic_folder).mutate();
                        icon.setBounds(0, 0, dp(22), dp(22));
                        row.setCompoundDrawablesRelative(icon, null, null, null);
                        row.setCompoundDrawablePadding(dp(14));
                        row.setCompoundDrawableTintList(getResources().getColorStateList(R.color.glass_action_text, getTheme()));
                        return row;
                    }
                }, (d, which) -> {
                    editDialog = null;
                    String destination = destinations.get(which);
                    if ("new".equals(destination)) editFolderName(key, null);
                    else {
                        HomeLayout draft = adapter.snapshotLayout();
                        draft.moveTo(key, destination);
                        saveFolderChange(draft, destination == null ? key : destination);
                    }
                }).setNegativeButton(R.string.cancel, null).create();
        showEditDialog(dialog, key, false);
    }

    private void renameFolder(String key) {
        if (!savingOrderNow && adapter.folder(key) != null) editFolderName(null, key);
    }

    private void editFolderName(String appKey, String folderKey) {
        HomeLayout.Folder folder = adapter.folder(folderKey);
        if (folderKey != null && folder == null) return;
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextColor(Color.WHITE);
        input.setTextSize(22);
        input.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
        input.setPadding(dp(24), dp(16), dp(24), dp(16));
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(40)});
        input.setHint(R.string.folder_name);
        input.setText(folder != null ? folder.name : "");
        input.selectAll();
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(folder != null ? R.string.rename_folder : R.string.new_folder)
                .setView(input).setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.done, null).create();
        showEditDialog(dialog, appKey != null ? appKey : folderKey, true);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            HomeLayout draft = adapter.snapshotLayout();
            String focus;
            if (folderKey == null) focus = draft.createFolder(appKey, name);
            else { draft.folders.get(folderKey).name = name; focus = folderKey; }
            editDialog = null;
            dialog.dismiss();
            saveFolderChange(draft, focus);
        });
        input.requestFocus();
    }

    private void showEditDialog(AlertDialog dialog, String focus, boolean keyboard) {
        editDialog = dialog;
        hideGlass();
        dialog.setOnDismissListener(d -> {
            if (editDialog == dialog) editDialog = null;
            if (!paused && !savingOrderNow && editDialog == null) focusKey(focus, lastFocused);
        });
        dialog.show();
        if (!keyboard && dialog.getListView() != null) {
            dialog.getListView().setSelector(new ColorDrawable(Color.TRANSPARENT));
            dialog.getListView().setPadding(dp(12), 0, dp(12), dp(4));
            dialog.getListView().setDivider(null);
            dialog.getListView().setSelection(0);
            dialog.getListView().requestFocusFromTouch();
        }
        if (dialog.getWindow() != null) {
            // Keep the first measured frame opaque, then capture only the dialog's footprint.
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(0xFF343840));
            dialog.getWindow().setLayout(dp(440), ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setDimAmount(.32f);
            View decor = dialog.getWindow().getDecorView();
            int[] previous = {Integer.MIN_VALUE, 0, 0, 0};
            Runnable material = () -> {
                if (!dialog.isShowing() || decor.getWidth() <= 0 || decor.getHeight() <= 0) return;
                int[] location = new int[2], home = new int[2];
                decor.getLocationOnScreen(location);
                root.getLocationOnScreen(home);
                int x = location[0] - home[0], y = location[1] - home[1];
                int width = decor.getWidth(), height = decor.getHeight();
                if (previous[0] == x && previous[1] == y && previous[2] == width && previous[3] == height) return;
                previous[0] = x; previous[1] = y; previous[2] = width; previous[3] = height;
                dialog.getWindow().setBackgroundDrawable(new FrostedGlassDrawable(root,
                        new Rect(x, y, x + width, y + height), density, 28));
            };
            decor.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> material.run());
            decor.post(material);
            for (int which : new int[]{AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_POSITIVE}) {
                android.widget.Button button = dialog.getButton(which);
                if (button == null) continue;
                button.setAllCaps(false);
                button.setTextSize(16);
                button.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
                button.setTextColor(getResources().getColorStateList(R.color.glass_action_text, getTheme()));
                button.setBackgroundResource(R.drawable.glass_menu_row);
                button.setPadding(dp(18), dp(10), dp(18), dp(10));
            }
            if (keyboard) dialog.getWindow().setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
    }

    private void saveFolderChange(HomeLayout draft, String focus) {
        if (savingOrderNow) return;
        savingOrderNow = true;
        savingLayout = draft.copy();
        final String originalFolder = adapter.activeFolder();
        AppOrderStore.WRITER.execute(() -> {
            boolean saved = layoutStore.write(draft);
            handler.post(() -> {
                if (isDestroyed()) return;
                savingOrderNow = false;
                savingLayout = null;
                if (saved) {
                    adapter.restoreLayout(draft);
                    // A move from a folder returns to Home so its new destination is clear.
                    if (originalFolder != null && !originalFolder.equals(focus)) adapter.showFolder(null);
                    syncFolderPresentation();
                    if (!paused) focusKey(adapter.activeFolder() != null ? lastFocusedKey : focus, 0);
                } else if (!paused) {
                    Toast.makeText(this, R.string.folder_save_failed, Toast.LENGTH_LONG).show();
                    focusKey(lastFocusedKey, lastFocused);
                }
                if (refreshPending) {
                    refreshPending = false;
                    adapter.reload();
                    syncFolderPresentation();
                    if (!paused) focusKey(focus, 0);
                }
            });
        });
    }

    private TextView addAppAction(LinearLayout panel, int title, int icon, Runnable action) {
        TextView row = new TextView(this);
        row.setText(title);
        row.setTextSize(16);
        row.setTextColor(getResources().getColorStateList(R.color.app_action_text, getTheme()));
        row.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinHeight(dp(50));
        row.setPadding(dp(14), dp(10), dp(14), dp(10));
        android.graphics.drawable.Drawable glyph = getDrawable(icon).mutate();
        glyph.setBounds(0, 0, dp(20), dp(20));
        row.setCompoundDrawablesRelative(glyph, null, null, null);
        row.setCompoundDrawablePadding(dp(12));
        row.setCompoundDrawableTintList(getResources().getColorStateList(R.color.app_action_text, getTheme()));
        row.setBackgroundResource(R.drawable.glass_menu_row);
        row.setFocusable(true);
        row.setClickable(true);
        row.setOnClickListener(v -> action.run());
        row.setOnKeyListener((v, code, event) -> {
            if (isConfirm(code)) {
                // The release of the long press must not activate the newly focused menu action.
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0)
                    menuReleaseGuard = false;
                if (menuReleaseGuard) {
                    if (event.getAction() == KeyEvent.ACTION_UP) menuReleaseGuard = false;
                    return true;
                }
                if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) action.run();
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) return true;
            if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    int index = panel.indexOfChild(row);
                    int next = code == KeyEvent.KEYCODE_DPAD_UP ? index - 1 : index + 1;
                    if (next >= 1 && next < panel.getChildCount()) panel.getChildAt(next).requestFocus();
                }
                return true;
            }
            return false;
        });
        panel.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private void dismissTileMenu(boolean restoreFocus) {
        restoreMenuFocus = restoreFocus;
        if (tileMenu != null) tileMenu.dismiss();
    }

    private void beginMove(String key) {
        if (adapter.positionOf(key) < 0) return;
        moveSnapshot = adapter.keys();
        movingKey = key;
        confirmDown = false;
        adapter.setMovingKey(key);
        moveIndicator.setVisibility(View.VISIBLE);
        focusKey(key, 0);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (android.util.Log.isLoggable("TvHomeMotion", android.util.Log.DEBUG)
                && event.getAction() == KeyEvent.ACTION_DOWN)
            android.util.Log.d("TvHomeMotion", "key=" + code + " expanded=" + expanded
                    + " transition=" + gridTransition + " offset=" + gridHost.getTranslationY()
                    + " requested=" + requestedFocusKey + " last=" + lastFocused
                    + " paused=" + paused + " focus=" + getCurrentFocus());
        if (movingKey == null) {
            boolean arrow = code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
                    || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN;
            if (arrow && !paused && tileMenu == null && editDialog == null && !savingOrderNow) {
                // Visible header focus takes priority over a stale pending tile request.
                if (handleTopBarDirection(getCurrentFocus(), event)) return true;
                // Continue from the latest requested key even when RecyclerView still holds focus
                // on an old/recycled child. Android's geometric focus search cannot do that.
                int position = adapter.positionOf(requestedFocusKey);
                if (position < 0) {
                    View focused = getCurrentFocus();
                    RecyclerView.ViewHolder holder = focused == null ? null
                            : grid.findContainingViewHolder(focused);
                    if (holder != null) position = holder.getBindingAdapterPosition();
                    else if (focused == root || focused == grid || focused == null)
                        position = adapter.positionOf(lastFocusedKey);
                }
                if (position >= 0) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) onTileVerticalKey(position, code);
                    return true;
                }
            }
            return super.dispatchKeyEvent(event);
        }
        if (isConfirm(code)) {
            if (!savingOrderNow) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) confirmDown = true;
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    boolean confirm = confirmDown && !event.isCanceled();
                    confirmDown = false;
                    if (confirm) saveMove();
                }
            }
            return true;
        }
        if (code == KeyEvent.KEYCODE_BACK) {
            if (!savingOrderNow && event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) finishMove(false, true);
            return true;
        }
        AppOrder.Direction direction;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT: direction = AppOrder.Direction.LEFT; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: direction = AppOrder.Direction.RIGHT; break;
            case KeyEvent.KEYCODE_DPAD_UP: direction = AppOrder.Direction.UP; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: direction = AppOrder.Direction.DOWN; break;
            default: return super.dispatchKeyEvent(event);
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && !savingOrderNow) {
            int from = adapter.positionOf(movingKey);
            int to = AppOrder.target(from, adapter.getItemCount(), adapter.columns(), direction);
            if (adapter.activeFolder() == null && !expanded && to >= COLUMNS) setExpanded(true);
            if (to != from) {
                animateMove(from, to);
                focusKey(movingKey, to);
            }
        }
        return true;
    }

    private void animateMove(int from, int to) {
        java.util.Map<String, android.graphics.Point> before = new java.util.HashMap<>();
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            int pos = grid.getChildAdapterPosition(child);
            if (pos < 0) continue;
            before.put(adapter.keyAt(pos), new android.graphics.Point(child.getLeft(), child.getTop()));
            child.animate().cancel();
            child.setTranslationX(0f);
            child.setTranslationY(0f);
        }
        adapter.move(from, to);
        grid.invalidateItemDecorations();
        int animationRequest = ++moveAnimationRequest;
        grid.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                grid.getViewTreeObserver().removeOnPreDrawListener(this);
                if (paused || movingKey == null || animationRequest != moveAnimationRequest) return true;
                for (int i = 0; i < grid.getChildCount(); i++) {
                    View child = grid.getChildAt(i);
                    int pos = grid.getChildAdapterPosition(child);
                    String key = adapter.keyAt(pos);
                    android.graphics.Point old = before.get(key);
                    if (old == null || key.equals(movingKey)) continue;
                    child.setTranslationX(old.x - child.getLeft());
                    child.setTranslationY(old.y - child.getTop());
                    child.animate().translationX(0).translationY(0).setDuration(140).start();
                }
                return true;
            }
        });
    }

    private void saveMove() {
        if (movingKey == null || savingOrderNow) return;
        savingOrderNow = true;
        savingLayout = adapter.snapshotLayout();
        final HomeLayout confirmed = savingLayout.copy();
        AppOrderStore.WRITER.execute(() -> {
            boolean saved = layoutStore.write(confirmed);
            handler.post(() -> {
                if (isDestroyed()) return;
                savingOrderNow = false;
                savingLayout = null;
                if (saved) {
                    finishMove(true, !paused);
                } else if (paused) {
                    finishMove(false, false);
                } else {
                    Toast.makeText(this, R.string.app_order_failed, Toast.LENGTH_LONG).show();
                }
                if (refreshPending) {
                    refreshPending = false;
                    if (movingKey != null) finishMove(false, false);
                    String key = lastFocusedKey;
                    int slot = lastFocused;
                    adapter.reload();
                    syncFolderPresentation();
                    if (!paused) focusKey(key, slot);
                }
            });
        });
    }

    private void finishMove(boolean saved, boolean restoreFocus) {
        String key = movingKey;
        int fallback = adapter.positionOf(key);
        if (!saved && moveSnapshot != null) adapter.applyOrder(moveSnapshot);
        movingKey = null;
        moveAnimationRequest++;
        moveSnapshot = null;
        confirmDown = false;
        adapter.setMovingKey(null);
        moveIndicator.setVisibility(View.GONE);
        // Never leave a translation on a recycled item after cancelling mid-animation.
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            child.animate().cancel();
            child.setTranslationX(0f);
            child.setTranslationY(0f);
        }
        if (restoreFocus) {
            focusKey(key, Math.max(0, fallback));
            Toast.makeText(this, saved ? R.string.app_order_saved : R.string.app_order_cancelled,
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        dismissTileMenu(false);
        if (editDialog != null) editDialog.dismiss();
        if (movingKey != null && !savingOrderNow) finishMove(false, false);
        if (!savingOrderNow) returnHome();
    }

    // ---- top bar ---------------------------------------------------------------------------------

    private void updateGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int res;
        if (hour < 5) res = R.string.good_night;
        else if (hour < 12) res = R.string.good_morning;
        else if (hour < 17) res = R.string.good_afternoon;
        else if (hour < 22) res = R.string.good_evening;
        else res = R.string.good_night;
        greeting.setText(res);
        if (shamsi) shamsiDate.setText(Shamsi.today());
    }

    /** Shows the Wi-Fi network name on the button when we can read it, otherwise the connection type. */
    private void updateWifiLabel() {
        String text = getString(R.string.wifi);
        boolean vpn = false;
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            boolean online = false, wifi = false, ethernet = false;
            // All networks, not just the active one: with a VPN up the active network is the VPN itself.
            for (Network net : cm != null ? cm.getAllNetworks() : new Network[0]) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(net);
                if (caps == null) continue;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    vpn = true;
                    continue;
                }
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;
                online = true;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) ethernet = true;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) wifi = true;
            }
            if (!online) {
                text = getString(R.string.offline);
                vpn = false;
            } else if (ethernet) {
                text = getString(R.string.ethernet);
            } else if (wifi) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
                String ssid = info != null ? info.getSSID() : null;
                if (ssid != null) {
                    ssid = ssid.replace("\"", "").trim();
                    if (!ssid.isEmpty() && !ssid.contains("unknown") && !ssid.equals("0x")) text = ssid;
                }
            }
        } catch (Exception ignored) {
        }
        if (vpn) {
            // Network name, then a green lock-and-VPN capsule drawn in place of the word "VPN".
            String badge = getString(R.string.vpn);
            SpannableString label = new SpannableString(text + " " + badge);
            label.setSpan(new VpnBadge(getDrawable(R.drawable.ic_lock), density),
                    text.length() + 1, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            btnWifi.setText(label);
        } else {
            btnWifi.setText(text);
        }
    }

    /** The network pill opens the VPN app when one is configured and installed, otherwise Wi-Fi settings. */
    private void openNetworkPill() {
        Intent i = vpnApp.isEmpty() ? null : getPackageManager().getLeanbackLaunchIntentForPackage(vpnApp);
        if (i == null && !vpnApp.isEmpty()) i = getPackageManager().getLaunchIntentForPackage(vpnApp);
        if (i != null) open(i);
        else openSettings(Settings.ACTION_WIFI_SETTINGS);
    }

    private void openSettings(String action) {
        // Settings shares one task across its panels. NEW_TASK alone can bring its previous
        // submenu back to the front instead of opening the screen the user requested.
        open(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
    }

    /** Hides the Inputs pill when it would have nothing to list: streaming boxes have no HDMI-in, AV or tuner. */
    private void updateInputsButton() {
        boolean any = liveTvIntent() != null || !sourcePanelService.isEmpty();
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (!any && tim != null) {
            for (TvInputInfo info : tim.getTvInputList()) {
                if (info.isPassthroughInput()) {
                    any = true;
                    break;
                }
            }
        }
        btnInputs.setVisibility(any ? View.VISIBLE : View.GONE);
    }

    /** The Live TV app's launch intent: its leanback entry point, or the configured action if it has none. */
    private Intent liveTvIntent() {
        if (liveTvPkg.isEmpty()) return null;
        Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(liveTvPkg);
        if (i != null || liveTvAction.isEmpty()) return i;
        Intent byAction = new Intent(liveTvAction).setPackage(liveTvPkg);
        return getPackageManager().resolveActivity(byAction, 0) != null ? byAction : null;
    }

    /** The TV's own source-selection panel, for TVs that have no TvInputManager (TCL Android TV 9). */
    private void openSourcePanel() {
        Intent i = new Intent(sourcePanelAction);
        ComponentName c = ComponentName.unflattenFromString(sourcePanelService);
        if (c != null) i.setComponent(c);
        try {
            startService(i);
        } catch (Exception e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    private void open(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    /** Dropdown menu under the Inputs pill: HDMI ports (with connection state), AV and Live TV. */
    private void showInputs() {
        View anchor = findViewById(R.id.btnInputs);
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);

        // Compact: as wide as the Inputs pill (a touch wider so "HDMI 1" fits), hanging right below it.
        int width = Math.max(anchor.getWidth(), (int) (150 * density));
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));   // needed for Back to dismiss
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);

        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        boolean sawNonHdmi = false;
        String tunerId = null;
        boolean tunerIsHw = false;
        if (tim != null) {
            List<TvInputInfo> inputs = new ArrayList<>(tim.getTvInputList());
            // HDMI ports first in port order, then AV and the rest.
            Collections.sort(inputs, (a, b) -> {
                boolean ha = a.getType() == TvInputInfo.TYPE_HDMI, hb = b.getType() == TvInputInfo.TYPE_HDMI;
                if (ha != hb) return ha ? -1 : 1;
                return String.valueOf(a.loadLabel(this)).compareToIgnoreCase(String.valueOf(b.loadLabel(this)));
            });
            // Some TVs (Sony) report every HDMI port as connected whether or not anything is plugged in,
            // so drop the HDMI status then. A TV with every port really in use loses it too.
            int hdmiPorts = 0, hdmiConnected = 0;
            for (TvInputInfo info : inputs) {
                if (!info.isPassthroughInput() || info.getType() != TvInputInfo.TYPE_HDMI) continue;
                hdmiPorts++;
                if (tim.getInputState(info.getId()) == TvInputManager.INPUT_STATE_CONNECTED) hdmiConnected++;
            }
            boolean hdmiStateBogus = hdmiPorts > 1 && hdmiConnected == hdmiPorts;
            for (TvInputInfo info : inputs) {
                if (info.getType() == TvInputInfo.TYPE_TUNER
                        && (info.getServiceInfo().applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                    // Prefer a hardware tuner: the framework names those ".../HW<n>". System apps such as
                    // AirPlay also register software inputs of type TUNER and may sort first (seen on TCL G05).
                    boolean hw = info.getId().matches(".*/HW\\d+$");
                    if (tunerId == null || (hw && !tunerIsHw) || (hw == tunerIsHw && info.getId().compareTo(tunerId) < 0)) {
                        tunerId = info.getId();
                        tunerIsHw = hw;
                    }
                }
                if (!info.isPassthroughInput()) continue;
                CharSequence l = info.loadLabel(this);
                String label = l == null ? info.getId() : l.toString();
                boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
                if (hdmi && !label.toUpperCase(Locale.ROOT).contains("HDMI")) label = "HDMI " + label;
                final String id = info.getId();
                int state = hdmi && hdmiStateBogus ? -1 : tim.getInputState(id);
                if (!hdmi && rows.getChildCount() > 0 && !sawNonHdmi) addSeparator(rows);
                if (!hdmi) sawNonHdmi = true;
                addInputRow(rows, popup, hdmi ? R.drawable.ic_input : R.drawable.ic_av, label, state,
                        () -> open(new Intent(Intent.ACTION_VIEW).setDataAndType(
                                TvContract.buildChannelUriForPassthroughInput(id), "vnd.android.cursor.item/channel")));
            }
        }
        // Nothing to list where there is no TvInputManager: the TV's own source panel switches inputs.
        if (rows.getChildCount() == 0 && !sourcePanelService.isEmpty()) {
            addInputRow(rows, popup, R.drawable.ic_input, getString(R.string.inputs), -1, this::openSourcePanel);
        }
        Intent liveTv = liveTvIntent();
        if (liveTv != null && tunerId != null) {
            liveTv = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(TvContract.buildChannelUriForPassthroughInput(tunerId), "vnd.android.cursor.item/channel")
                    .setPackage(liveTvPkg);
        }
        if (liveTv != null) {
            if (rows.getChildCount() > 0) addSeparator(rows);
            final Intent liveTvIntent = liveTv;
            addInputRow(rows, popup, R.drawable.ic_livetv, getString(R.string.live_tv), -1, () -> open(liveTvIntent));
        }
        if (rows.getChildCount() == 0) {
            Toast.makeText(this, R.string.no_inputs, Toast.LENGTH_SHORT).show();
            return;
        }

        // Fit the widest row: TVs that report a status on every port (Sony) would otherwise squeeze the names out.
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        popup.setWidth(Math.max(width, panel.getMeasuredWidth()));

        // Drop in: fade and unfold from the top edge.
        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * density);
        popup.showAsDropDown(anchor, 0, (int) (10 * density), Gravity.START);
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
        rows.getChildAt(0).requestFocus();
    }

    private void addSeparator(LinearLayout rows) {
        View line = new View(this);
        line.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) density);
        int m = (int) (6 * density), h = (int) (12 * density);
        lp.setMargins(h, m, h, m);
        rows.addView(line, lp);
    }

    private void addInputRow(LinearLayout rows, PopupWindow popup, int iconRes, String title, int state, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        TextView status = row.findViewById(R.id.status);
        View dot = row.findViewById(R.id.dot);
        // This TV reports "standby" for every port whether or not something is plugged in, so only
        // definite states are shown.
        if (state == TvInputManager.INPUT_STATE_CONNECTED) {
            status.setText(R.string.connected);
            dot.setVisibility(View.VISIBLE);
        } else if (state == TvInputManager.INPUT_STATE_DISCONNECTED) {
            status.setText(R.string.not_connected);
            dot.setVisibility(View.GONE);
        } else {
            status.setVisibility(View.GONE);
            dot.setVisibility(View.GONE);
        }
        row.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        rows.addView(row);
    }
}
