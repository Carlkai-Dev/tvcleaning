package com.gathin.tvhome;

import android.app.Activity;
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
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * tvOS style home: black, a pinned dock row, an app grid, and one glass highlight that springs between tiles.
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
    private View btnInputs;
    private TextView btnWifi;
    private TextView shamsiDate;
    private boolean shamsi;
    private String vpnApp;
    private SpringAnimation springX, springY;
    private float glassTargetX, glassTargetY;
    // How far the grid has scrolled the glass. springY works without it, in grid content space, so a
    // scroll moves the glass in lockstep with its tile instead of becoming a moving spring target.
    private float glassScrollShift;
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
    private List<String> savingOrder;
    private TextView moveHint;
    private AppOrderStore orderStore;
    private boolean savingOrderNow, paused = true, refreshPending;
    private int focusRequest;
    private boolean confirmDown;
    private int normalGridBottomPadding;
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
        grid.setLayoutManager(new GridLayoutManager(this, COLUMNS));
        grid.setHasFixedSize(true);
        grid.setItemAnimator(null);
        grid.addItemDecoration(new DockDecoration(COLUMNS, density));
        grid.setLayoutAnimation(new LayoutAnimationController(
                AnimationUtils.loadAnimation(this, R.anim.tile_enter), 0.06f));
        adapter = new AppAdapter(this, this, COLUMNS, cfg);
        grid.setAdapter(adapter);
        normalGridBottomPadding = grid.getPaddingBottom();
        orderStore = new AppOrderStore(this);
        moveHint = new TextView(this);
        moveHint.setTextColor(Color.WHITE);
        moveHint.setTextSize(15);
        moveHint.setTypeface(getResources().getFont(R.font.outfit));
        moveHint.setFontVariationSettings("'wght' 500");
        moveHint.setGravity(Gravity.CENTER);
        moveHint.setPadding(dp(18), dp(10), dp(18), dp(10));
        moveHint.setBackgroundResource(R.drawable.glass_panel);
        moveHint.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        moveHint.setVisibility(View.GONE);
        FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hintParams.setMargins(dp(24), 0, dp(24), dp(8));
        root.addView(moveHint, hintParams);
        IntentFilter packages = new IntentFilter();
        packages.addAction(Intent.ACTION_PACKAGE_ADDED);
        packages.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packages.addAction(Intent.ACTION_PACKAGE_CHANGED);
        packages.addDataScheme("package");
        registerReceiver(packageChanges, packages);

        // tvOS layout: push the dock row towards the bottom of the screen, leaving room for
        // rows_below_dock grid rows under it; the rest of the grid stays below until you press Down.
        int rowsBelowDock = cfg.rowsBelowDock;
        grid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int h = b - t;
            // Item height follows the tile size, which follows the column count (AppAdapter.tileWidthPx).
            int inner = (r - l) - grid.getPaddingLeft() - grid.getPaddingRight();
            int itemH = AppAdapter.itemHeightPx(AppAdapter.tileHeightPx(AppAdapter.tileWidthPx(inner, COLUMNS, density)), density);
            int dockRow = itemH + (int) ((6 + 12) * density);   // item height plus DockDecoration offsets
            int gridRows = itemH * rowsBelowDock;
            int want = Math.max(0, h - dockRow - gridRows - (int) (30 * density));
            if (grid.getPaddingTop() != want) {
                grid.setPadding(grid.getPaddingLeft(), want, grid.getPaddingRight(), grid.getPaddingBottom());
            }
        });

        // Keep the glass glued to its tile while the grid scrolls.
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (!glassShown) return;
                glassTargetY -= dy;
                glassScrollShift -= dy;
                glass.setTranslationY(glass.getTranslationY() - dy);
            }
        });

        // Liquid feel: soft springs on the highlight's position.
        springX = new SpringAnimation(glass, DynamicAnimation.TRANSLATION_X);
        springY = new SpringAnimation(glass, new FloatPropertyCompat<View>("glassContentY") {
            @Override
            public float getValue(View v) {
                return v.getTranslationY() - glassScrollShift;
            }

            @Override
            public void setValue(View v, float value) {
                v.setTranslationY(value + glassScrollShift);
            }
        });
        for (SpringAnimation s : new SpringAnimation[]{springX, springY}) {
            s.setSpring(new SpringForce().setStiffness(260f).setDampingRatio(0.58f));
        }

        btnInputs = findViewById(R.id.btnInputs);
        View btnSettings = findViewById(R.id.btnSettings);
        btnInputs.setOnClickListener(v -> showInputs());
        btnWifi.setOnClickListener(v -> openNetworkPill());
        btnWifi.setOnLongClickListener(v -> {
            open(new Intent(Settings.ACTION_WIFI_SETTINGS));
            return true;
        });
        btnSettings.setOnClickListener(v -> open(new Intent(Settings.ACTION_SETTINGS)));

        // Keep focus on the top bar at its ends and at the top: Android's focus finder would otherwise
        // jump to a tile far below when there is nothing in that direction.
        View.OnKeyListener pillKeys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                // The left end is Inputs, or Wi-Fi on a box without inputs.
                return v.getId() == R.id.btnInputs
                        || (v.getId() == R.id.btnWifi && btnInputs.getVisibility() != View.VISIBLE);
            }
            return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v.getId() == R.id.btnSettings;
        };
        btnInputs.setOnKeyListener(pillKeys);
        btnWifi.setOnKeyListener(pillKeys);
        btnSettings.setOnKeyListener(pillKeys);

        // Hide the glass when focus leaves the tiles.
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((oldF, newF) -> {
            boolean onTile = newF != null && newF.getId() == R.id.card;
            if (!onTile) hideGlass();
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
        if (savingOrderNow && savingOrder != null) adapter.applyOrder(savingOrder);
        grid.scheduleLayoutAnimation();
        updateGreeting();
        updateWifiLabel();
        updateInputsButton();
        handler.postDelayed(minuteTick, MINUTE_MS);

        // Return to the app we left from, or the same slot if that app is gone. Always refocus: after
        // reload() the view that holds focus may no longer show the app it had.
        grid.post(() -> {
            int pos = adapter.positionOf(returnKey);
            focusTile(pos >= 0 ? pos : returnSlot);
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        paused = true;
        focusRequest++;
        dismissTileMenu(false);
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
        // Outside editing, Home's Back still goes nowhere.
    }

    // ---- glass highlight -------------------------------------------------------------------------

    @Override
    public void onTileFocused(int position, View card) {
        lastFocused = position;
        lastFocusedKey = adapter.keyAt(position);
        // Tile rect in root coordinates, using layout positions (ignores the scale animation).
        Rect r = new Rect(0, 0, card.getWidth(), card.getHeight());
        root.offsetDescendantRectToMyCoords(card, r);
        float pad = 6f * density;        // matches GlassView's glow inset
        float w = card.getWidth() * AppAdapter.FOCUS_SCALE + 2 * pad;
        float h = card.getHeight() * AppAdapter.FOCUS_SCALE + 2 * pad;
        float x = r.exactCenterX() - w / 2f;
        float y = r.exactCenterY() - h / 2f;

        ViewGroup.LayoutParams lp = glass.getLayoutParams();
        if (lp.width != (int) w || lp.height != (int) h) {
            lp.width = (int) w;
            lp.height = (int) h;
            glass.setLayoutParams(lp);
            glass.setRadius(AppAdapter.cornerRadius(card.getWidth(), density) * AppAdapter.FOCUS_SCALE + pad);
        }
        glassTargetX = x;
        glassTargetY = y;
        if (!glassShown) {
            springX.cancel();
            springY.cancel();
            glass.setTranslationX(x);
            glass.setTranslationY(y);
            glass.animate().alpha(1f).setDuration(180).start();
            glassShown = true;
        } else {
            springX.animateToFinalPosition(x);
            springY.animateToFinalPosition(y - glassScrollShift);
        }
        View item = (View) card.getParent();
        if (position < COLUMNS) {
            // Dock: park it at the bottom of the screen and show the top shelf for this app.
            int decoratedTop = item.getTop() - (int) (6 * density);
            int dy = decoratedTop - grid.getPaddingTop();
            if (Math.abs(dy) > 1) grid.smoothScrollBy(0, dy);
            showHero(position, r.top - dy);
        } else {
            hideHero();
            // Scroll just enough so the whole glass (not only the tile) is visible.
            Rect want = new Rect(0, -(int) pad * 2, item.getWidth(), item.getHeight() + (int) pad);
            grid.requestChildRectangleOnScreen(item, want, false);
        }
    }

    // ---- top shelf -------------------------------------------------------------------------------

    private void showHero(int position, float dockTopInRoot) {
        String label = adapter.labelAt(position);
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
        int count = adapter.getItemCount();
        int target;
        // Horizontal: swallow the key at a row's ends so focus never wraps to the next row or
        // escapes up into the top bar. Mid-row moves fall through to the default focus search.
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            return position % COLUMNS == 0;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            return position % COLUMNS == COLUMNS - 1 || position == count - 1;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (position < COLUMNS) return false;          // dock row: let focus move to the top bar
            target = position - COLUMNS;
        } else {
            int lastRowStart = ((count - 1) / COLUMNS) * COLUMNS;
            if (position >= lastRowStart) return true;     // already on the last row: stay put
            target = Math.min(position + COLUMNS, count - 1);
        }
        focusTile(target);
        return true;
    }

    private void focusTile(int position) {
        if (adapter.getItemCount() == 0 || paused) return;
        int pos = Math.max(0, Math.min(position, adapter.getItemCount() - 1));
        String key = adapter.keyAt(pos);
        int request = ++focusRequest;
        RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(pos);
        if (holder == null) grid.scrollToPosition(pos);
        requestTileFocus(key, request, 0);
    }

    private void requestTileFocus(String key, int request, int attempt) {
        if (paused || request != focusRequest || tileMenu != null) return;
        int pos = adapter.positionOf(key);
        if (pos < 0) return;
        RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(pos);
        if (holder != null && !grid.isComputingLayout() && !grid.hasPendingAdapterUpdates()) {
            View card = holder.itemView.findViewById(R.id.card);
            card.requestFocus();
            // Moves/reloads need new geometry even when focus stayed on the same view.
            onTileFocused(pos, card);
        } else if (attempt < 12) {
            grid.postOnAnimation(() -> requestTileFocus(key, request, attempt + 1));
        }
    }

    private void focusKey(String key, int fallback) {
        grid.post(() -> {
            if (paused || tileMenu != null) return;
            int pos = adapter.positionOf(key);
            focusTile(pos >= 0 ? pos : fallback);
        });
    }

    // ---- app actions and remote reordering -------------------------------------------------------

    private int dp(float value) { return Math.round(value * density); }

    private static boolean isConfirm(int key) {
        return key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER
                || key == KeyEvent.KEYCODE_NUMPAD_ENTER || key == KeyEvent.KEYCODE_BUTTON_A;
    }

    @Override
    public boolean onTileClicked() {
        if (movingKey != null) { saveMove(); return true; }
        return tileMenu != null;
    }

    @android.annotation.SuppressLint("RtlHardcoded")
    @Override
    public void onTileLongPressed(int position, View card) {
        if (movingKey != null || tileMenu != null || paused) return;
        String key = adapter.keyAt(position);
        if (key == null) return;
        final int fallback = position;
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(8), dp(8), dp(8), dp(8));
        panel.setBackgroundResource(R.drawable.glass_panel);
        TextView title = new TextView(this);
        title.setText(adapter.labelAt(position));
        title.setTextSize(13);
        title.setTextColor(0xFFCCCCD4);
        title.setTypeface(getResources().getFont(R.font.outfit));
        title.setFontVariationSettings("'wght' 400");
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setPadding(dp(12), dp(4), dp(12), dp(8));
        panel.addView(title);
        PopupWindow popup = new PopupWindow(panel, dp(240), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setElevation(dp(16));
        popup.setOutsideTouchable(true);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        tileMenu = popup;
        restoreMenuFocus = true;
        menuReleaseGuard = true;
        TextView move = addAppAction(panel, R.string.move_app, R.drawable.ic_move, () -> {
            dismissTileMenu(false);
            beginMove(key);
        });
        if (adapter.isApp(position)) {
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
        boolean right = rightSpace >= dp(240) || (leftSpace < dp(240) && rightSpace >= leftSpace);
        int width = Math.min(dp(240), Math.max(1, right ? rightSpace : leftSpace));
        panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(root.getHeight() - inset * 2, View.MeasureSpec.AT_MOST));
        int height = panel.getMeasuredHeight();
        popup.setWidth(width);
        popup.setHeight(height);
        int x = right ? tile.right + gap : tile.left - gap - width;
        int y = Math.max(screen[1] + inset, Math.min(tile.centerY() - height / 2,
                screen[1] + root.getHeight() - inset - height));
        adapter.setMenuKey(key);
        popup.showAtLocation(root, Gravity.TOP | Gravity.LEFT,
                x - screen[0] + window[0], y - screen[1] + window[1]);
        panel.setAlpha(0f);
        panel.setTranslationX(dp(right ? -6 : 6));
        panel.animate().alpha(1f).translationX(0f).setDuration(160).start();
        move.requestFocus();
    }

    private TextView addAppAction(LinearLayout panel, int title, int icon, Runnable action) {
        TextView row = new TextView(this);
        row.setText(title);
        row.setTextSize(17);
        row.setTextColor(getResources().getColorStateList(R.color.app_action_text, getTheme()));
        row.setTypeface(getResources().getFont(R.font.outfit));
        row.setFontVariationSettings("'wght' 500");
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinHeight(dp(52));
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
        row.setCompoundDrawablePadding(dp(10));
        row.setCompoundDrawableTintList(getResources().getColorStateList(R.color.app_action_text, getTheme()));
        row.setBackgroundResource(R.drawable.input_row_bg);
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
        // Reserve room for the two-line remote hint so grid labels can scroll above it.
        grid.setPadding(grid.getPaddingLeft(), grid.getPaddingTop(), grid.getPaddingRight(),
                Math.max(normalGridBottomPadding, dp(100)));
        movingKey = key;
        confirmDown = false;
        adapter.setMovingKey(key);
        updateMoveHint();
        moveHint.setVisibility(View.VISIBLE);
        focusKey(key, 0);
    }

    private void updateMoveHint() {
        int pos = adapter.positionOf(movingKey);
        if (pos < 0) return;
        moveHint.setText(savingOrderNow ? getString(R.string.saving_app_order)
                : getString(R.string.move_app_hint, adapter.labelAt(pos), pos + 1,
                adapter.getItemCount(), getString(pos < COLUMNS ? R.string.app_dock : R.string.app_grid)));
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (movingKey == null) return super.dispatchKeyEvent(event);
        int code = event.getKeyCode();
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
            int to = AppOrder.target(from, adapter.getItemCount(), COLUMNS, direction);
            if (to != from) {
                animateMove(from, to);
                updateMoveHint();
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
        savingOrder = adapter.keys();
        final List<String> confirmed = new ArrayList<>(savingOrder);
        updateMoveHint();
        AppOrderStore.WRITER.execute(() -> {
            boolean saved = orderStore.write(confirmed);
            handler.post(() -> {
                if (isDestroyed()) return;
                savingOrderNow = false;
                savingOrder = null;
                if (saved) {
                    finishMove(true, !paused);
                } else if (paused) {
                    finishMove(false, false);
                } else {
                    updateMoveHint();
                    Toast.makeText(this, R.string.app_order_failed, Toast.LENGTH_LONG).show();
                }
                if (refreshPending) {
                    refreshPending = false;
                    if (movingKey != null) finishMove(false, false);
                    String key = lastFocusedKey;
                    int slot = lastFocused;
                    adapter.reload();
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
        moveHint.setVisibility(View.GONE);
        grid.setPadding(grid.getPaddingLeft(), grid.getPaddingTop(), grid.getPaddingRight(), normalGridBottomPadding);
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
        dismissTileMenu(true);
        if (movingKey != null && !savingOrderNow) finishMove(false, true);
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
        open(i != null ? i : new Intent(Settings.ACTION_WIFI_SETTINGS));
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
