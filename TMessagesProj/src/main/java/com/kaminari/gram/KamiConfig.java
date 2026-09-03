package com.kaminari.gram;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

/**
 * KamiGram feature flags, NekoConfig-style: one SharedPreferences-backed holder,
 * read directly as a public field, written through a setter that persists.
 *
 * Features live in the Extraordikami screen (see ui.ExtraordiKamiActivity):
 *  - showDeletedMessages: server-side deletions become labeled tombstones that
 *    stay in the chat, instead of removing the message (see MessagesController)
 *  - showUserIdInProfile: profile header shows @username and the numeric ID
 */
public final class KamiConfig {

    private KamiConfig() {
    }

    private static final SharedPreferences prefs;

    public static volatile boolean showDeletedMessages;
    public static volatile boolean showUserIdInProfile;

    static {
        Context context = ApplicationLoader.applicationContext;
        prefs = context != null ? context.getSharedPreferences("kamigram_config", Context.MODE_PRIVATE) : null;
        if (prefs != null) {
            showDeletedMessages = prefs.getBoolean("showDeletedMessages", true);
            showUserIdInProfile = prefs.getBoolean("showUserIdInProfile", true);
        } else {
            showDeletedMessages = true;
            showUserIdInProfile = true;
        }
    }

    public static void setShowDeletedMessages(boolean value) {
        showDeletedMessages = value;
        if (prefs != null) {
            prefs.edit().putBoolean("showDeletedMessages", value).apply();
        }
    }

    public static void setShowUserIdInProfile(boolean value) {
        showUserIdInProfile = value;
        if (prefs != null) {
            prefs.edit().putBoolean("showUserIdInProfile", value).apply();
        }
    }
}
