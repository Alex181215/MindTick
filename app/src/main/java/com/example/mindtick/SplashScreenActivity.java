package com.example.mindtick;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;

import com.example.mindtick.MainActivity;
import com.example.mindtick.R;
import com.example.mindtick.databinding.ActivitySplashScreenBinding;

import kotlin.reflect.TypeOfKt;

public class SplashScreenActivity extends Activity {
    private ActivitySplashScreenBinding b;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivitySplashScreenBinding.inflate(getLayoutInflater());
        View v = b.getRoot();
        setContentView(v);

        // Найдем текстовые элементы
        TextView logo = findViewById(R.id.tvAppName);
        TextView slogan = findViewById(R.id.tvSlogan);

        // Анимация плавного появления
        Animation fadeIn = new AlphaAnimation(0, 1);
        fadeIn.setDuration(1500);
        logo.startAnimation(fadeIn);
        slogan.startAnimation(fadeIn);

        // Переход к главному экрану через 3 секунды
        new Handler().postDelayed(() -> {
            startActivity(new Intent(SplashScreenActivity.this, MainActivity.class));
            finish();
        }, 1000);
    }
}
