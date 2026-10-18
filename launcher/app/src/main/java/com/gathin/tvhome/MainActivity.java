package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
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
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * tvOS style home with a dock, app grid and highlights attached to their cards.
 */
public class MainActivity extends Activity implements AppAdapter.TileListener {

    /** Tiles per row and dock slots (res/values/config.xml, columns). Set in onCreate. */
    static int COLUMNS = 6;
    private static final long MINUTE_MS = 60_000L;
    private String liveTvPkg;

    private AppAdapter adapter;
    private RecyclerView grid;
    private FrameLayout root;
    private View ambient;
    private TextView heroName;
    private GradientDrawable ambientGlow;
    private int ambientColor;
    private boolean heroShown;
    private final Rect heroAnchor = new Rect();
    private final Rect heroInk = new Rect();
    private TextView greeting;
    private TopButton btnWifi;
    private HeaderDropdown inputDropdown;
    private WifiDropdown wifiDropdown;
    private boolean pendingWifiPermissionResult;
    // Static so they survive the activity being destroyed while another app is open (low-memory TVs,
    // or Developer options → "Don't keep activities"); they only reset when the process dies.
    private static int lastFocused = 0;
    /** App of the last focused tile, so focus follows it when the app list changes. */
    private static String lastFocusedKey;
    private float density;
    private boolean moving;
    private boolean ignoreMoveRelease;
    private int movingPosition;
    private String movingKey;
    private TextView moveHint;
    private AppMenuView appMenu;
    private int settingsRightPresses;
    private PopupWindow hiddenPopup;
    private int lastDockPosition;
    private int pendingFocusPosition = RecyclerView.NO_POSITION;
    private boolean homeShown;
    private final DecelerateInterpolator navigationEase = new DecelerateInterpolator(1.5f);

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
        COLUMNS = Math.max(1, getResources().getInteger(R.integer.columns));
        liveTvPkg = getString(R.string.live_tv_package).trim();

        root = findViewById(R.id.root);
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

