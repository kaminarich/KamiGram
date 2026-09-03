package com.kaminari.gram;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

/**
 * KamiGram feature flags, NekoConfig-style: read as plain fields at the call
 * sites, written through setters that persist.
 *
 * Preferences are resolved lazily rather than in a static initialiser. The
 * static-init approach fails permanently if the class happens to be touched
 * before {@code ApplicationLoader.applicationContext} is assigned: prefs would
 * stay null for the whole process and no toggle would ever persist. Resolving on
 * first use lets it recover instead.
 *
 * Fields are volatile because they are read from the network thread
 * (MessagesController's update pipeline) and written from the UI thread.
 *
 * Features and their single enforcement point:
 * <ul>
 *   <li>{@code showDeletedMessages} - MessagesController.kamiKeepDeletedMessages</li>
 *   <li>{@code showUserIdInProfile} - ProfileActivity's header subtitle</li>
 * </ul>
 */
public final class KamiConfig {

    private KamiConfig() {
    }

    private static final String PREFS_NAME = "kamigram_config";
    private static final String KEY_DELETED = "showDeletedMessages";
    private static final String KEY_USER_ID = "showUserIdInProfile";

    public static volatile boolean showDeletedMessages = true;
    public static volatile boolean showUserIdInProfile = true;

    private static SharedPreferences prefs;
    private static boolean loaded;

    private static synchronized SharedPreferences prefs() {
        if (prefs == null) {
            Context context = ApplicationLoader.applicationContext;
            if (context != null) {
                prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            }
        }
        if (prefs != null && !loaded) {
            showDeletedMessages = prefs.getBoolean(KEY_DELETED, true);
            showUserIdInProfile = prefs.getBoolean(KEY_USER_ID, true);
            loaded = true;
        }
        return prefs;
    }

    /** Called once from ApplicationLoader so the first read is not on a hot path. */
    public static void init() {
        prefs();
    }

    public static boolean showDeletedMessages() {
        prefs();
        return showDeletedMessages;
    }

    public static boolean showUserIdInProfile() {
        prefs();
        return showUserIdInProfile;
    }

    public static void setShowDeletedMessages(boolean value) {
        showDeletedMessages = value;
        SharedPreferences p = prefs();
        if (p != null) {
            p.edit().putBoolean(KEY_DELETED, value).apply();
        }
    }

    public static void setShowUserIdInProfile(boolean value) {
        showUserIdInProfile = value;
        SharedPreferences p = prefs();
        if (p != null) {
            p.edit().putBoolean(KEY_USER_ID, value).apply();
        }
    }
}
