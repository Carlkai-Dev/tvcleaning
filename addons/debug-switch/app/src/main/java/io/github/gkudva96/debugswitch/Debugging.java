package io.github.gkudva96.debugswitch;

/** What the screen says, with no Android types, so the JVM tests cover it. */
final class Debugging {
    /** The line under ON / OFF. */
    enum Hint { NOT_GRANTED, NEEDS_WIFI, OPEN, CLOSED }

    private Debugging() {
    }

    /**
     * Android starts Wireless debugging only on Wi-Fi and switches it off when the Wi-Fi drops, which is why it
     * is off after standby. Without the grant the button can't do anything, so that comes first.
     */
    static Hint hint(boolean on, boolean granted, boolean onWifi) {
        if (!granted) return Hint.NOT_GRANTED;
        if (on) return Hint.OPEN;
        return onWifi ? Hint.CLOSED : Hint.NEEDS_WIFI;
    }

    /** -1 for an unset, empty or odd service.adb.tls.port value, or one this firmware hides from apps. */
    static int port(String prop) {
        if (prop == null) return -1;
        try {
            int p = Integer.parseInt(prop.trim());
            return p > 0 && p < 65536 ? p : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** What a paired computer passes to adb connect, or null when the port isn't known. */
    static String endpoint(String ip, int port) {
        if (ip == null || port <= 0) return null;
        return ip + ":" + port;
    }
}
