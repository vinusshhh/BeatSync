package com.beatsync.app.auth;

import android.content.Context;
import android.content.SharedPreferences;
import com.beatsync.app.models.User;
import com.google.gson.Gson;

public class AuthManager {
    private static final String PREF_NAME = "beatsync_prefs";
    private static final String KEY_TOKEN = "jwt_token";
    private static final String KEY_USER = "current_user";
    // Permanent Live Cloud Endpoint
    public static final String DEFAULT_BASE_URL = "https://beatsync-91vv.onrender.com";

    private static AuthManager instance;
    private final SharedPreferences prefs;
    private final Gson gson;

    private AuthManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    public static synchronized AuthManager getInstance(Context context) {
        if (instance == null) {
            instance = new AuthManager(context);
        }
        return instance;
    }

    public void saveAuth(String token, User user) {
        prefs.edit()
                .putString(KEY_TOKEN, token)
                .putString(KEY_USER, gson.toJson(user))
                .apply();
    }

    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public User getUser() {
        String json = prefs.getString(KEY_USER, null);
        if (json == null) return null;
        try {
            return gson.fromJson(json, User.class);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isLoggedIn() {
        return getToken() != null && getUser() != null;
    }

    public void logout() {
        prefs.edit()
                .remove(KEY_TOKEN)
                .remove(KEY_USER)
                .apply();
    }

    public String getBaseUrl() {
        return DEFAULT_BASE_URL;
    }
}
