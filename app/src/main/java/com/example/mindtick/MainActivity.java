package com.example.mindtick;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.example.mindtick.databinding.ActivityMainBinding;
import com.google.android.material.navigation.NavigationView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import Data.DatabaseHandler;
import Utils.FilterType;
import Utils.Util;
import android.app.AlarmManager;
import android.Manifest;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding b;
    private ArrayAdapter<String> todayFilterAdapter;
    private ArrayAdapter<String> allTasksFilterAdapter;
    private String currentFragmentTag = "TodayFragment";
    private boolean sortAscending = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        boolean shown = prefs.getBoolean("permissions_dialog_shown", false);
        if (!shown) {
            showPermissionsDialog();
            prefs.edit().putBoolean("permissions_dialog_shown", true).apply();
        }

        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        checkAndRequestPermissions();

        DrawerLayout drawerLayout = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.nav_view);

        // Настройка адаптеров для Spinner
        String[] todayFilterOptions = {"По категориям", "По времени", "По приоритету"};
        todayFilterAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, todayFilterOptions);
        todayFilterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        String[] allTasksFilterOptions = {"По категориям", "По времени", "По дате", "По приоритету"};
        allTasksFilterAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, allTasksFilterOptions);
        allTasksFilterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        b.btnSettings.setOnClickListener(view -> drawerLayout.openDrawer(GravityCompat.END));

        // Кнопка сортировки
        updateSortButtonIcon();
        b.btnSort.setOnClickListener(v -> {
            sortAscending = !sortAscending;
            updateSortButtonIcon();
            applyFilterToCurrentFragment();
        });

        boolean isTaskForToday = hasTasksForToday();
        if (getIntent().hasExtra("isTaskForToday")) {
            isTaskForToday = getIntent().getBooleanExtra("isTaskForToday", false);
        }

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

        navigationView.setNavigationItemSelectedListener(item -> {
            b.drawerLayout.closeDrawer(GravityCompat.END);
            return true;
        });

        b.filterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
                if (currentFragment == null || currentFragment instanceof DoneFragment) {
                    Log.e("MainActivity", "Invalid fragment for filter selection: " + currentFragmentTag);
                    updateSpinnerSelection();
                    return;
                }
                FilterType filterType;
                if (currentFragment instanceof TodayFragment) {
                    switch (position) {
                        case 0:
                            filterType = FilterType.CATEGORY;
                            break;
                        case 1:
                            filterType = FilterType.TIME;
                            break;
                        case 2:
                            filterType = FilterType.PRIORITY;
                            break;
                        default:
                            filterType = FilterType.CATEGORY;
                            break;
                    }
                } else {
                    switch (position) {
                        case 0:
                            filterType = FilterType.CATEGORY;
                            break;
                        case 1:
                            filterType = FilterType.TIME;
                            break;
                        case 2:
                            filterType = FilterType.DATE;
                            break;
                        case 3:
                            filterType = FilterType.PRIORITY;
                            break;
                        default:
                            filterType = FilterType.CATEGORY;
                            break;
                    }
                }
                Log.d("MainActivity", "Selected filter position: " + position + ", filterType: " + filterType + ", sortAscending: " + sortAscending + ", fragment: " + currentFragmentTag);
                if (currentFragment instanceof AllTasksFragment) {
                    ((AllTasksFragment) currentFragment).setFilter(filterType, sortAscending);
                } else if (currentFragment instanceof TodayFragment) {
                    ((TodayFragment) currentFragment).setFilter(filterType, sortAscending);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void updateSortButtonIcon() {
        b.btnSort.setImageResource(sortAscending ? R.drawable.baseline_align_vertical_top_24 : R.drawable.baseline_align_vertical_bottom_24);
    }

    private void applyFilterToCurrentFragment() {
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (currentFragment == null) {
            Log.e("MainActivity", "Current fragment is null in applyFilterToCurrentFragment");
            return;
        }
        if (currentFragment instanceof DoneFragment) {
            ((DoneFragment) currentFragment).setSort(sortAscending);
        } else if (currentFragment instanceof AllTasksFragment) {
            AllTasksFragment fragment = (AllTasksFragment) currentFragment;
            fragment.setFilter(fragment.getCurrentFilter(), sortAscending);
        } else if (currentFragment instanceof TodayFragment) {
            TodayFragment fragment = (TodayFragment) currentFragment;
            fragment.setFilter(fragment.getCurrentFilter(), sortAscending);
        }
    }

    private void updateSpinnerSelection() {
        b.filterSpinner.post(() -> {
            Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (currentFragment == null) {
                Log.e("MainActivity", "Current fragment is null in updateSpinnerSelection");
                b.filterSpinner.setAdapter(todayFilterAdapter);
                b.filterSpinner.setSelection(0);
                b.btnSort.setVisibility(View.VISIBLE);
                return;
            }
            if (currentFragment instanceof DoneFragment) {
                b.filterSpinner.setAdapter(null);
                b.filterSpinner.setVisibility(View.INVISIBLE);
                b.btnSort.setVisibility(View.VISIBLE);
            } else if (currentFragment instanceof AllTasksFragment) {
                b.filterSpinner.setVisibility(View.VISIBLE);
                b.btnSort.setVisibility(View.VISIBLE);
                b.filterSpinner.setAdapter(allTasksFilterAdapter);
                FilterType currentFilter = ((AllTasksFragment) currentFragment).getCurrentFilter();
                int position;
                switch (currentFilter) {
                    case CATEGORY:
                        position = 0;
                        break;
                    case TIME:
                        position = 1;
                        break;
                    case DATE:
                        position = 2;
                        break;
                    case PRIORITY:
                        position = 3;
                        break;
                    default:
                        position = 0;
                        break;
                }
                b.filterSpinner.setSelection(position);
            } else if (currentFragment instanceof TodayFragment) {
                b.filterSpinner.setVisibility(View.VISIBLE);
                b.btnSort.setVisibility(View.VISIBLE);
                b.filterSpinner.setAdapter(todayFilterAdapter);
                FilterType currentFilter = ((TodayFragment) currentFragment).getCurrentFilter();
                int position;
                switch (currentFilter) {
                    case CATEGORY:
                        position = 0;
                        break;
                    case TIME:
                        position = 1;
                        break;
                    case PRIORITY:
                        position = 2;
                        break;
                    default:
                        position = 0;
                        break;
                }
                b.filterSpinner.setSelection(position);
            }
            updateSortButtonIcon();
        });
    }

    private void showPermissionsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Настройки для корректной работы")
                .setMessage("Чтобы напоминания работали всегда, включите автозапуск в фоне и отключите экономию батареи.")
                .setPositiveButton("Сделать сейчас", (dialog, which) -> {
                    requestIgnoreBatteryOptimizations();
                    openAutoStartSettings();
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
        DatabaseHandler dbHandler = new DatabaseHandler(this);
        SQLiteDatabase database = dbHandler.getReadableDatabase();
        String currentDate = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(new Date());
        Cursor cursor = database.query(Util.TABLE_NAME,
                new String[]{Util.KEY_ID},
                Util.KEY_DATE + " = ? AND " + Util.KEY_STATUS + " = ?",
                new String[]{currentDate, "1"},
                null, null, null);
        boolean hasTasks = cursor.getCount() > 0;
        cursor.close();
        database.close();
        return hasTasks;
    }

    private void loadFragment(Fragment fragment, String imageText) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.setCustomAnimations(
                R.anim.fade_in_activity,
                R.anim.fade_out_activity,
                R.anim.fade_in_activity,
                R.anim.fade_out_activity
        );
        transaction.replace(R.id.fragment_container, fragment, imageText);
        transaction.addToBackStack(null);
        transaction.commit();
        setImage(imageText);
        currentFragmentTag = imageText;
        updateSpinnerSelection();
    }

    private void setImage(String imageText) {
        if (imageText.equals("TodayFragment")) {
            imageClean();
            b.imageTodayW.setVisibility(View.VISIBLE);
            b.imageActive.setVisibility(View.VISIBLE);
            b.imageDone.setVisibility(View.VISIBLE);
            b.textTodayW.setVisibility(View.VISIBLE);
            b.textActive.setVisibility(View.VISIBLE);
            b.textDone.setVisibility(View.VISIBLE);
            b.tvTitle.setText("Задачи на сегодня");
        } else if (imageText.equals("AllTasksFragment")) {
            imageClean();
            b.imageToday.setVisibility(View.VISIBLE);
            b.imageActiveW.setVisibility(View.VISIBLE);
            b.imageDone.setVisibility(View.VISIBLE);
            b.textToday.setVisibility(View.VISIBLE);
            b.textActiveW.setVisibility(View.VISIBLE);
            b.textDone.setVisibility(View.VISIBLE);
            b.tvTitle.setText("Все задачи");
        } else if (imageText.equals("DoneFragment")) {
            imageClean();
            b.imageToday.setVisibility(View.VISIBLE);
            b.imageActive.setVisibility(View.VISIBLE);
            b.imageDoneW.setVisibility(View.VISIBLE);
            b.textToday.setVisibility(View.VISIBLE);
            b.textActive.setVisibility(View.VISIBLE);
            b.textDoneW.setVisibility(View.VISIBLE);
            b.tvTitle.setText("Выполненные задачи");
        }
    }

    private void imageClean() {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Необходимы разрешения на уведомления", Toast.LENGTH_SHORT).show();
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        1);
            }
        }

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
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
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

        if (requestCode == 2) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Разрешение на будильники предоставлено", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Для работы будильников необходимо разрешение.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onBackPressed() {
        String currentFragmentTag = getCurrentFragmentTag();
        switch (currentFragmentTag) {
            case "DoneFragment":
                loadFragment(new AllTasksFragment(), "AllTasksFragment");
                break;
            case "AllTasksFragment":
                loadFragment(new TodayFragment(), "TodayFragment");
                break;
            case "TodayFragment":
                moveTaskToBack(true);
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == EditTask.REQUEST_EDIT_TASK && resultCode == RESULT_OK && data != null) {
            long updatedId = data.getLongExtra("task_id", -1);
            String returnFragment = data.getStringExtra("returnFragment");
            if (returnFragment != null) {
                loadFragmentByTag(returnFragment);
            } else {
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