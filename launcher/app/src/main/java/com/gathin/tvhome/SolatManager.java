package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Malaysian Prayer Times (Waktu Solat) Manager.
 * Fetches prayer times from https://api.waktusolat.app with automatic year & month resolution,
 * offline caching in SharedPreferences, next prayer highlighting, and interactive TV zone selection.
 */
public final class SolatManager {

    public static class Zone {
        public final String code;
        public final String name;

        public Zone(String code, String name) {
            this.code = code;
            this.name = name;
        }
    }

    private static final List<Zone> ALL_ZONES = new ArrayList<>();
    static {
        ALL_ZONES.add(new Zone("WLY01", "Kuala Lumpur, Putrajaya"));
        ALL_ZONES.add(new Zone("WLY02", "Labuan"));
        ALL_ZONES.add(new Zone("SGR01", "Gombak, Petaling, Sepang, Hulu Langat, Hulu Selangor"));
        ALL_ZONES.add(new Zone("SGR02", "Klang, Kuala Langat"));
        ALL_ZONES.add(new Zone("SGR03", "Kuala Selangor, Sabak Bernam"));
        ALL_ZONES.add(new Zone("JHR01", "Pulau Aur, Pulau Pemanggil"));
        ALL_ZONES.add(new Zone("JHR02", "Johor Bahru, Kota Tinggi, Mersing, Kulai"));
        ALL_ZONES.add(new Zone("JHR03", "Kluang, Pontian"));
        ALL_ZONES.add(new Zone("JHR04", "Batu Pahat, Muar, Segamat, Gemas Johor, Tangkak"));
        ALL_ZONES.add(new Zone("PNG01", "Seluruh Negeri Pulau Pinang"));
        ALL_ZONES.add(new Zone("PRK01", "Tapah, Slim River, Tanjung Malim"));
        ALL_ZONES.add(new Zone("PRK02", "Ipoh, Batu Gajah, Kampar, Sg Siput, Kuala Kangsar"));
        ALL_ZONES.add(new Zone("PRK03", "Pengkalan Hulu, Grik, Lenggong"));
        ALL_ZONES.add(new Zone("PRK04", "Temengor, Belum"));
        ALL_ZONES.add(new Zone("PRK05", "Teluk Intan, Seri Iskandar, Lumut, Sitiawan, P. Pangkor"));
        ALL_ZONES.add(new Zone("PRK06", "Selama, Taiping, Bagan Serai, Parit Buntar"));
        ALL_ZONES.add(new Zone("PRK07", "Bukit Larut"));
        ALL_ZONES.add(new Zone("KDH01", "Kota Setar, Kubang Pasu, Pokok Sena"));
        ALL_ZONES.add(new Zone("KDH02", "Kuala Muda, Yan, Pendang"));
        ALL_ZONES.add(new Zone("KDH03", "Padang Terap, Sik"));
        ALL_ZONES.add(new Zone("KDH04", "Baling"));
        ALL_ZONES.add(new Zone("KDH05", "Kulim, Bandar Baharu"));
        ALL_ZONES.add(new Zone("KDH06", "Langkawi"));
        ALL_ZONES.add(new Zone("KDH07", "Puncak Gunung Jerai"));
        ALL_ZONES.add(new Zone("MLK01", "Seluruh Negeri Melaka"));
        ALL_ZONES.add(new Zone("NGR01", "Tampin, Jempol"));
        ALL_ZONES.add(new Zone("NGR02", "Seremban, Port Dickson, Rembau, Jelebu, Kuala Pilah"));
        ALL_ZONES.add(new Zone("PHG01", "Pulau Tioman"));
        ALL_ZONES.add(new Zone("PHG02", "Kuantan, Pekan, Rompin, Muadzam Shah"));
        ALL_ZONES.add(new Zone("PHG03", "Jerantut, Temerloh, Maran, Bera, Chenor, Jengka"));
        ALL_ZONES.add(new Zone("PHG04", "Bentong, Lipis, Raub"));
        ALL_ZONES.add(new Zone("PHG05", "Genting Sempah, Janda Baik, Bukit Tinggi"));
        ALL_ZONES.add(new Zone("PHG06", "Cameron Highlands, Genting Highlands, Bukit Fraser"));
        ALL_ZONES.add(new Zone("TRG01", "Kuala Terengganu, Marang, Kuala Nerus"));
        ALL_ZONES.add(new Zone("TRG02", "Besut, Setiu"));
        ALL_ZONES.add(new Zone("TRG03", "Hulu Terengganu"));
        ALL_ZONES.add(new Zone("TRG04", "Dungun, Kemaman"));
        ALL_ZONES.add(new Zone("KTN01", "Kota Bharu, Bachok, Pasir Puteh, Tumpat, Pasir Mas"));
        ALL_ZONES.add(new Zone("KTN02", "Jeli, Gua Musang"));
        ALL_ZONES.add(new Zone("PLS01", "Seluruh Negeri Perlis"));
        ALL_ZONES.add(new Zone("SBH01", "Bahagian Sandakan (Timur), Bukit Garam, Semawang"));
        ALL_ZONES.add(new Zone("SBH02", "Beluran, Telupid, Pinangah, Terusan, Kuamut"));
        ALL_ZONES.add(new Zone("SBH03", "Lahad Datu, Silabukan, Kunak, Sahabat, Semporna"));
        ALL_ZONES.add(new Zone("SBH04", "Bandar Tawau, Balung, Merotai, Kalabakan"));
        ALL_ZONES.add(new Zone("SBH05", "Kudat, Kota Marudu, Pitas, Pulau Banggi"));
        ALL_ZONES.add(new Zone("SBH06", "Gunung Kinabalu"));
        ALL_ZONES.add(new Zone("SBH07", "Kota Kinabalu, Penampang, Tuaran, Ranau, Papar"));
        ALL_ZONES.add(new Zone("SBH08", "Pensiangan, Keningau, Tambunan, Nabawan"));
        ALL_ZONES.add(new Zone("SBH09", "Beaufort, Kuala Penyu, Sipitang, Tenom"));
        ALL_ZONES.add(new Zone("SWK01", "Limbang, Lawas, Sundar, Trusan"));
        ALL_ZONES.add(new Zone("SWK02", "Miri, Niah, Bekenu, Sibuti, Marudi"));
        ALL_ZONES.add(new Zone("SWK03", "Pandan, Belaga, Suai, Tatau, Sebauh, Bintulu"));
        ALL_ZONES.add(new Zone("SWK04", "Sibu, Mukah, Dalat, Song, Igan, Oya, Kanowit, Kapit"));
        ALL_ZONES.add(new Zone("SWK05", "Sarikei, Matu, Julau, Rajang, Daro, Bintangor"));
        ALL_ZONES.add(new Zone("SWK06", "Lubok Antu, Sri Aman, Betong, Saratok"));
        ALL_ZONES.add(new Zone("SWK07", "Serian, Simunjan, Samarahan, Sebuyau"));
        ALL_ZONES.add(new Zone("SWK08", "Kuching, Bau, Lundu, Sematan"));
        ALL_ZONES.add(new Zone("SWK09", "Zon Khas (Kampung Matang)"));
    }

