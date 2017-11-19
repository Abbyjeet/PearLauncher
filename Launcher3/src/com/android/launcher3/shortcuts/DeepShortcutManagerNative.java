package com.android.launcher3.shortcuts;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.LauncherApps;
import android.content.pm.LauncherApps.ShortcutQuery;
import android.content.pm.ShortcutInfo;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.android.launcher3.Utilities;

/**
 * Performs operations related to deep shortcuts, such as querying for them, pinning them, etc.
 */
@TargetApi(25)
public class DeepShortcutManagerNative extends DeepShortcutManager {
    private static final String TAG = "DeepShortcutManager";

    private static final int FLAG_GET_ALL = ShortcutQuery.FLAG_MATCH_DYNAMIC
            | ShortcutQuery.FLAG_MATCH_MANIFEST | ShortcutQuery.FLAG_MATCH_PINNED;

    private final LauncherApps mLauncherApps;
    private boolean mWasLastCallSuccess;

    protected DeepShortcutManagerNative(Context context) {
        mLauncherApps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
    }

    public boolean wasLastCallSuccess() {
        return mWasLastCallSuccess;
    }

    public void onShortcutsChanged(List<ShortcutInfoCompat> shortcuts) {
        // mShortcutCache.removeShortcuts(shortcuts);
    }

    /**
     * Queries for the shortcuts with the package name and provided ids.
     *
     * This method is intended to get the full details for shortcuts when they are added or updated,
     * because we only get "key" fields in onShortcutsChanged().
     */
    public List<ShortcutInfoCompat> queryForFullDetails(String packageName,
                                                        List<String> shortcutIds, UserHandle user) {
        return query(FLAG_GET_ALL, packageName, null, shortcutIds, user);
    }

    /**
     * Gets all the manifest and dynamic shortcuts associated with the given package and user,
     * to be displayed in the shortcuts container on long press.
     */
    public List<ShortcutInfoCompat> queryForShortcutsContainer(ComponentName activity,
                                                               List<String> ids, UserHandle user) {
        return query(ShortcutQuery.FLAG_MATCH_MANIFEST | ShortcutQuery.FLAG_MATCH_DYNAMIC,
                activity.getPackageName(), activity, ids, user);
    }

    /**
     * Removes the given shortcut from the current list of pinned shortcuts.
     * (Runs on background thread)
     */
    public void unpinShortcut(final ShortcutKey key) {
        if (Utilities.ATLEAST_NOUGAT_MR1) {
            String packageName = key.componentName.getPackageName();
            String id = key.getId();
            UserHandle user = key.user;
            List<String> pinnedIds = extractIds(queryForPinnedShortcuts(packageName, user));
            pinnedIds.remove(id);
            try {
                mLauncherApps.pinShortcuts(packageName, pinnedIds, user);
                mWasLastCallSuccess = true;
            } catch (SecurityException|IllegalStateException e) {
                Log.w(TAG, "Failed to unpin shortcut", e);
                mWasLastCallSuccess = false;
            }
        }
    }

    /**
     * Adds the given shortcut to the current list of pinned shortcuts.
     * (Runs on background thread)
     */
    public void pinShortcut(final ShortcutKey key) {
        if (Utilities.ATLEAST_NOUGAT_MR1) {
            String packageName = key.componentName.getPackageName();
            String id = key.getId();
            UserHandle user = key.user;
            List<String> pinnedIds = extractIds(queryForPinnedShortcuts(packageName, user));
            pinnedIds.add(id);
            try {
                mLauncherApps.pinShortcuts(packageName, pinnedIds, user);
                mWasLastCallSuccess = true;
            } catch (SecurityException|IllegalStateException e) {
                Log.w(TAG, "Failed to pin shortcut", e);
                mWasLastCallSuccess = false;
            }
        }
    }

    public void startShortcut(String packageName, String id, Rect sourceBounds,
                              Bundle startActivityOptions, UserHandle user) {
        if (Utilities.ATLEAST_NOUGAT_MR1) {
            try {
                mLauncherApps.startShortcut(packageName, id, sourceBounds,
                        startActivityOptions, user);
                mWasLastCallSuccess = true;
            } catch (SecurityException|IllegalStateException e) {
                Log.e(TAG, "Failed to start shortcut", e);
                mWasLastCallSuccess = false;
            }
        }
    }

    public Drawable getShortcutIconDrawable(ShortcutInfoCompat shortcutInfo, int density) {
        if (Utilities.ATLEAST_NOUGAT_MR1) {
            try {
                Drawable icon = mLauncherApps.getShortcutIconDrawable(
                        shortcutInfo.getShortcutInfo(), density);
                mWasLastCallSuccess = true;
                return icon;
            } catch (SecurityException|IllegalStateException e) {
                Log.e(TAG, "Failed to get shortcut icon", e);
                mWasLastCallSuccess = false;
            }
        }
        return null;
    }

    protected List<String> extractIds(List<ShortcutInfoCompat> shortcuts) {
        List<String> shortcutIds = new ArrayList<>(shortcuts.size());
        for (ShortcutInfoCompat shortcut : shortcuts) {
            shortcutIds.add(shortcut.getId());
        }
        return shortcutIds;
    }

    /**
     * Query the system server for all the shortcuts matching the given parameters.
     * If packageName == null, we query for all shortcuts with the passed flags, regardless of app.
     *
     * TODO: Use the cache to optimize this so we don't make an RPC every time.
     */
    protected List<ShortcutInfoCompat> query(int flags, String packageName,
                                             ComponentName activity, List<String> shortcutIds, UserHandle user) {
        if (Utilities.ATLEAST_NOUGAT_MR1) {
            ShortcutQuery q = new ShortcutQuery();
            q.setQueryFlags(flags);
            if (packageName != null) {
                q.setPackage(packageName);
                q.setActivity(activity);
                q.setShortcutIds(shortcutIds);
            }
            List<ShortcutInfo> shortcutInfos = null;
            try {
                shortcutInfos = mLauncherApps.getShortcuts(q, user);
                mWasLastCallSuccess = true;
            } catch (SecurityException|IllegalStateException e) {
                Log.e(TAG, "Failed to query for shortcuts", e);
                mWasLastCallSuccess = false;
            }
            if (shortcutInfos == null) {
                return Collections.EMPTY_LIST;
            }
            List<ShortcutInfoCompat> shortcutInfoCompats = new ArrayList<>(shortcutInfos.size());
            for (ShortcutInfo shortcutInfo : shortcutInfos) {
                shortcutInfoCompats.add(new ShortcutInfoCompat(shortcutInfo));
            }
            return shortcutInfoCompats;
        } else {
            return Collections.EMPTY_LIST;
        }
    }

    public boolean hasHostPermission() {
        if (Utilities.ATLEAST_NOUGAT_MR1) {
            try {
                return mLauncherApps.hasShortcutHostPermission();
            } catch (SecurityException|IllegalStateException e) {
                Log.e(TAG, "Failed to make shortcut manager call", e);
            }
        }
        return false;
    }
}