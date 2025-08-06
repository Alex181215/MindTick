package com.example.mindtick;

import static java.security.AccessController.getContext;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.Toast;
import android.widget.Toolbar;

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
        b = ActivityMainBinding.inflate(getLayoutInflater());
        View v = b.getRoot();
        setContentView(v);

        checkAndRequestPermissions();

        DrawerLayout drawerLayout = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.nav_view);

        b.btnSettings.setOnClickListener(view -> drawerLayout.openDrawer(GravityCompat.END));

        // Если приложение открылось после сплэшскрина, проверяем есть ли активные задачи на сегодня
        boolean isTaskForToday = hasTasksForToday();

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
        });

        Toast.makeText(this, "Добро пожаловать!!!", Toast.LENGTH_SHORT).show();

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
        // Заменяем текущий фрагмент
        transaction.replace(R.id.fragment_container, fragment);
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
            }
        }
    }
}