    private final Activity activity;
    private final SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final View cardView;
    private final TextView tvZoneBadge;
    private final TextView tvHijri;

    private final View pillSubuh, pillSyuruk, pillZohor, pillAsar, pillMaghrib, pillIsyak;
    private final TextView nameSubuh, nameSyuruk, nameZohor, nameAsar, nameMaghrib, nameIsyak;
    private final TextView timeSubuh, timeSyuruk, timeZohor, timeAsar, timeMaghrib, timeIsyak;

    public SolatManager(Activity activity, View cardView) {
        this.activity = activity;
        this.cardView = cardView;
        this.prefs = activity.getSharedPreferences("waktu_solat_prefs", Context.MODE_PRIVATE);

        tvZoneBadge = cardView.findViewById(R.id.solatZoneBadge);
        tvHijri = cardView.findViewById(R.id.solatHijriDate);

        pillSubuh = cardView.findViewById(R.id.pillSubuh);
        pillSyuruk = cardView.findViewById(R.id.pillSyuruk);
        pillZohor = cardView.findViewById(R.id.pillZohor);
        pillAsar = cardView.findViewById(R.id.pillAsar);
        pillMaghrib = cardView.findViewById(R.id.pillMaghrib);
        pillIsyak = cardView.findViewById(R.id.pillIsyak);

        nameSubuh = cardView.findViewById(R.id.nameSubuh);
        nameSyuruk = cardView.findViewById(R.id.nameSyuruk);
        nameZohor = cardView.findViewById(R.id.nameZohor);
        nameAsar = cardView.findViewById(R.id.nameAsar);
        nameMaghrib = cardView.findViewById(R.id.nameMaghrib);
        nameIsyak = cardView.findViewById(R.id.nameIsyak);

        timeSubuh = cardView.findViewById(R.id.timeSubuh);
        timeSyuruk = cardView.findViewById(R.id.timeSyuruk);
        timeZohor = cardView.findViewById(R.id.timeZohor);
        timeAsar = cardView.findViewById(R.id.timeAsar);
        timeMaghrib = cardView.findViewById(R.id.timeMaghrib);
        timeIsyak = cardView.findViewById(R.id.timeIsyak);

        cardView.setOnClickListener(v -> showZoneDialog());
    }

