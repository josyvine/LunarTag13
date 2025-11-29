package com.lunartag.app.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.OnUserEarnedRewardListener;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.lunartag.app.R;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Central Logic for Handling AdMob Rewarded Ads and Tiered Unlocks.
 * Logic:
 * Level 0: Locked (Watch Ad #1 to unlock Admin Button -> Get 3 Slots)
 * Level 1: 3 Slots Active (When 0, Watch Ad #2 -> Get 5 Slots)
 * Level 2: 5 Slots Active (When 0, Watch Ad #3 -> Get Unlimited)
 * Level 3: Unlimited Mode
 */
public class AdManager {

    private static final String TAG = "AdManager";
    private static final String PREFS_ADS = "LunarTagAdsPrefs";
    private static final String KEY_AD_LEVEL = "ad_level"; // 0, 1, 2, 3
    private static final String KEY_SLOTS_REMAINING = "slots_remaining";
    private static final String KEY_LAST_UNLOCK_DATE = "last_unlock_date";

    // General Settings (To read Shift End)
    private static final String PREFS_SETTINGS = "LunarTagSettings";
    private static final String KEY_SHIFT_END = "shift_end";

    private RewardedAd rewardedAd;
    private final Context context;
    private boolean isAdLoading = false;

    public interface OnAdRewardListener {
        void onRewardEarned();
        void onAdFailed();
    }

    public AdManager(Context context) {
        this.context = context;
        loadRewardedAd(); // Pre-load ad on init
    }

    /**
     * Loads a Rewarded Ad in the background.
     */
    public void loadRewardedAd() {
        if (rewardedAd != null || isAdLoading) return;

        isAdLoading = true;
        AdRequest adRequest = new AdRequest.Builder().build();
        String adUnitId = context.getString(R.string.admob_rewarded_id);

        RewardedAd.load(context, adUnitId, adRequest, new RewardedAdLoadCallback() {
            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                Log.e(TAG, "Ad Failed to Load: " + loadAdError.getMessage());
                rewardedAd = null;
                isAdLoading = false;
            }

            @Override
            public void onAdLoaded(@NonNull RewardedAd ad) {
                Log.d(TAG, "Ad Loaded Successfully.");
                rewardedAd = ad;
                isAdLoading = false;
            }
        });
    }

    /**
     * Shows the Rewarded Ad if ready.
     * Handles the Level Upgrade logic automatically on success.
     */
    public void showRewardedAd(Activity activity, OnAdRewardListener listener) {
        if (rewardedAd == null) {
            Toast.makeText(context, "Ad not ready yet. Please try again in a few seconds.", Toast.LENGTH_SHORT).show();
            loadRewardedAd(); // Try loading again
            if (listener != null) listener.onAdFailed();
            return;
        }

        rewardedAd.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                rewardedAd = null;
                loadRewardedAd(); // Pre-load the next one
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                rewardedAd = null;
                if (listener != null) listener.onAdFailed();
            }
        });

        rewardedAd.show(activity, new OnUserEarnedRewardListener() {
            @Override
            public void onUserEarnedReward(@NonNull RewardItem rewardItem) {
                // *** THE MAGIC LOGIC ***
                upgradeAdLevel();
                if (listener != null) listener.onRewardEarned();
            }
        });
    }

    /**
     * Upgrades the user's tier based on current level.
     */
    private void upgradeAdLevel() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_ADS, Context.MODE_PRIVATE);
        int currentLevel = prefs.getInt(KEY_AD_LEVEL, 0);
        SharedPreferences.Editor editor = prefs.edit();

        if (currentLevel == 0) {
            // Unlocking Admin Button -> Level 1 (3 Slots)
            editor.putInt(KEY_AD_LEVEL, 1);
            editor.putInt(KEY_SLOTS_REMAINING, 3);
            saveUnlockDate(editor);
        } else if (currentLevel == 1) {
            // Level 1 -> Level 2 (5 Slots)
            editor.putInt(KEY_AD_LEVEL, 2);
            editor.putInt(KEY_SLOTS_REMAINING, 5);
        } else if (currentLevel == 2) {
            // Level 2 -> Level 3 (Unlimited)
            editor.putInt(KEY_AD_LEVEL, 3);
            // Slots ignored in Level 3
        }

        editor.apply();
    }

    private void saveUnlockDate(SharedPreferences.Editor editor) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.US);
        editor.putString(KEY_LAST_UNLOCK_DATE, sdf.format(new Date()));
    }

    /**
     * Checks if the Shift End Time has passed.
     * If yes, it RESETS the Ad Level to 0 (Locked).
     */
    public void checkShiftReset() {
        SharedPreferences settings = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE);
        String shiftEndStr = settings.getString(KEY_SHIFT_END, "00:00 AM");

        // Logic: If current time > shift end time, AND we are unlocked, reset.
        // Simplified Logic: We reset if the DATE has changed since last unlock.
        // Or strictly by time.
        
        // Strict Shift Logic:
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.US);
            Date dateEnd = sdf.parse(shiftEndStr);
            
            if (dateEnd != null) {
                Calendar now = Calendar.getInstance();
                Calendar end = Calendar.getInstance();
                end.setTime(dateEnd);
                
                // Align dates
                end.set(Calendar.YEAR, now.get(Calendar.YEAR));
                end.set(Calendar.MONTH, now.get(Calendar.MONTH));
                end.set(Calendar.DAY_OF_MONTH, now.get(Calendar.DAY_OF_MONTH));

                // If "Now" is AFTER "Shift End", reset.
                // NOTE: This is simple logic. For night shifts (cross-midnight), logic is complex.
                // For safety/simplicity: We reset if "Ad Date" != "Today".
                
                SharedPreferences prefs = context.getSharedPreferences(PREFS_ADS, Context.MODE_PRIVATE);
                String lastUnlock = prefs.getString(KEY_LAST_UNLOCK_DATE, "");
                SimpleDateFormat dateSdf = new SimpleDateFormat("yyyyMMdd", Locale.US);
                String today = dateSdf.format(new Date());

                if (!lastUnlock.equals(today)) {
                    // It's a new day. Reset to 0.
                    prefs.edit().putInt(KEY_AD_LEVEL, 0).putInt(KEY_SLOTS_REMAINING, 0).apply();
                }
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    // --- Getters for UI ---

    public int getAdLevel() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_ADS, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_AD_LEVEL, 0);
    }

    public int getSlotsRemaining() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_ADS, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_SLOTS_REMAINING, 0);
    }

    public void decrementSlot() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_ADS, Context.MODE_PRIVATE);
        int current = prefs.getInt(KEY_SLOTS_REMAINING, 0);
        if (current > 0) {
            prefs.edit().putInt(KEY_SLOTS_REMAINING, current - 1).apply();
        }
    }
}