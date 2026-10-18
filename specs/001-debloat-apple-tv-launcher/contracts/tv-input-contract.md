# Contract: TV Input Framework Integration & Deep Links

**Feature Branch**: `001-debloat-apple-tv-launcher`  
**Date**: 2026-10-18

---

## 1. TV Input Discovery Interface

The launcher enumerates physical video inputs on the OnePlus Y1 using Android's `TvInputManager` system service (`android.media.tv.TvInputManager`):

```java
TvInputManager tim = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
List<TvInputInfo> inputs = tim.getTvInputList();
```

### Filtering Criteria:
- **Included Types**:
  - `TvInputInfo.TYPE_HDMI` (HDMI 1, HDMI 2, HDMI 3)
  - `TvInputInfo.TYPE_COMPOSITE` / `TvInputInfo.TYPE_COMPONENT` (AV input)
- **Excluded / Handled Separately**:
  - `TvInputInfo.TYPE_TUNER` (Tuned directly or launched via `live_tv_package`)

---

## 2. Input Tuning Intent Contract

To switch the active display input to a designated HDMI or AV source without opening manufacturer overlay menus, the launcher launches an Intent with a passthrough channel URI:

```java
Uri passthroughUri = TvContract.buildChannelUriForPassthroughInput(tvInputInfo.getId());
Intent intent = new Intent(Intent.ACTION_VIEW, passthroughUri);
intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
context.startActivity(intent);
```

### Fallback Mechanism:
If the passthrough URI intent throws an `ActivityNotFoundException` or security exception on customized OEM firmware:
```java
Intent fallbackIntent = context.getPackageManager().getLaunchIntentForPackage(liveTvPackage);
if (fallbackIntent != null) {
    fallbackIntent.putExtra("input_id", tvInputInfo.getId());
    context.startActivity(fallbackIntent);
}
```

---

## 3. Wi-Fi Status Query Contract

To display the connected Wi-Fi network name on the top-right frosted pill button:

1. Query `WifiManager.getConnectionInfo()`.
2. Extract SSID: strip wrapping quotation marks (`"`).
3. If SSID is `<unknown ssid>` or Wi-Fi is disconnected, check Ethernet connection status via `ConnectivityManager`.
4. Fallback label: `Ethernet` if wired, or `Not Connected` if offline.
