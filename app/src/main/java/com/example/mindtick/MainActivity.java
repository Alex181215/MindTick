package com.example.mindtick;

import static java.security.AccessController.getContext;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.Toast;
import android.widget.Toolbar;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.example.mindtick.databinding.ActivityMainBinding;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import Data.DatabaseHandler;
import Utils.Util;
import android.app.AlarmManager;
import android.Manifest;


public class MainActivity extends AppCompatActivity{
    private ActivityMainBinding b;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Проверяем, показывали ли уже диалог с разрешениями
        SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        boolean shown = prefs.getBoolean("permissions_dialog_shown", false);
        if (!shown) {
            showPermissionsDialog();

            // Сохраняем, что диалог уже показан
            prefs.edit().putBoolean("permissions_dialog_shown", true).apply();
        }


        b = ActivityMainBinding.inflate(getLayoutInflater());
        View v = b.getRoot();
        setContentView(v);

        checkAndRequestPermissions();

        DrawerLayout drawerLayout = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.nav_view);

        b.btnSettings.setOnClickListener(view -> drawerLayout.openDrawer(GravityCompat.END));

        loadFragment(new TodayFragment(), "TodayFragment");

        // Если приложение открылось после сплэшскрина, проверяем есть ли активные задачи на сегодня
        boolean isTaskForToday = true;

        // Если передан Intent, используем его значение, иначе проверяем наличие задач на сегодня
        if (getIntent().hasExtra("isTaskForToday")) {
            isTaskForToday = getIntent().getBooleanExtra("isTaskForToday", false);
        }

        // Переход на нужный фрагмент в зависимости от полученного значения
        if (isTaskForToday) {
            loadFragment(new TodayFragment(), "TodayFragment");
        } else {
            loadFragment(new AllTasksFragment(), "AllTasksFragment");
        }

        b.btnToDay.setOnClickListener(view -> loadFragment(new TodayFragment(), "TodayFragment"));
        b.btnActive.setOnClickListener(view -> loadFragment(new AllTasksFragment(), "AllTasksFragment"));
        b.btnCompleted.setOnClickListener(view -> loadFragment(new DoneFragment(), "DoneFragment"));

        b.btnAddTask.setOnClickListener(view -> {
            startActivity(new Intent(this, NewTask.class));
            overridePendingTransition(R.anim.fade_in_activity, R.anim.fade_out_activity);
        });
    }

    // Обработка ответа на запрос разрешений
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // Обработка разрешений
        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Разрешение на уведомления предоставлено", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Для работы приложения нужны уведомления. Перейдите в настройки.", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                startActivity(intent);
                overridePendingTransition(R.anim.fade_in_activity, R.anim.fade_out_activity);
            }
        }

        // Обработка разрешения на будильники
        if (requestCode == 2) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Разрешение на будильники предоставлено", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Для работы будильников необходимо разрешение.", Toast.LENGTH_LONG).show();
            }
        }
    }


    private void showPermissionsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Настройки для корректной работы")
                .setMessage("Чтобы напоминания работали всегда, включите автозапуск, фон и отключите экономию батареи.")
                .setPositiveButton("Сделать сейчас", (dialog, which) -> {
                    requestIgnoreBatteryOptimizations();
                    openAutoStartSettings();

                    // Сохраняем, что уже показывали
                    SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
                    prefs.edit().putBoolean("permissions_dialog_shown", true).apply();
                })
                .setNegativeButton("Позже", (dialog, which) -> {
                    SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
                    prefs.edit().putBoolean("permissions_dialog_shown", true).apply();
                })
                .show();
    }

    private void requestIgnoreBatteryOptimizations() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            overridePendingTransition(R.anim.fade_in_activity, R.anim.fade_out_activity);
        }
    }

    private void openAutoStartSettings() {
        try {
            Intent intent = new Intent();
            String manufacturer = android.os.Build.MANUFACTURER.toLowerCase();
            if (manufacturer.contains("xiaomi")) {
                intent.setComponent(new ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                ));
            } else if (manufacturer.contains("realme") || manufacturer.contains("oppo")) {
                intent.setComponent(new ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.startupapp.StartupAppListActivity"
                ));
            } else if (manufacturer.contains("huawei")) {
                intent.setComponent(new ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                ));
            }
            startActivity(intent);
            overridePendingTransition(R.anim.fade_in_activity, R.anim.fade_out_activity);
        } catch (Exception e) {
            Toast.makeText(this, "Откройте настройки автозапуска вручную", Toast.LENGTH_LONG).show();
        }
    }



    private boolean hasTasksForToday() {
        // Получаем доступ к базе данных через DatabaseHandler
        DatabaseHandler dbHandler = new DatabaseHandler(this);  // getContext() вернет контекст фрагмента
        SQLiteDatabase database = dbHandler.getReadableDatabase();

        // Получаем текущую дату
        String currentDate = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(new Date());

        // Запрос для получения активных задач на сегодняшний день
        Cursor cursor = database.query(Util.TABLE_NAME,
                new String[]{Util.KEY_ID},
                Util.KEY_DATE + " = ? AND " + Util.KEY_STATUS + " = ?",
                new String[]{currentDate, "1"}, // Статус 1 = активная задача
                null, null, null);

        boolean hasTasks = cursor.getCount() > 0;
        cursor.close();
        database.close();

        return hasTasks;
    }



    private void loadFragment(Fragment fragment, String imageText) {
        // Начинаем транзакцию
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

        transaction.setCustomAnimations(
                R.anim.fade_in_activity,  // анимация появления фрагмента
                R.anim.fade_out_activity, // анимация исчезновения при замене
                R.anim.fade_in_activity,  // анимация появления при возврате назад
                R.anim.fade_out_activity  // анимация исчезновения при возврате назад
        );

        // Заменяем текущий фрагмент
        transaction.replace(R.id.fragment_container, fragment, imageText);
        // Добавляем транзакцию в стек для возможности возврата
        transaction.addToBackStack(null);
        // Применяем изменения
        transaction.commit();

        setImage(imageText);
    }

    private void setImage(String imageText){
        if(imageText.equals("TodayFragment")){
            imageClean();
            b.imageTodayW.setVisibility(View.VISIBLE);
            b.imageActive.setVisibility(View.VISIBLE);
            b.imageDone.setVisibility(View.VISIBLE);

            b.textTodayW.setVisibility(View.VISIBLE);
            b.textActive.setVisibility(View.VISIBLE);
            b.textDone.setVisibility(View.VISIBLE);

            b.tvTitle.setText("Задачи на сегодня");
        } else if(imageText.equals("AllTasksFragment")){
            imageClean();
            b.imageToday.setVisibility(View.VISIBLE);
            b.imageActiveW.setVisibility(View.VISIBLE);
            b.imageDone.setVisibility(View.VISIBLE);

            b.textToday.setVisibility(View.VISIBLE);
            b.textActiveW.setVisibility(View.VISIBLE);
            b.textDone.setVisibility(View.VISIBLE);

            b.tvTitle.setText("Все задачи");
        } else if(imageText.equals("DoneFragment")){
            imageClean();
            b.imageToday.setVisibility(View.VISIBLE);
            b.imageActive.setVisibility(View.VISIBLE);
            b.imageDoneW.setVisibility(View.VISIBLE);

            b.textToday.setVisibility(View.VISIBLE);
            b.textActive.setVisibility(View.VISIBLE);
            b.textDoneW.setVisibility(View.VISIBLE);

            b.tvTitle.setText("Выполненые задачи");
        }
    }

    private void imageClean(){
        b.imageToday.setVisibility(View.GONE);
        b.imageTodayW.setVisibility(View.GONE);
        b.textToday.setVisibility(View.GONE);
        b.textTodayW.setVisibility(View.GONE);

        b.imageActive.setVisibility(View.GONE);
        b.imageActiveW.setVisibility(View.GONE);
        b.textActive.setVisibility(View.GONE);
        b.textActiveW.setVisibility(View.GONE);

        b.imageDone.setVisibility(View.GONE);
        b.imageDoneW.setVisibility(View.GONE);
        b.textDone.setVisibility(View.GONE);
        b.textDoneW.setVisibility(View.GONE);
    }

    private void checkAndRequestPermissions() {
        // Проверка разрешения на уведомления для Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {

                Toast.makeText(this, "Необходимы разрешения на уведомления", Toast.LENGTH_SHORT).show();

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        1);  // Код запроса
            }
        }

        // Проверка разрешения на точные будильники для Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {

                Toast.makeText(this, "Требуется разрешение на точные напоминания", Toast.LENGTH_SHORT).show();

                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                overridePendingTransition(R.anim.fade_in_activity, R.anim.fade_out_activity);
            }
        }
    }

    @Override
    public void onBackPressed() {
        String currentFragmentTag = getCurrentFragmentTag(); // метод, который возвращает тэг текущего фрагмента

        switch (currentFragmentTag) {
            case "DoneFragment": // Выполнено
                loadFragment(new AllTasksFragment(), "AllTasksFragment");
                break;

            case "AllTasksFragment": // Все задачи
                loadFragment(new TodayFragment(), "TodayFragment");
                break;

            case "TodayFragment": // Сегодня
                moveTaskToBack(true); // сворачиваем приложение
                break;

            default:
                super.onBackPressed();
        }
    }

    private String getCurrentFragmentTag() {
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (currentFragment != null) {
            return currentFragment.getTag();
        }
        return null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == EditTask.REQUEST_EDIT_TASK && resultCode == RESULT_OK && data != null) {
            long updatedId = data.getLongExtra("task_id", -1);
            String returnFragment = data.getStringExtra("returnFragment");
            // обновляем список и показываем нужный фрагмент
            if (returnFragment != null) {
                loadFragmentByTag(returnFragment);
            } else {
                // просто обновить текущий фрагмент
                refreshCurrentFragment();
            }
        }
    }

    private void refreshCurrentFragment() {
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (currentFragment != null) {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.detach(currentFragment);
            transaction.attach(currentFragment);
            transaction.commit();
        }
    }

    public void loadFragmentByTag(String tag) {
        Fragment fragment;
        switch (tag) {
            case "TodayFragment":
                fragment = new TodayFragment();
                break;
            case "AllTasksFragment":
                fragment = new AllTasksFragment();
                break;
            case "DoneFragment":
                fragment = new DoneFragment();
                break;
            default:
                fragment = new TodayFragment();
                break;
        }
        loadFragment(fragment, tag);
    }

}