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
 * (MessagesController's update pipeline, FileLoadOperation) and written from the
 * UI thread.
 *
 * Each flag and its single enforcement point:
 * <ul>
 *   <li>{@code showDeletedMessages} - MessagesController.kamiKeepDeletedMessages</li>
 *   <li>{@code showUserIdInProfile} - ProfileActivity header subtitle</li>
 *   <li>{@code hideOnlineStatus} - MessagesController.updateTimerProc, the
 *       account.updateStatus call</li>
 *   <li>{@code hideTypingStatus} - MessagesController.sendTyping, typing action</li>
 *   <li>{@code hideMediaStatus} - MessagesController.sendTyping, upload actions</li>
 *   <li>{@code boostNetwork} - FileLoadOperation.updateParams (download) and
 *       FileUploadOperation (upload)</li>
 *   <li>{@code forceHighQualityMedia} - MediaController.PhotoEntry.isHighQuality</li>
 *   <li>{@code bypassFirebaseLogin} - LoginActivity code settings</li>
 * </ul>
 */
public final class KamiConfig {

    private KamiConfig() {
    }

    private static final String PREFS_NAME = "kamigram_config";

    private static final String KEY_DELETED = "showDeletedMessages";
    private static final String KEY_USER_ID = "showUserIdInProfile";
    private static final String KEY_HIDE_ONLINE = "hideOnlineStatus";
    private static final String KEY_HIDE_TYPING = "hideTypingStatus";
    private static final String KEY_HIDE_MEDIA = "hideMediaStatus";
    private static final String KEY_BOOST = "boostNetwork";
    private static final String KEY_HQ_MEDIA = "forceHighQualityMedia";
    private static final String KEY_BYPASS_FIREBASE = "bypassFirebaseLogin";

    // on by default: these are KamiGram's identity
    public static volatile boolean showDeletedMessages = true;
    public static volatile boolean showUserIdInProfile = true;

    // off by default: privacy and experimental features are opt-in, because each
    // one changes protocol-visible behaviour
    public static volatile boolean hideOnlineStatus = false;
    public static volatile boolean hideTypingStatus = false;
    public static volatile boolean hideMediaStatus = false;
    public static volatile boolean boostNetwork = false;
    public static volatile boolean forceHighQualityMedia = false;
    public static volatile boolean bypassFirebaseLogin = false;

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
            hideOnlineStatus = prefs.getBoolean(KEY_HIDE_ONLINE, false);
            hideTypingStatus = prefs.getBoolean(KEY_HIDE_TYPING, false);
            hideMediaStatus = prefs.getBoolean(KEY_HIDE_MEDIA, false);
            boostNetwork = prefs.getBoolean(KEY_BOOST, false);
            forceHighQualityMedia = prefs.getBoolean(KEY_HQ_MEDIA, false);
            bypassFirebaseLogin = prefs.getBoolean(KEY_BYPASS_FIREBASE, false);
            loaded = true;
        }
        return prefs;
    }

    /** Called once from ApplicationLoader so the first read is not on a hot path. */
    public static void init() {
        prefs();
    }

    private static void put(String key, boolean value) {
        SharedPreferences p = prefs();
        if (p != null) {
            p.edit().putBoolean(key, value).apply();
        }
    }

    public static boolean showDeletedMessages() {
        prefs();
        return showDeletedMessages;
    }

    public static boolean showUserIdInProfile() {
        prefs();
        return showUserIdInProfile;
    }

    public static boolean hideOnlineStatus() {
        prefs();
        return hideOnlineStatus;
    }

    public static boolean hideTypingStatus() {
        prefs();
        return hideTypingStatus;
    }

    public static boolean hideMediaStatus() {
        prefs();
        return hideMediaStatus;
    }

    public static boolean boostNetwork() {
        prefs();
        return boostNetwork;
    }

    public static boolean forceHighQualityMedia() {
        prefs();
        return forceHighQualityMedia;
    }

    public static boolean bypassFirebaseLogin() {
        prefs();
        return bypassFirebaseLogin;
    }

    public static void setShowDeletedMessages(boolean value) {
        showDeletedMessages = value;
        put(KEY_DELETED, value);
    }

    public static void setShowUserIdInProfile(boolean value) {
        showUserIdInProfile = value;
        put(KEY_USER_ID, value);
    }

    public static void setHideOnlineStatus(boolean value) {
        hideOnlineStatus = value;
        put(KEY_HIDE_ONLINE, value);
    }

    public static void setHideTypingStatus(boolean value) {
        hideTypingStatus = value;
        put(KEY_HIDE_TYPING, value);
    }

    public static void setHideMediaStatus(boolean value) {
        hideMediaStatus = value;
        put(KEY_HIDE_MEDIA, value);
    }

    public static void setBoostNetwork(boolean value) {
        boostNetwork = value;
        put(KEY_BOOST, value);
    }

    public static void setForceHighQualityMedia(boolean value) {
        forceHighQualityMedia = value;
        put(KEY_HQ_MEDIA, value);
    }

    public static void setBypassFirebaseLogin(boolean value) {
        bypassFirebaseLogin = value;
        put(KEY_BYPASS_FIREBASE, value);
    }
}
