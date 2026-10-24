package io.github.ptrinh.tvremote;

import android.app.Activity;
import android.app.AppOpsManager;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * The card's screen: the pairing QR code (the relay's phone page with #<TV id>.<phone key>; with no relay,
 * LAN mode's page on this TV, http://<TV's address>:8781/#<TV id>.<phone key>), whether the relay is online, New code (a new phone key: phones paired before stop working) and Hide code for when
 * guests are watching; showing it again asks for the PIN, if one is set. Below, what install.sh granted.
 * Opening it also starts {@link RemoteService}. In the tvOS style: black, a warm glow, glass buttons that
 * turn white when focused.
 */
public class MainActivity extends Activity {
    private static final String PREF_HIDDEN = "qr_hidden";
    private static final int ACCENT = 0xFFFF8A00;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private float d;
    private TextView status, hint, hideButton, setupButton;
    private View statusDot;
    private ImageView qr;
    private TextView qrHidden;
    private FrameLayout qrCard;
    private LinearLayout checks;
    private String shownUrl;

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            drawStatus();
            drawChecks();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        d = getResources().getDisplayMetrics().density;
        RemoteService.start(this);
        TvLock.start(this);
        RemoteRelay.start(this, Relay.url(this));
        LocalServer.start(this);

        FrameLayout root = new FrameLayout(this);
        GradientDrawable glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setColors(new int[]{0x55FF8A00, 0x00000000});
        glow.setGradientRadius(700 * d);
        glow.setGradientCenter(0.15f, 0f);
        root.setBackground(glow);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        // A TV screen is 540 dp tall: everything on the left has to fit without scrolling.
        row.setPadding((int) (72 * d), (int) (28 * d), (int) (72 * d), (int) (28 * d));
        root.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = text(getString(R.string.app_name), 40, Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        left.addView(title);

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusDot = new View(this);
        statusRow.addView(statusDot, new LinearLayout.LayoutParams((int) (12 * d), (int) (12 * d)));
        status = text("", 20, 0xCCFFFFFF);
        LinearLayout.LayoutParams sp = wrap();
        sp.leftMargin = (int) (10 * d);
        statusRow.addView(status, sp);
        left.addView(statusRow, top(wrap(), 10));

        hint = text("", 18, 0x99FFFFFF);
        hint.setMaxWidth((int) (560 * d));
        hint.setLineSpacing(0, 1.15f);
        left.addView(hint, top(wrap(), 16));

        LinearLayout buttons = new LinearLayout(this);
        TextView newCode = button(getString(R.string.new_code));
        newCode.setOnClickListener(v -> {
            RemoteRelay.newPhoneKey(this);
            shownUrl = null;
            drawQr();
            Toast.makeText(this, R.string.new_code_done, Toast.LENGTH_LONG).show();
        });
        buttons.addView(newCode);
        hideButton = button("");
        hideButton.setOnClickListener(v -> toggleHidden());
        LinearLayout.LayoutParams hp = wrap();
        hp.leftMargin = (int) (12 * d);
        buttons.addView(hideButton, hp);
        setupButton = button(getString(R.string.relay_setup));
        setupButton.setOnClickListener(v -> showRelaySetup());
        LinearLayout.LayoutParams rp = wrap();
        rp.leftMargin = (int) (12 * d);
        buttons.addView(setupButton, rp);
        left.addView(buttons, top(wrap(), 22));

        // What install.sh granted: five short lines, small enough to fit under the buttons.
        checks = new LinearLayout(this);
        checks.setOrientation(LinearLayout.VERTICAL);
        left.addView(checks, top(wrap(), 20));

        qrCard = new FrameLayout(this);
        GradientDrawable white = new GradientDrawable();
        white.setColor(Color.WHITE);
        white.setCornerRadius(28 * d);
        qrCard.setBackground(white);
        qrCard.setPadding((int) (14 * d), (int) (14 * d), (int) (14 * d), (int) (14 * d));
        qr = new ImageView(this);
        qr.setScaleType(ImageView.ScaleType.FIT_CENTER);
        qrCard.addView(qr, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        qrHidden = text("🔒", 64, Color.BLACK);
        qrHidden.setGravity(Gravity.CENTER);
        qrCard.addView(qrHidden, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        int side = (int) (340 * d);
        LinearLayout.LayoutParams qp = new LinearLayout.LayoutParams(side, side);
        qp.leftMargin = (int) (48 * d);
        row.addView(qrCard, qp);

        setContentView(root);
        drawQr();
        newCode.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(refresh);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refresh);
        super.onPause();
    }

    // ---- the code -----------------------------------------------------------------------------------

    private boolean hidden() {
        return Pin.prefs(this).getBoolean(PREF_HIDDEN, false);
    }

    private void toggleHidden() {
        if (!hidden()) {
            Pin.prefs(this).edit().putBoolean(PREF_HIDDEN, true).apply();
            drawQr();
            return;
        }
        Runnable show = () -> {
            Pin.prefs(this).edit().putBoolean(PREF_HIDDEN, false).apply();
            drawQr();
        };
        if (!Pin.hasPin(Pin.prefs(this))) {
            show.run();
            return;
        }
        askPin(getString(R.string.pin_show_code), show);
    }

    /**
     * The relay's link, or LAN mode's on this TV's current address (checked every second while the card is
     * open, so a new address after a network change gives a new code).
     */
    private void drawQr() {
        boolean relay = !Relay.url(this).isEmpty();
        String ip = relay ? null : LocalServer.lanAddress();
        boolean h = hidden();
        hideButton.setText(h ? R.string.show_code : R.string.hide_code);
        setupButton.setVisibility(relay ? View.GONE : View.VISIBLE);
        hint.setText(h ? R.string.card_hidden_hint : relay ? R.string.card_hint : ip != null ? R.string.card_lan_hint : R.string.card_lan_no_network);
        qr.setVisibility(h ? View.INVISIBLE : View.VISIBLE);
        qrHidden.setVisibility(h ? View.VISIBLE : View.GONE);
        if (h) {
            qr.setImageBitmap(null);
            shownUrl = null;
            return;
        }
        String url = relay ? RemoteRelay.phoneUrl(this) : ip != null ? RemoteRelay.lanUrl(this, ip) : null;
        qr.setVisibility(url != null ? View.VISIBLE : View.INVISIBLE);
        if (url == null) {
            shownUrl = null;
            qr.setImageBitmap(null);
        } else if (!url.equals(shownUrl)) {
            qr.setImageBitmap(qrBitmap(url));
            shownUrl = url;
        }
    }

    /** A QR code of {@code text}, black on white with the four-module quiet zone, sharp when scaled. */
    static Bitmap qrBitmap(String text) {
        boolean[][] m = Qr.encode(text.getBytes(StandardCharsets.UTF_8));
        if (m == null) return null;
        int n = m.length + 8;
        int[] px = new int[n * n];
        Arrays.fill(px, Color.WHITE);
        for (int y = 0; y < m.length; y++) {
            for (int x = 0; x < m.length; x++) if (m[y][x]) px[(y + 4) * n + x + 4] = Color.BLACK;
        }
        Bitmap small = Bitmap.createBitmap(px, n, n, Bitmap.Config.RGB_565);
        return Bitmap.createScaledBitmap(small, n * 8, n * 8, false);
    }

    /** Where the setup steps and the Deploy to Cloudflare button are; the popup's QR code opens it. */
    private static final String SETUP_URL = "https://github.com/Gathin23/cleanyourtv/tree/main/addons/tv-remote#setup";

    /** LAN mode's popup: the three steps to a relay, with the TV's own address filled into the command. */
    private void showRelaySetup() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = new LinearLayout(this);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        int pad = (int) (40 * d);
        panel.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF21C1C1E);
        bg.setCornerRadius(28 * d);
        panel.setBackground(bg);

