package id.rk.aserver;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

final class AppPrefs {
    private static final String FILE = "aserver_settings";
    static final String KEY_ACTIVE = "guard_active";
    static final String KEY_WIFI_LOCK = "keep_wifi_awake";
    static final String KEY_AUTO_START = "start_after_boot";
    static final String KEY_SELECTED = "selected_packages";
    static final String KEY_MONITOR_URL = "monitor_url";
    static final String DEFAULT_MONITOR_URL = "http://127.0.0.1:8080/";
    static final String KEY_SERVER_ALIVE = "server_alive";
    static final String KEY_LAST_CHECK = "last_server_check";
    static final String KEY_LAST_HTTP_CODE = "last_http_code";
    static final String KEY_LAST_ERROR = "last_error";
    static final String KEY_TUNNEL_URL = "tunnel_monitor_url";
    static final String KEY_TUNNEL_ALIVE = "tunnel_alive";
    static final String KEY_TUNNEL_LAST_CHECK = "tunnel_last_check";
    static final String KEY_TUNNEL_HTTP_CODE = "tunnel_last_http_code";
    static final String KEY_TUNNEL_LAST_ERROR = "tunnel_last_error";
    static final String KEY_HEARTBEAT = "service_heartbeat";

    private AppPrefs() { }

    static SharedPreferences get(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static Set<String> selectedPackages(Context context) {
        Set<String> saved = get(context).getStringSet(KEY_SELECTED, Collections.emptySet());
        return saved == null ? new HashSet<>() : new HashSet<>(saved);
    }

    static void saveSelectedPackages(Context context, Set<String> packages) {
        get(context).edit().putStringSet(KEY_SELECTED, new HashSet<>(packages)).apply();
    }
}
