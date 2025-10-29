package com.example.wingman;

import android.app.Application;
import android.os.Bundle;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

public class AppVisibilityTracker extends Application {

    private static final MutableLiveData<Boolean> isInForeground = new MutableLiveData<>(false);

    @Override
    public void onCreate() {
        super.onCreate();

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            private int activityReferences = 0;
            private boolean isActivityChangingConfigurations = false;

            @Override
            public void onActivityCreated(android.app.Activity activity, Bundle bundle) {}

            @Override
            public void onActivityStarted(android.app.Activity activity) {
                if (++activityReferences == 1 && !isActivityChangingConfigurations) {
                    isInForeground.postValue(true);
                }
            }

            @Override
            public void onActivityResumed(android.app.Activity activity) {}

            @Override
            public void onActivityPaused(android.app.Activity activity) {}

            @Override
            public void onActivityStopped(android.app.Activity activity) {
                isActivityChangingConfigurations = activity.isChangingConfigurations();
                if (--activityReferences == 0 && !isActivityChangingConfigurations) {
                    isInForeground.postValue(false);
                }
            }

            @Override
            public void onActivitySaveInstanceState(android.app.Activity activity, Bundle bundle) {}

            @Override
            public void onActivityDestroyed(android.app.Activity activity) {}
        });
    }

    public static LiveData<Boolean> isAppInForeground() {
        return isInForeground;
    }
}