        LinearLayout steps = new LinearLayout(this);
        steps.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(getString(R.string.relay_setup_title), 30, Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        steps.addView(title);
        String ip = LocalServer.lanAddress();
        String[] lines = {
            getString(R.string.relay_step1),
            getString(R.string.relay_step2, ip != null ? ip : "<tv ip>"),
            getString(R.string.relay_step3),
        };
        for (int i = 0; i < lines.length; i++) {
            LinearLayout row = new LinearLayout(this);
            TextView n = text(String.valueOf(i + 1), 18, 0xFFFF9F0A);
            n.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            row.addView(n, new LinearLayout.LayoutParams((int) (28 * d), ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView t = text(lines[i], 16, 0xCCFFFFFF);
            t.setLineSpacing(0, 1.15f);
            t.setMaxWidth((int) (600 * d));
            row.addView(t);
            steps.addView(row, top(wrap(), i == 0 ? 20 : 14));
        }
        TextView close = button(getString(R.string.close));
        close.setOnClickListener(v -> dialog.dismiss());
        steps.addView(close, top(wrap(), 24));
        panel.addView(steps);

        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView code = new ImageView(this);
        code.setImageBitmap(qrBitmap(SETUP_URL));
        GradientDrawable white = new GradientDrawable();
        white.setColor(Color.WHITE);
        white.setCornerRadius(18 * d);
        code.setBackground(white);
        int qp = (int) (8 * d);
        code.setPadding(qp, qp, qp, qp);
        side.addView(code, new LinearLayout.LayoutParams((int) (220 * d), (int) (220 * d)));
        TextView caption = text(getString(R.string.relay_setup_scan), 14, 0x99FFFFFF);
        caption.setGravity(Gravity.CENTER);
        caption.setMaxWidth((int) (220 * d));
        side.addView(caption, top(wrap(), 10));
        LinearLayout.LayoutParams sideLp = wrap();
        sideLp.leftMargin = (int) (40 * d);
        panel.addView(side, sideLp);

        dialog.setContentView(panel);
        Window w = dialog.getWindow();
        if (w != null) w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.show();
        close.requestFocus();
    }

    private void askPin(String title, Runnable then) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        PinPad pad = new PinPad(this, title, (p, pin) -> {
            String why = Pin.attempt(this, pin, SystemClock.elapsedRealtime());
            if (why != null) {
                p.fail(why);
                return;
            }
            dialog.dismiss();
            then.run();
        });
        dialog.setContentView(pad.view);
        Window w = dialog.getWindow();
        if (w != null) w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.setOnKeyListener((di, keyCode, event) -> pad.key(event));
        dialog.show();
        pad.focus();
    }

    // ---- status ------------------------------------------------------------------------------------

    private void drawStatus() {
        boolean relay = !Relay.url(this).isEmpty();
        boolean on = relay ? RemoteRelay.online() : LocalServer.running();
        status.setText(relay ? (on ? R.string.status_online : R.string.status_connecting)
                : on ? R.string.status_lan : R.string.status_lan_starting);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(on ? 0xFF30D158 : 0xFFFF9F0A);
        statusDot.setBackground(dot);
        drawQr();
    }

    /** What install.sh grants, one line each: green On, or how to fix it. */
    private void drawChecks() {
        checks.removeAllViews();
        check(R.string.check_overlay, Settings.canDrawOverlays(this));
        check(R.string.check_accessibility, KeyService.running != null);
        check(R.string.check_usage, ScreenTime.allowed(this));
        check(R.string.check_capture, opAllowed(this, "android:project_media"));
        check(R.string.check_secure, checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED);
    }

    private void check(int label, boolean on) {
        LinearLayout r = new LinearLayout(this);
        TextView name = text(getString(label), 14, 0x99FFFFFF);
        r.addView(name, new LinearLayout.LayoutParams((int) (260 * d), ViewGroup.LayoutParams.WRAP_CONTENT));
        r.addView(text(getString(on ? R.string.check_on : R.string.check_off), 14, on ? 0xFF30D158 : 0xFFFF9F0A));
        checks.addView(r, top(wrap(), 2));
    }

    /** An app-op is allowed for this app; PROJECT_MEDIA has no public constant, hence the string. */
    @SuppressWarnings("deprecation")   // unsafeCheckOpNoThrow is Android 10+; this one works on 8 too
    static boolean opAllowed(android.content.Context ctx, String op) {
        try {
            AppOpsManager ops = ctx.getSystemService(AppOpsManager.class);
            return ops != null && ops.checkOpNoThrow(op, Process.myUid(), ctx.getPackageName()) == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;   // an op this Android doesn't know
        }
    }

    // ---- views -------------------------------------------------------------------------------------

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    private TextView button(String label) {
        TextView b = text(label, 18, Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setFocusable(true);
        b.setClickable(true);
        b.setSingleLine();
        b.setPadding((int) (20 * d), 0, (int) (20 * d), 0);
        b.setMinHeight((int) (52 * d));
        b.setMinimumWidth((int) (110 * d));
        b.setBackground(PinPad.keyBackground(d));
        b.setOnFocusChangeListener((v, has) -> ((TextView) v).setTextColor(has ? 0xFF0B0B0D : Color.WHITE));
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return b;
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams top(LinearLayout.LayoutParams lp, int dp) {
        lp.topMargin = (int) (dp * d);
        return lp;
    }
}