    public String getCurrentZoneCode() {
        return prefs.getString("zone_code", "WLY01");
    }

    public String getCurrentZoneName() {
        String code = getCurrentZoneCode();
        return prefs.getString("zone_name", getZoneName(code));
    }

    private static String getZoneName(String code) {
        for (Zone z : ALL_ZONES) {
            if (z.code.equalsIgnoreCase(code)) return z.name;
        }
        return code;
    }

    public void update() {
        String zone = getCurrentZoneCode();
        String zoneName = getCurrentZoneName();
        tvZoneBadge.setText(zone + " · " + zoneName + " ▾");

        Calendar cal = Calendar.getInstance();
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH) + 1;
        int day = cal.get(Calendar.DAY_OF_MONTH);

        String cacheKey = "cache_" + zone + "_" + year + "_" + month;
        String cachedJson = prefs.getString(cacheKey, null);
        if (cachedJson != null) {
            parseAndDisplay(cachedJson, day);
        }

        long lastFetch = prefs.getLong("last_fetch_" + zone + "_" + year + "_" + month, 0);
        long now = System.currentTimeMillis();
        if (cachedJson == null || (now - lastFetch) > 43_200_000L) { // 12 hours
            fetchFromApi(zone, year, month, day, cacheKey);
        }
    }

    private void fetchFromApi(String zone, int year, int month, int day, String cacheKey) {
        executor.execute(() -> {
            try {
                String urlStr = "https://api.waktusolat.app/v2/solat/" + zone + "?year=" + year + "&month=" + month;
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("User-Agent", "CleanYourTV-Solat/1.0");

                int code = conn.getResponseCode();
                if (code == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();
                    String jsonStr = sb.toString();

                    prefs.edit()
                            .putString(cacheKey, jsonStr)
                            .putLong("last_fetch_" + zone + "_" + year + "_" + month, System.currentTimeMillis())
                            .apply();

                    mainHandler.post(() -> parseAndDisplay(jsonStr, day));
                }
            } catch (Exception e) {
                // Silently fallback to cache or retry on next minute
            }
        });
    }

    private void parseAndDisplay(String jsonStr, int targetDay) {
        try {
            JSONObject obj = new JSONObject(jsonStr);
            JSONArray prayers = obj.optJSONArray("prayers");
            if (prayers == null || prayers.length() == 0) return;

            JSONObject todayObj = null;
            for (int i = 0; i < prayers.length(); i++) {
                JSONObject p = prayers.getJSONObject(i);
                if (p.optInt("day") == targetDay) {
                    todayObj = p;
                    break;
                }
            }
            if (todayObj == null && prayers.length() > 0) {
                todayObj = prayers.getJSONObject(0);
            }
            if (todayObj == null) return;

            long fajr = todayObj.optLong("fajr");
            long syuruk = todayObj.optLong("syuruk");
            long dhuhr = todayObj.optLong("dhuhr");
            long asr = todayObj.optLong("asr");
            long maghrib = todayObj.optLong("maghrib");
            long isha = todayObj.optLong("isha");
            String hijri = todayObj.optString("hijri", "");

            if (!hijri.isEmpty()) {
                tvHijri.setText(hijri + "H");
            }

            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            if (fajr > 0) timeSubuh.setText(sdf.format(new Date(fajr * 1000L)));
            if (syuruk > 0) timeSyuruk.setText(sdf.format(new Date(syuruk * 1000L)));
            if (dhuhr > 0) timeZohor.setText(sdf.format(new Date(dhuhr * 1000L)));
            if (asr > 0) timeAsar.setText(sdf.format(new Date(asr * 1000L)));
            if (maghrib > 0) timeMaghrib.setText(sdf.format(new Date(maghrib * 1000L)));
            if (isha > 0) timeIsyak.setText(sdf.format(new Date(isha * 1000L)));

            highlightCurrentPrayer(fajr, syuruk, dhuhr, asr, maghrib, isha);
        } catch (Exception e) {
            // Drop invalid format
        }
    }

    private void highlightCurrentPrayer(long fajr, long syuruk, long dhuhr, long asr, long maghrib, long isha) {
        long now = System.currentTimeMillis() / 1000L;
        int active = 0; // 0: None, 1: Subuh, 2: Syuruk, 3: Zohor, 4: Asar, 5: Maghrib, 6: Isyak

        if (now < fajr) active = 1;
        else if (now < syuruk) active = 2;
        else if (now < dhuhr) active = 3;
        else if (now < asr) active = 4;
        else if (now < maghrib) active = 5;
        else if (now < isha) active = 6;
        else active = 1; // After Isyak, next is Subuh tomorrow

        setHighlight(pillSubuh, nameSubuh, timeSubuh, active == 1);
        setHighlight(pillSyuruk, nameSyuruk, timeSyuruk, active == 2);
        setHighlight(pillZohor, nameZohor, timeZohor, active == 3);
        setHighlight(pillAsar, nameAsar, timeAsar, active == 4);
        setHighlight(pillMaghrib, nameMaghrib, timeMaghrib, active == 5);
        setHighlight(pillIsyak, nameIsyak, timeIsyak, active == 6);
    }

    private void setHighlight(View pill, TextView name, TextView time, boolean isNext) {
        pill.setBackgroundResource(isNext ? R.drawable.solat_pill_active_bg : R.drawable.solat_pill_bg);
        name.setTextColor(isNext ? Color.parseColor("#00E5FF") : Color.parseColor("#99FFFFFF"));
        time.setTextColor(isNext ? Color.parseColor("#00E5FF") : Color.parseColor("#FFFFFFFF"));
    }

    public void showZoneDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_solat_zone, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
            int width = (int) (640 * activity.getResources().getDisplayMetrics().density);
            dialog.getWindow().setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
        }

        RecyclerView rv = dialogView.findViewById(R.id.zoneList);
        rv.setLayoutManager(new LinearLayoutManager(activity));

        String currentCode = getCurrentZoneCode();
        ZoneAdapter adapter = new ZoneAdapter(ALL_ZONES, currentCode, selectedZone -> {
            setZone(selectedZone.code, selectedZone.name);
            dialog.dismiss();
            update();
            Toast.makeText(activity, "Kawasan ditukar ke " + selectedZone.code + " (" + selectedZone.name + ")", Toast.LENGTH_SHORT).show();
        });
        rv.setAdapter(adapter);

        dialogView.findViewById(R.id.btnCloseDialog).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btnCustomZone).setOnClickListener(v -> {
            dialog.dismiss();
            showCustomZonePrompt();
        });

        dialog.setOnKeyListener((d, keyCode, event) -> {
            if ((keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE)
                    && event.getAction() == KeyEvent.ACTION_UP) {
                dialog.dismiss();
                return true;
            }
            return false;
        });

        dialog.show();
        rv.requestFocus();
    }

    private void showCustomZonePrompt() {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Masukkan Kod Kawasan (JAKIM)");
        builder.setMessage("Contoh: WLY01, SGR01, JHR02, PNG01, KDH01");

        final EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setHint("Kod Kawasan (cth: WLY01)");
        input.setText(getCurrentZoneCode());
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.GRAY);
        input.setPadding(30, 20, 30, 20);
        builder.setView(input);

        builder.setPositiveButton("Simpan", (d, which) -> {
            String code = input.getText().toString().trim().toUpperCase(Locale.ROOT);
            if (!code.isEmpty()) {
                String name = getZoneName(code);
                setZone(code, name);
                update();
                Toast.makeText(activity, "Kawasan disimpan: " + code, Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Batal", (d, which) -> d.cancel());

        AlertDialog alert = builder.create();
        alert.setOnKeyListener((d, keyCode, event) -> {
            if ((keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE)
                    && event.getAction() == KeyEvent.ACTION_UP) {
                alert.dismiss();
                return true;
            }
            return false;
        });
        alert.show();
    }

    private void setZone(String code, String name) {
        prefs.edit()
                .putString("zone_code", code)
                .putString("zone_name", name)
                .apply();
    }

    private static class ZoneAdapter extends RecyclerView.Adapter<ZoneAdapter.ViewHolder> {
        private final List<Zone> zones;
        private final String currentCode;
        private final ZoneClickListener listener;

        interface ZoneClickListener {
            void onZoneClicked(Zone zone);
        }

        ZoneAdapter(List<Zone> zones, String currentCode, ZoneClickListener listener) {
            this.zones = zones;
            this.currentCode = currentCode;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_solat_zone, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Zone z = zones.get(position);
            holder.code.setText(z.code);
            holder.name.setText(z.name);

            boolean isCurrent = z.code.equalsIgnoreCase(currentCode);
            holder.itemView.setSelected(isCurrent);

            holder.itemView.setFocusable(true);
            holder.itemView.setClickable(true);
            holder.itemView.setOnClickListener(v -> listener.onZoneClicked(z));
            holder.itemView.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                        || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                    v.performClick();
                    return true;
                }
                return false;
            });
        }

        @Override
        public int getItemCount() {
            return zones.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final TextView code;
            final TextView name;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                code = itemView.findViewById(R.id.zoneCode);
                name = itemView.findViewById(R.id.zoneName);
            }
        }
    }
}
