package com.example.wingman;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import android.content.Intent;
import android.os.CountDownTimer;

public class TimerViewModel extends AndroidViewModel {
    private CountDownTimer countDownTimer;
    private long endTime = 0;
    public MutableLiveData<Long> timeLeftInMillis = new MutableLiveData<>(1500000L);
    public MutableLiveData<Long> defaultTime = new MutableLiveData<>(1500000L);
    public MutableLiveData<Boolean> isRunning = new MutableLiveData<>(false);
    public MutableLiveData<Boolean> finished = new MutableLiveData<>(false);
    private boolean timerFinishedConsumed = false;

    public TimerViewModel(@NonNull Application application) {
        super(application);
    }

    public void startTimer() {
        if (Boolean.TRUE.equals(isRunning.getValue())) return;

        long currentTime = System.currentTimeMillis();

        if (timeLeftInMillis.getValue() == null) timeLeftInMillis.setValue(defaultTime.getValue());

        long millisLeft = timeLeftInMillis.getValue();

        if (endTime == 0 || endTime <= currentTime) {
            endTime = currentTime + millisLeft;
        } else {
            millisLeft = endTime - currentTime;
            timeLeftInMillis.setValue(millisLeft);
        }

        countDownTimer = new CountDownTimer(millisLeft, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timeLeftInMillis.postValue(millisUntilFinished);
            }

            @Override
            public void onFinish() {
                isRunning.postValue(false);
                finished.postValue(true);
                timerFinishedConsumed = false;

                endTime = 0;

                boolean isAppForeground = AppVisibilityTracker.isAppInForeground().getValue() != null &&
                        AppVisibilityTracker.isAppInForeground().getValue();
                boolean isTimerVisible = TimerFragment.isTimerFragmentVisible;

                if (!isAppForeground || !isTimerVisible) {
                    try {
                        Intent intent = new Intent("com.example.wingman.ACTION_TIMER_FINISHED");
                        intent.setPackage(getApplication().getPackageName());
                        getApplication().sendBroadcast(intent);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    timerFinishedConsumed = true;
                }

                timeLeftInMillis.postValue(0L);
                isRunning.postValue(false);
                finished.postValue(true);
            }
        }.start();

        isRunning.postValue(true);
    }

    public void pauseTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
        long currentTime = System.currentTimeMillis();
        if (endTime > currentTime) {
            long millisLeft = endTime - currentTime;
            timeLeftInMillis.setValue(millisLeft);
        } else {
            timeLeftInMillis.setValue(0L);
            finished.setValue(true);
            timerFinishedConsumed = false;
        }

        isRunning.postValue(false);
        endTime = 0;
    }

    public void resetTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
        timeLeftInMillis.postValue(defaultTime.getValue());
        isRunning.postValue(false);
        finished.postValue(false);
        timerFinishedConsumed = false;
        endTime = 0;
    }

    public void switchMode(long newTime) {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
        defaultTime.postValue(newTime);
        timeLeftInMillis.postValue(newTime);
        isRunning.postValue(false);
        finished.postValue(false);
        timerFinishedConsumed = false;
        endTime = 0;
    }

    public boolean isTimerFinishedConsumed() {
        return timerFinishedConsumed;
    }

    public void setTimerFinishedConsumed(boolean consumed) {
        timerFinishedConsumed = consumed;
        if (consumed) finished.postValue(false);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }
}