        grid = findViewById(R.id.grid);
        grid.setLayoutManager(new GridLayoutManager(this, COLUMNS));
        grid.setHasFixedSize(true);
        grid.setItemAnimator(null);
        grid.addItemDecoration(new DockDecoration(COLUMNS, density));
        grid.setLayoutAnimation(new LayoutAnimationController(
                AnimationUtils.loadAnimation(this, R.anim.tile_enter), 0.06f));
        adapter = new AppAdapter(this, this, COLUMNS);
        grid.setAdapter(adapter);
        // One listener owns pending focus, including repeated presses while a row is scrolling in.
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrollStateChanged(@NonNull RecyclerView rv, int state) {
                if (state == RecyclerView.SCROLL_STATE_IDLE && pendingFocusPosition != RecyclerView.NO_POSITION)
                    focusTile(pendingFocusPosition);
            }
        });
        moveHint = new TextView(this);
        moveHint.setTextColor(Color.WHITE);
        moveHint.setTextSize(16);
        moveHint.setGravity(Gravity.CENTER);
        moveHint.setPadding((int) (16 * density), (int) (12 * density),
                (int) (16 * density), (int) (12 * density));
        GradientDrawable hintBackground = new GradientDrawable();
        hintBackground.setColor(0xF0202020);
        hintBackground.setCornerRadius(16 * density);
        hintBackground.setStroke((int) (2 * density), 0xFFFFCA63);
        moveHint.setBackground(hintBackground);
        moveHint.setVisibility(View.GONE);
        FrameLayout.LayoutParams hintLayout = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        hintLayout.setMargins((int) (56 * density), (int) (125 * density),
                (int) (56 * density), 0);
        root.addView(moveHint, hintLayout);

        // tvOS layout: push the dock row to the bottom of the screen; the grid hides below until you press Down.
        grid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int h = b - t;
            int dockRow = getResources().getDimensionPixelSize(R.dimen.tile_row_height) + (int) (18 * density);
            int want = Math.max(0, h - dockRow - (int) (18 * density));
            if (grid.getPaddingTop() != want) {
                grid.setPadding(grid.getPaddingLeft(), want, grid.getPaddingRight(), grid.getPaddingBottom());
            }
            if (heroShown) positionHeroName();
            if (pendingFocusPosition != RecyclerView.NO_POSITION
                    && grid.findViewHolderForAdapterPosition(pendingFocusPosition) != null)
                grid.post(() -> {
                    if (pendingFocusPosition != RecyclerView.NO_POSITION) focusTile(pendingFocusPosition);
                });
        });

        View btnInputs = findViewById(R.id.btnInputs);
        View btnSettings = findViewById(R.id.btnSettings);
        btnInputs.setOnClickListener(v -> showInputs());
        wifiDropdown = new WifiDropdown(this, root, btnWifi);
        btnWifi.setOnClickListener(v -> wifiDropdown.show());
        btnSettings.setOnClickListener(v -> open(new Intent(Settings.ACTION_SETTINGS)));
        btnSettings.setOnFocusChangeListener((v, focused) -> settingsRightPresses = 0);
        btnSettings.setContentDescription(getString(R.string.settings_hidden_hint));

        // Keep focus on the top bar at its ends and at the top: Android's focus finder would otherwise
        // jump to a tile far below when there is nothing in that direction.
        View.OnKeyListener pillKeys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (v.getId() == R.id.btnSettings && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                // Count separate presses, not the repeat events from holding Right.
                if (event.getRepeatCount() == 0 && ++settingsRightPresses == 5) {
                    settingsRightPresses = 0;
                    showHiddenApps();
                }
                return true;
            }
            settingsRightPresses = 0;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                focusTile(Math.min(lastDockPosition, COLUMNS - 1));
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && v.getId() == R.id.btnInputs) return true;
            return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v.getId() == R.id.btnSettings;
        };
        btnInputs.setOnKeyListener(pillKeys);
        btnWifi.setOnKeyListener(pillKeys);
        btnSettings.setOnKeyListener(pillKeys);

        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm != null) cm.registerDefaultNetworkCallback(networkCallback);
    }

    @Override
    protected void onResume() {
        super.onResume();
        heroShown = false;
        heroName.setAlpha(0f);
        ambient.setAlpha(0f);
        // Read these before reload(): rebinding can move focus to the first tile and overwrite them.
        final String returnKey = lastFocusedKey;
        final int returnSlot = lastFocused;
        adapter.reload();
        if (!homeShown) grid.scheduleLayoutAnimation();
        homeShown = true;
        updateGreeting();
        updateWifiLabel();
        handler.postDelayed(minuteTick, MINUTE_MS);

        // Return to the app we left from, or the same slot if that app is gone. Always refocus: after
        // reload() the view that holds focus may no longer show the app it had.
        grid.post(() -> {
            int pos = adapter.positionOf(returnKey);
            focusTile(pos >= 0 ? pos : returnSlot);
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (!intent.hasCategory(Intent.CATEGORY_HOME)) return;
        if (inputDropdown != null) inputDropdown.window.dismiss();
        if (wifiDropdown != null) wifiDropdown.dismiss();
        if (hiddenPopup != null) hiddenPopup.dismiss();
        grid.post(() -> focusTile(lastDockPosition));
    }

    @Override
    protected void onPause() {
        settingsRightPresses = 0;
        pendingFocusPosition = RecyclerView.NO_POSITION;
        grid.stopScroll();
        adapter.updateDockSpacing(grid, RecyclerView.NO_POSITION, false);
        if (hiddenPopup != null) hiddenPopup.dismiss();
        if (inputDropdown != null) inputDropdown.window.dismiss();
        if (wifiDropdown != null) wifiDropdown.dismiss();
        dismissAppMenu(null);
        // Leaving Home never commits an unfinished rearrangement.
        if (moving) finishMoving(false);
        super.onPause();
        handler.removeCallbacks(minuteTick);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm != null) cm.unregisterNetworkCallback(networkCallback);
    }

    @Override
    public void onBackPressed() {
        if (moving) finishMoving(false);
        // Home screen: Back goes nowhere.
    }

    @Override
    public void onTileMenuRequested(int position, View card) {
        if (moving || appMenu != null) return;
        AppAdapter.Entry entry = adapter.entryAt(position);
        if (entry == null) return;
        String key = adapter.keyAt(position);
        Rect tile = new Rect(0, 0, card.getWidth(), card.getHeight());
        root.offsetDescendantRectToMyCoords(card, tile);
        grid.stopScroll();
        appMenu = new AppMenuView(this, entry, tile, card.getScaleX(), new AppMenuView.Listener() {
            @Override public void onDismiss() { dismissAppMenu(key); }
            @Override public void onAction(int action) {
                dismissAppMenu(key);
                int current = adapter.positionOf(key);
                if (action == AppMenuView.MOVE && current >= 0) beginMoving(current);
                else if (action == AppMenuView.UNINSTALL) requestUninstall(entry);
                else if (action == AppMenuView.HIDE) {
                    if (adapter.setHidden(entry.pkg, true)) {
                        refreshApps(null, position);
                        Toast.makeText(MainActivity.this, R.string.app_hidden_hint, Toast.LENGTH_LONG).show();
                    } else Toast.makeText(MainActivity.this, R.string.move_save_failed, Toast.LENGTH_LONG).show();
                }
            }
        });
        root.addView(appMenu, new FrameLayout.LayoutParams(-1, -1));
        appMenu.requestFocus();
        card.animate().scaleX(AppAdapter.FOCUS_SCALE).scaleY(AppAdapter.FOCUS_SCALE).setDuration(180).start();
    }

    private void dismissAppMenu(String focusKey) {
        if (appMenu == null) return;
        root.removeView(appMenu);
        appMenu = null;
        if (focusKey != null) grid.post(() -> focusTile(adapter.positionOf(focusKey)));
    }

    private void refreshApps(String key, int fallback) {
        adapter.reload();
        grid.post(() -> {
            int pos = adapter.positionOf(key);
            focusTile(pos >= 0 ? pos : fallback);
        });
    }

    private void requestUninstall(AppAdapter.Entry entry) {
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(entry.pkg, 0);
            if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                Toast.makeText(this, R.string.system_app_hide_instead, Toast.LENGTH_LONG).show();
                return;
            }
            // Android owns the confirmation and performs deletion only if the user accepts it.
            startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + entry.pkg)));
        } catch (PackageManager.NameNotFoundException | ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.uninstall_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    private void showHiddenApps() {
        if (hiddenPopup != null && hiddenPopup.isShowing()) return;
        List<String> packages = new ArrayList<>(adapter.userHiddenPackages());
        Collections.sort(packages, (a, b) -> hiddenAppLabel(a).compareToIgnoreCase(hiddenAppLabel(b)));
        if (packages.isEmpty()) {
            Toast.makeText(this, R.string.no_hidden_apps, Toast.LENGTH_SHORT).show();
            return;
        }
        View anchor = findViewById(R.id.btnSettings);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundResource(R.drawable.glass_panel);
        int inset = (int) (6 * density);
        panel.setPadding(inset, inset, inset, inset);
        TextView heading = new TextView(this);
        heading.setText(R.string.hidden_apps_title);
        heading.setTextSize(12);
        heading.setTextColor(0xFFBDBDC8);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding((int) (12 * density), 0, 0, 0);
        panel.addView(heading, new LinearLayout.LayoutParams(-1, (int) (32 * density)));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows, new ScrollView.LayoutParams(-1, -2));
        Rect anchorRect = new Rect(0, 0, anchor.getWidth(), anchor.getHeight());
        root.offsetDescendantRectToMyCoords(anchor, anchorRect);
        int available = root.getHeight() - anchorRect.bottom - (int) (96 * density);
        int listHeight = Math.min(packages.size() * (int) (44 * density),
                Math.min((int) (264 * density), Math.max((int) (44 * density), available)));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, listHeight));
        TextView hint = new TextView(this);
        hint.setText(R.string.hidden_list_help);
        hint.setTextColor(0xFFBDBDC8);
        hint.setTextSize(10);
        hint.setGravity(Gravity.CENTER);
        panel.addView(hint, new LinearLayout.LayoutParams(-1, (int) (30 * density)));
        final PopupWindow popup = new PopupWindow(panel, (int) (264 * density), -2, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setOnDismissListener(() -> {
            hiddenPopup = null;
            settingsRightPresses = 0;
            anchor.requestFocus();
        });
        for (String pkg : packages) {
            TextView row = new TextView(this);
            row.setText(hiddenAppLabel(pkg));
            row.setTextSize(16);
            row.setTypeface(getResources().getFont(R.font.outfit));
            row.setTextColor(getColorStateList(R.drawable.pill_text));
            row.setBackgroundResource(R.drawable.input_row_bg);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding((int) (12 * density), 0, (int) (12 * density), 0);
            row.setSingleLine(true);
            row.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.setFocusable(true);
            row.setOnClickListener(v -> {
                Intent launch = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
                if (launch == null) launch = getPackageManager().getLaunchIntentForPackage(pkg);
                popup.dismiss();
                if (launch != null) open(launch);
                else Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
            });
            row.setOnLongClickListener(v -> {
                if (adapter.setHidden(pkg, false)) {
                    adapter.reload();
                    popup.dismiss();
                    Toast.makeText(this, R.string.app_shown, Toast.LENGTH_SHORT).show();
                } else Toast.makeText(this, R.string.move_save_failed, Toast.LENGTH_LONG).show();
                return true;
            });
            row.setOnKeyListener((v, code, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                int index = rows.indexOfChild(v);
                if (code == KeyEvent.KEYCODE_DPAD_UP && index == 0) return true;
                if (code == KeyEvent.KEYCODE_DPAD_DOWN && index == rows.getChildCount() - 1) return true;
                return code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT;
            });
            rows.addView(row, new LinearLayout.LayoutParams(-1, (int) (44 * density)));
        }
        hiddenPopup = popup;
        panel.setAlpha(0f);
        popup.showAsDropDown(anchor, 0, (int) (10 * density), Gravity.END);
        panel.animate().alpha(1f).setDuration(160).start();
        rows.getChildAt(0).requestFocus();
    }

    private String hiddenAppLabel(String pkg) {
        try {
            return getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException ignored) { return pkg; }
    }

    private void beginMoving(int position) {
        if (moving) return;
        adapter.beginMove();
        moving = true;
        ignoreMoveRelease = false; // The menu has already consumed the selection key-up.
        movingPosition = position;
        movingKey = adapter.keyAt(position);
        moveHint.setVisibility(View.VISIBLE);
        updateMoveHint();
    }

    private void updateMoveHint() {
        moveHint.setText(getString(R.string.move_help, adapter.labelAt(movingPosition), COLUMNS));
        moveHint.setContentDescription(moveHint.getText());
    }

    private void finishMoving(boolean save) {
        if (!adapter.finishMove(save)) {
            Toast.makeText(this, R.string.move_save_failed, Toast.LENGTH_LONG).show();
            return;
        }
        moving = false;
        moveHint.setVisibility(View.GONE);
        final String key = movingKey;
        grid.post(() -> focusTile(adapter.positionOf(key)));
        Toast.makeText(this, save ? R.string.move_saved : R.string.move_cancelled,
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!moving) return super.dispatchKeyEvent(event);
        int key = event.getKeyCode();
        boolean confirm = key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER
                || key == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (confirm) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                if (ignoreMoveRelease) ignoreMoveRelease = false;
                else finishMoving(true);
            }
            return true;
        }
        if (key == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP) finishMoving(false);
            return true;
        }
        boolean direction = key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT
                || key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN;
        if (!direction) return super.dispatchKeyEvent(event);
        if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
        int target = movingPosition;
        if (key == KeyEvent.KEYCODE_DPAD_LEFT && target % COLUMNS > 0) target--;
        if (key == KeyEvent.KEYCODE_DPAD_RIGHT && target % COLUMNS < COLUMNS - 1) target++;
        if (key == KeyEvent.KEYCODE_DPAD_UP) target -= COLUMNS;
        if (key == KeyEvent.KEYCODE_DPAD_DOWN) target += COLUMNS;
        if (target < 0 || target >= adapter.getItemCount() || target == movingPosition) return true;
        adapter.move(movingPosition, target);
        movingPosition = target;
        final int destination = target;
        grid.post(() -> focusTile(destination));
        updateMoveHint();
        return true;
    }

    // ---- focused card ---------------------------------------------------------------------------

    @Override
    public void onTileFocused(int position, View card) {
        adapter.updateDockSpacing(grid, position, true);
        lastFocused = position;
        lastFocusedKey = adapter.keyAt(position);
        View item = (View) card.getParent();
        if (position < COLUMNS) {
            lastDockPosition = position;
            // Dock: park it at the bottom of the screen and show the top shelf for this app.
            int decoratedTop = item.getTop() - (int) (6 * density);
            int dy = decoratedTop - grid.getPaddingTop();
            if (Math.abs(dy) > 1) grid.smoothScrollBy(0, dy, navigationEase, 200);
            showHero(position);
        } else {
            hideHero();
            // Keep the scaled card visible when moving through the app grid.
            int top = item.getTop() - (int) (4 * density);
            int bottom = item.getBottom() + (int) (8 * density);
            int dy = bottom > grid.getHeight() - grid.getPaddingBottom()
                    ? bottom - (grid.getHeight() - grid.getPaddingBottom())
                    : top < grid.getPaddingTop() ? top - grid.getPaddingTop() : 0;
            if (Math.abs(dy) > 1) grid.smoothScrollBy(0, dy, navigationEase, 200);
        }
    }

    // ---- top shelf -------------------------------------------------------------------------------

    private void positionHeroName() {
        heroAnchor.set(0, 0, grid.getWidth(), grid.getHeight());
        root.offsetDescendantRectToMyCoords(grid, heroAnchor);
        // The parked row has 6 dp of decoration and 8 dp of item padding above its artwork.
        float dockTop = heroAnchor.top + grid.getPaddingTop()
                + (6f + 8f - DockDecoration.VERTICAL_PADDING_DP) * density;
        String text = heroName.getText().toString();
        heroName.getPaint().getTextBounds(text, 0, text.length(), heroInk);
        // Anchor the visible ink, including descenders, 24 dp above the fixed dock edge.
        // Paint metrics are available before the first TextView layout, unlike getHeight().
        float baseline = dockTop - 24f * density - heroInk.bottom;
        heroName.setY(baseline + heroName.getPaint().ascent());
    }

    private void showHero(int position) {
        String label = adapter.labelAt(position);
        int color = adapter.ambientColorAt(position);
        boolean changed = !label.contentEquals(heroName.getText());
        // Replace stale callbacks immediately when the user crosses several icons quickly.
        heroName.animate().cancel();
        ambient.animate().cancel();
        heroName.setText(label);
        heroName.setTranslationX(0f);
        positionHeroName();
        if (!heroShown) {
            heroName.setAlpha(1f);
            setAmbient(color);
            ambient.animate().alpha(1f).setDuration(240).start();
            heroShown = true;
        } else if (changed) {
            heroName.setAlpha(0.7f);
            heroName.animate().alpha(1f).setDuration(150).start();
            if (color != ambientColor) setAmbient(color);
            ambient.animate().alpha(1f).setDuration(180).start();
        } else {
            heroName.animate().alpha(1f).setDuration(150).start();
            ambient.animate().alpha(1f).setDuration(180).start();
        }
    }

    private void setAmbient(int color) {
        ambientColor = color;
        int c = color & 0x00FFFFFF;
        ambientGlow.setColors(new int[]{0x5C000000 | c, 0x26000000 | c, 0x00000000 | c});
    }

    private void hideHero() {
        if (!heroShown) return;
        heroName.animate().cancel();
        ambient.animate().cancel();
        heroShown = false;
        heroName.animate().alpha(0f).setDuration(160).start();
        ambient.animate().alpha(0f).setDuration(300).start();
    }

    // ---- navigation ------------------------------------------------------------------------------

    @Override
    public boolean onTileDirectionKey(int position, int keyCode) {
        int count = adapter.getItemCount();
        if (pendingFocusPosition != RecyclerView.NO_POSITION) position = pendingFocusPosition;
        int target;
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            int column = position % COLUMNS;
            target = position + (keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
            if ((keyCode == KeyEvent.KEYCODE_DPAD_LEFT && column == 0)
                    || (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && column == COLUMNS - 1)
                    || target >= count) return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (position < COLUMNS) {
                pendingFocusPosition = RecyclerView.NO_POSITION;
                adapter.updateDockSpacing(grid, RecyclerView.NO_POSITION, true);
                Rect bounds = new Rect(0, 0, grid.getWidth(), grid.getHeight());
                root.offsetDescendantRectToMyCoords(grid, bounds);
                int contentWidth = grid.getWidth() - grid.getPaddingLeft() - grid.getPaddingRight();
                int center = bounds.left + grid.getPaddingLeft()
                        + (int) ((position + 0.5f) * contentWidth / COLUMNS);
                View nearest = findViewById(R.id.btnInputs);
                int distance = Integer.MAX_VALUE;
                for (int id : new int[]{R.id.btnInputs, R.id.btnWifi, R.id.btnSettings}) {
                    View button = findViewById(id);
                    Rect buttonBounds = new Rect(0, 0, button.getWidth(), button.getHeight());
                    root.offsetDescendantRectToMyCoords(button, buttonBounds);
                    int delta = Math.abs(center - buttonBounds.centerX());
                    if (delta <= distance) { nearest = button; distance = delta; }
                }
                lastDockPosition = position;
                nearest.requestFocus();
                return true;
            }
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
        if (adapter.getItemCount() == 0) return;
        final int pos = Math.max(0, Math.min(position, adapter.getItemCount() - 1));
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
        if (vh != null) {
            pendingFocusPosition = RecyclerView.NO_POSITION;
            View card = vh.itemView.findViewById(R.id.card);
            // Already focused: refresh the title and scroll position when returning from an app.
            if (card.isFocused()) onTileFocused(pos, card);
            else card.requestFocus();
            return;
        }
        // The shared listener will focus the latest requested tile once it is attached.
        pendingFocusPosition = pos;
        grid.smoothScrollToPosition(pos);
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
    }

    /** Shows the Wi-Fi network name on the button when we can read it, otherwise the connection type. */
    private void updateWifiLabel() {
        String text = getString(R.string.wifi);
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            Network net = cm != null ? cm.getActiveNetwork() : null;
            NetworkCapabilities caps = net != null ? cm.getNetworkCapabilities(net) : null;
            if (caps == null) {
                text = getString(R.string.offline);
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                text = getString(R.string.ethernet);
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
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
        btnWifi.setText(text);
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
        if (inputDropdown != null && inputDropdown.window.isShowing()) return;
        View anchor = findViewById(R.id.btnInputs);
        inputDropdown = new HeaderDropdown(this, root, anchor);
        LinearLayout rows = inputDropdown.rows;
        final PopupWindow popup = inputDropdown.window;
        popup.setOnDismissListener(() -> {
            inputDropdown = null;
            anchor.requestFocus();
        });

        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        boolean sawNonHdmi = false;
        String tunerId = null;
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
                        && (info.getServiceInfo().applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                        && (tunerId == null || info.getId().compareTo(tunerId) < 0)) {
                    tunerId = info.getId();
                }
                if (!info.isPassthroughInput()) continue;
                CharSequence l = info.loadLabel(this);
                String label = l == null ? info.getId() : l.toString();
                boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
                if (hdmi && !label.toUpperCase().contains("HDMI")) label = "HDMI " + label;
                final String id = info.getId();
                int state = hdmi && hdmiStateBogus ? -1 : tim.getInputState(id);
                if (!hdmi && rows.getChildCount() > 0 && !sawNonHdmi) addSeparator(rows);
                if (!hdmi) sawNonHdmi = true;
                addInputRow(rows, popup, hdmi ? R.drawable.ic_input : R.drawable.ic_av, label, state,
                        () -> open(new Intent(Intent.ACTION_VIEW).setDataAndType(
                                TvContract.buildChannelUriForPassthroughInput(id), "vnd.android.cursor.item/channel")));
            }
        }
        Intent liveTv = liveTvPkg.isEmpty() ? null : getPackageManager().getLeanbackLaunchIntentForPackage(liveTvPkg);
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

        inputDropdown.show();
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
        status.setFontVariationSettings("'wght' 400");
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
        HeaderDropdown.containNavigation(row);
        rows.addView(row);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == WifiDropdown.LOCATION_REQUEST && wifiDropdown != null) {
            pendingWifiPermissionResult = true;
            showWifiAfterPermission();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && pendingWifiPermissionResult) showWifiAfterPermission();
    }

    private void showWifiAfterPermission() {
        // Wait for the permission window to close and the normal resume/focus work to finish.
        root.post(() -> {
            if (pendingWifiPermissionResult && hasWindowFocus() && !isFinishing()) {
                pendingWifiPermissionResult = false;
                btnWifi.requestFocus();
                wifiDropdown.showAfterPermission();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == WifiDropdown.SAVE_REQUEST && wifiDropdown != null)
            wifiDropdown.onSaveResult(resultCode, data);
    }
}
