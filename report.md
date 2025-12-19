# Отчёт по проекту "Трекер фильмов"

## 1. Цель проекта

Разработать Android-приложение для трекинга фильмов с возможностью:
- Просмотра списка фильмов
- Добавления фильма с метаданными
- Редактирования фильма
- Удаления фильма
- Отслеживания статуса просмотра

## 2. Архитектура проекта

Проект состоит из двух основных компонентов:
- **Android-приложение** (Kotlin) - клиентская часть
- **Python API** (FastAPI) - серверная часть

Приложение использует архитектуру клиент-сервер с REST API для обмена данными.

## 3. Структура проекта

### 3.1 Android-приложение

Все Kotlin файлы расположены в пакете `com.example.filmsapp`:
- `Film.kt` - модель данных
- `FilmApi.kt` - интерфейс Retrofit для API
- `ApiClient.kt` - конфигурация Retrofit клиента
- `FilmAdapter.kt` - адаптер для RecyclerView
- `MainActivity.kt` - главный экран со списком фильмов
- `AddFilmActivity.kt` - экран добавления фильма
- `DetailActivity.kt` - экран просмотра/редактирования/удаления

### 3.2 Python API

Файлы в директории `api/`:
- `main.py` - FastAPI приложение
- `Dockerfile` - конфигурация Docker образа
- `docker-compose.yml` - конфигурация Docker Compose

## 4. Модель данных

### Листинг 1. Film.kt - модель данных фильма

```kotlin
package com.example.filmsapp

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Film(
    val id: Int? = null,
    val title: String,
    val year: Int? = null,
    val director: String? = null,
    val status: String, // "want", "watching", "dropped", "watched"
    val dateAdded: String,
    val note: String
) : Parcelable
```

Модель `Film` представляет данные о фильме. Класс помечен аннотацией `@Parcelize` для возможности передачи между Activity через Intent. Поля `year` и `director` являются опциональными (nullable), обязательным является только `title`.

## 5. API слой

### Листинг 2. FilmApi.kt - интерфейс Retrofit

```kotlin
package com.example.filmsapp

import retrofit2.Call
import retrofit2.http.*

interface FilmApi {
    @GET("films")
    fun getFilms(): Call<List<Film>>

    @POST("films")
    fun createFilm(@Body film: Film): Call<Film>

    @GET("films/{id}")
    fun getFilm(@Path("id") id: Int): Call<Film>

    @PUT("films/{id}")
    fun updateFilm(@Path("id") id: Int, @Body film: Film): Call<Film>

    @DELETE("films/{id}")
    fun deleteFilm(@Path("id") id: Int): Call<Unit>
}
```

Интерфейс определяет все необходимые HTTP методы для работы с API: получение списка, создание, получение по ID, обновление и удаление фильма.

### Листинг 3. ApiClient.kt - конфигурация Retrofit

```kotlin
package com.example.filmsapp

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    private const val BASE_URL = "http://10.0.2.2:5001/"

    private val gson: Gson = GsonBuilder()
        .setLenient()
        .create()

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(logging)
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    val filmApi: FilmApi = retrofit.create(FilmApi::class.java)
}
```

`ApiClient` - singleton объект, который настраивает Retrofit клиент. Используется адрес `10.0.2.2:5001` для доступа к API на эмуляторе Android. Добавлен HTTP logging interceptor для отладки запросов.

## 6. Пользовательский интерфейс

### 6.1 Главный экран (MainActivity)

### Листинг 4. MainActivity.kt - главный экран приложения

```kotlin
package com.example.filmsapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.time.LocalDate

class MainActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FilmAdapter
    private val films = mutableListOf<Film>()
    private val KEY_FILMS = "films"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupWindowInsets()

        recyclerView = findViewById(R.id.recyclerViewFilms)
        adapter = FilmAdapter(films) { film ->
            val intent = Intent(this, DetailActivity::class.java)
            intent.putExtra("film", film)
            startActivity(intent)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        val fabAdd = findViewById<FloatingActionButton>(R.id.fabAdd)
        fabAdd.setOnClickListener {
            val intent = Intent(this, AddFilmActivity::class.java)
            startActivity(intent)
        }

        if (savedInstanceState != null) {
            val savedFilms = savedInstanceState.getParcelableArrayList<Film>(KEY_FILMS)
            if (savedFilms != null) {
                films.clear()
                films.addAll(savedFilms)
                adapter.notifyDataSetChanged()
            }
        } else {
            loadFilms()
        }
    }

    override fun onResume() {
        super.onResume()
        loadFilms()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelableArrayList(KEY_FILMS, ArrayList(films))
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun loadFilms() {
        Log.d("MainActivity", "loadFilms request")
        ApiClient.filmApi.getFilms().enqueue(object : Callback<List<Film>> {
            override fun onResponse(
                call: Call<List<Film>>,
                response: Response<List<Film>>
            ) {
                Log.d(
                    "MainActivity",
                    "loadFilms onResponse code=${response.code()} bodySize=${response.body()?.size}"
                )
                if (response.isSuccessful) {
                    val filmsList = response.body() ?: emptyList()
                    val sortedFilms = sortFilms(filmsList)
                    films.clear()
                    films.addAll(sortedFilms)
                    adapter.notifyDataSetChanged()
                } else {
                    Log.e("MainActivity", "loadFilms error: ${response.code()}")
                    Toast.makeText(
                        this@MainActivity,
                        "Ошибка загрузки: ${response.code()}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onFailure(call: Call<List<Film>>, t: Throwable) {
                Log.e("MainActivity", "loadFilms onFailure", t)
                Toast.makeText(
                    this@MainActivity,
                    "Ошибка сети: ${t.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun sortFilms(list: List<Film>): List<Film> =
        list.sortedWith(
            compareByDescending<Film> { LocalDate.parse(it.dateAdded) }
                .thenByDescending { it.id ?: 0 }
        )
}
```

`MainActivity` реализует:
- Отображение списка фильмов в RecyclerView
- Обработку WindowInsets для корректного отображения под системными барами
- Сохранение состояния при повороте экрана через `onSaveInstanceState`
- Сортировку фильмов по дате добавления (новые сверху)
- Загрузку данных при возврате на экран (`onResume`)
- Переход на экраны добавления и редактирования

### 6.2 Экран добавления фильма

### Листинг 5. AddFilmActivity.kt - экран добавления фильма

```kotlin
package com.example.filmsapp

import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.time.LocalDate

class AddFilmActivity : AppCompatActivity() {
    private lateinit var editTextTitle: EditText
    private lateinit var editTextYear: EditText
    private lateinit var editTextDirector: EditText
    private lateinit var spinnerStatus: Spinner
    private lateinit var editTextNote: EditText
    private lateinit var buttonSave: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_film)

        setupWindowInsets()

        editTextTitle = findViewById(R.id.editTextFilmTitle)
        editTextYear = findViewById(R.id.editTextYear)
        editTextDirector = findViewById(R.id.editTextDirector)
        spinnerStatus = findViewById(R.id.spinnerStatus)
        editTextNote = findViewById(R.id.editTextNote)
        buttonSave = findViewById(R.id.buttonSave)

        setupSpinner()
        setupSaveButton()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val horizontalPadding = resources.getDimensionPixelSize(R.dimen.default_horizontal_padding)
            v.setPadding(
                horizontalPadding + systemBars.left,
                systemBars.top,
                horizontalPadding + systemBars.right,
                systemBars.bottom
            )
            insets
        }
    }

    private fun setupSpinner() {
        val statuses = listOf(
            "Хочу посмотреть" to "want",
            "Смотрю" to "watching",
            "Бросил" to "dropped",
            "Посмотрел" to "watched"
        )

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            statuses.map { it.first }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerStatus.adapter = adapter
    }

    private fun setupSaveButton() {
        buttonSave.setOnClickListener {
            val title = editTextTitle.text.toString().trim()
            val yearText = editTextYear.text.toString().trim()
            val director = editTextDirector.text.toString().trim()
            val note = editTextNote.text.toString().trim()

            if (title.isEmpty()) {
                Toast.makeText(this, "Заполните название фильма", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val year = if (yearText.isEmpty()) {
                null
            } else {
                try {
                    yearText.toInt()
                } catch (e: NumberFormatException) {
                    Toast.makeText(this, "Некорректный год", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            val directorValue = if (director.isEmpty()) null else director

            val statusValues = listOf("want", "watching", "dropped", "watched")
            val selectedPosition = spinnerStatus.selectedItemPosition
            val status = statusValues[selectedPosition]

            val dateAdded = LocalDate.now().toString()

            val film = Film(
                title = title,
                year = year,
                director = directorValue,
                status = status,
                dateAdded = dateAdded,
                note = note
            )

            createFilm(film)
        }
    }

    private fun createFilm(film: Film) {
        Log.d("AddFilmActivity", "createFilm request: ${film.title}")
        ApiClient.filmApi.createFilm(film).enqueue(object : Callback<Film> {
            override fun onResponse(
                call: Call<Film>,
                response: Response<Film>
            ) {
                Log.d("AddFilmActivity", "createFilm onResponse code=${response.code()}")
                if (response.isSuccessful) {
                    Toast.makeText(this@AddFilmActivity, "Фильм добавлен", Toast.LENGTH_SHORT)
                        .show()
                    finish()
                } else {
                    Log.e("AddFilmActivity", "createFilm error: ${response.code()}")
                    Toast.makeText(
                        this@AddFilmActivity,
                        "Ошибка: ${response.code()}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onFailure(call: Call<Film>, t: Throwable) {
                Log.e("AddFilmActivity", "createFilm onFailure", t)
                Toast.makeText(
                    this@AddFilmActivity,
                    "Ошибка сети: ${t.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }
}
```

Экран добавления фильма включает:
- Валидацию: обязательным является только поле "название"
- Обработку опциональных полей (год, режиссёр)
- Spinner для выбора статуса просмотра
- Автоматическое добавление текущей даты
- Обработку ошибок сети и валидации

### 6.3 Экран редактирования и удаления

### Листинг 6. DetailActivity.kt - экран просмотра/редактирования/удаления

```kotlin
package com.example.filmsapp

import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class DetailActivity : AppCompatActivity() {
    private lateinit var film: Film
    private lateinit var editTextTitle: EditText
    private lateinit var editTextYear: EditText
    private lateinit var editTextDirector: EditText
    private lateinit var spinnerStatus: Spinner
    private lateinit var editTextNote: EditText
    private lateinit var buttonSave: Button
    private lateinit var buttonDelete: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        film = intent.getParcelableExtra<Film>("film") ?: run {
            Toast.makeText(this, "Ошибка загрузки фильма", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupWindowInsets()

        editTextTitle = findViewById(R.id.editTextFilmTitle)
        editTextYear = findViewById(R.id.editTextYear)
        editTextDirector = findViewById(R.id.editTextDirector)
        spinnerStatus = findViewById(R.id.spinnerStatus)
        editTextNote = findViewById(R.id.editTextNote)
        buttonSave = findViewById(R.id.buttonSave)
        buttonDelete = findViewById(R.id.buttonDelete)

        setupSpinner()
        fillFields()
        setupSaveButton()
        setupDeleteButton()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val horizontalPadding = resources.getDimensionPixelSize(R.dimen.default_horizontal_padding)
            v.setPadding(
                horizontalPadding + systemBars.left,
                systemBars.top,
                horizontalPadding + systemBars.right,
                systemBars.bottom
            )
            insets
        }
    }

    private fun setupSpinner() {
        val statuses = listOf(
            "Хочу посмотреть" to "want",
            "Смотрю" to "watching",
            "Бросил" to "dropped",
            "Посмотрел" to "watched"
        )

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            statuses.map { it.first }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerStatus.adapter = adapter

        val statusIndex = statuses.indexOfFirst { it.second == film.status }
        if (statusIndex >= 0) {
            spinnerStatus.setSelection(statusIndex)
        }
    }

    private fun fillFields() {
        editTextTitle.setText(film.title)
        editTextYear.setText(film.year?.toString() ?: "")
        editTextDirector.setText(film.director ?: "")
        editTextNote.setText(film.note)
    }

    private fun setupSaveButton() {
        buttonSave.setOnClickListener {
            val title = editTextTitle.text.toString().trim()
            val yearText = editTextYear.text.toString().trim()
            val director = editTextDirector.text.toString().trim()
            val note = editTextNote.text.toString().trim()

            if (title.isEmpty()) {
                Toast.makeText(this, "Заполните название фильма", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val year = if (yearText.isEmpty()) {
                null
            } else {
                try {
                    yearText.toInt()
                } catch (e: NumberFormatException) {
                    Toast.makeText(this, "Некорректный год", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            val directorValue = if (director.isEmpty()) null else director

            val statusValues = listOf("want", "watching", "dropped", "watched")
            val selectedPosition = spinnerStatus.selectedItemPosition
            val status = statusValues[selectedPosition]

            val updatedFilm = Film(
                id = film.id,
                title = title,
                year = year,
                director = directorValue,
                status = status,
                dateAdded = film.dateAdded,
                note = note
            )

            updateFilm(updatedFilm)
        }
    }

    private fun setupDeleteButton() {
        buttonDelete.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Удаление фильма")
                .setMessage("Вы уверены, что хотите удалить этот фильм?")
                .setPositiveButton("Удалить") { _, _ ->
                    deleteFilm()
                }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }

    private fun updateFilm(film: Film) {
        val filmId = film.id ?: run {
            Toast.makeText(this, "Ошибка: ID фильма не найден", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d("DetailActivity", "updateFilm request: id=$filmId")
        ApiClient.filmApi.updateFilm(filmId, film).enqueue(object : Callback<Film> {
            override fun onResponse(
                call: Call<Film>,
                response: Response<Film>
            ) {
                Log.d("DetailActivity", "updateFilm onResponse code=${response.code()}")
                if (response.isSuccessful) {
                    Toast.makeText(this@DetailActivity, "Фильм обновлён", Toast.LENGTH_SHORT)
                        .show()
                    finish()
                } else {
                    Log.e("DetailActivity", "updateFilm error: ${response.code()}")
                    Toast.makeText(
                        this@DetailActivity,
                        "Ошибка: ${response.code()}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onFailure(call: Call<Film>, t: Throwable) {
                Log.e("DetailActivity", "updateFilm onFailure", t)
                Toast.makeText(
                    this@DetailActivity,
                    "Ошибка сети: ${t.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun deleteFilm() {
        val filmId = film.id ?: run {
            Toast.makeText(this, "Ошибка: ID фильма не найден", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d("DetailActivity", "deleteFilm request: id=$filmId")
        ApiClient.filmApi.deleteFilm(filmId).enqueue(object : Callback<Unit> {
            override fun onResponse(
                call: Call<Unit>,
                response: Response<Unit>
            ) {
                Log.d("DetailActivity", "deleteFilm onResponse code=${response.code()}")
                if (response.isSuccessful) {
                    Toast.makeText(this@DetailActivity, "Фильм удалён", Toast.LENGTH_SHORT)
                        .show()
                    finish()
                } else {
                    Log.e("DetailActivity", "deleteFilm error: ${response.code()}")
                    Toast.makeText(
                        this@DetailActivity,
                        "Ошибка: ${response.code()}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onFailure(call: Call<Unit>, t: Throwable) {
                Log.e("DetailActivity", "deleteFilm onFailure", t)
                Toast.makeText(
                    this@DetailActivity,
                    "Ошибка сети: ${t.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }
}
```

Экран детального просмотра реализует:
- Заполнение полей данными фильма
- Редактирование всех полей с валидацией
- Подтверждение удаления через диалог
- Обновление и удаление через API

### 6.4 Адаптер для списка

### Листинг 7. FilmAdapter.kt - адаптер RecyclerView

```kotlin
package com.example.filmsapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class FilmAdapter(
    private val films: MutableList<Film>,
    private val onItemClick: (Film) -> Unit
) : RecyclerView.Adapter<FilmAdapter.FilmViewHolder>() {

    class FilmViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textViewTitle: TextView = itemView.findViewById(R.id.textViewTitle)
        val textViewStatus: TextView = itemView.findViewById(R.id.textViewStatus)
        val textViewYear: TextView = itemView.findViewById(R.id.textViewYear)
        val textViewDirector: TextView = itemView.findViewById(R.id.textViewDirector)
        val textViewNote: TextView = itemView.findViewById(R.id.textViewNote)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilmViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_film, parent, false)
        return FilmViewHolder(view)
    }

    override fun onBindViewHolder(holder: FilmViewHolder, position: Int) {
        val film = films[position]
        holder.textViewTitle.text = film.title
        holder.textViewStatus.text = getStatusText(film.status)
        holder.textViewYear.text = film.year?.toString() ?: ""
        holder.textViewDirector.text = film.director ?: ""
        holder.textViewNote.text = film.note

        holder.itemView.setOnClickListener {
            onItemClick(film)
        }
    }

    override fun getItemCount(): Int = films.size

    fun updateFilms(newFilms: List<Film>) {
        films.clear()
        films.addAll(newFilms)
        notifyDataSetChanged()
    }

    private fun getStatusText(status: String): String {
        return when (status) {
            "want" -> "Хочу посмотреть"
            "watching" -> "Смотрю"
            "dropped" -> "Бросил"
            "watched" -> "Посмотрел"
            else -> status
        }
    }
}
```

Адаптер обеспечивает:
- Отображение данных фильма в элементах списка
- Обработку кликов для перехода к детальному просмотру
- Локализацию статусов просмотра
- Корректную обработку nullable полей

## 7. Конфигурация Android

### Листинг 8. AndroidManifest.xml - манифест приложения

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:networkSecurityConfig="@xml/network_security_config"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.FilmsApp">
        
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        
        <activity
            android:name=".AddFilmActivity"
            android:exported="false" />
        
        <activity
            android:name=".DetailActivity"
            android:exported="false" />
    </application>

</manifest>
```

В манифесте зарегистрированы:
- Разрешения для интернета и уведомлений
- Конфигурация сетевой безопасности для разрешения HTTP трафика
- Все три Activity приложения

### Листинг 9. app/build.gradle.kts - зависимости проекта

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-parcelize")
}

android {
    namespace = "com.example.filmsapp"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.filmsapp"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.gson)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.cardview)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
```

Основные зависимости:
- Retrofit и Gson для работы с REST API
- RecyclerView и CardView для отображения списков
- Material Design компоненты
- OkHttp с логированием для отладки

## 8. Серверная часть (Python API)

### Листинг 10. api/main.py - FastAPI приложение

```python
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List, Optional
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("film_api")

app = FastAPI()

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

films = []
next_id = 1

class FilmCreate(BaseModel):
    title: str
    year: Optional[int] = None
    director: Optional[str] = None
    status: str
    dateAdded: str
    note: str

class FilmResponse(BaseModel):
    id: int
    title: str
    year: Optional[int] = None
    director: Optional[str] = None
    status: str
    dateAdded: str
    note: str

class FilmUpdate(BaseModel):
    title: str
    year: Optional[int] = None
    director: Optional[str] = None
    status: str
    dateAdded: str
    note: str

@app.get("/films", response_model=List[FilmResponse])
def get_films():
    logger.info(f"GET /films count={len(films)}")
    return films

@app.post("/films", response_model=FilmResponse)
def create_film(film: FilmCreate):
    global next_id
    logger.info(f"POST /films payload={film.model_dump_json()}")
    new_film = FilmResponse(
        id=next_id,
        title=film.title,
        year=film.year,
        director=film.director,
        status=film.status,
        dateAdded=film.dateAdded,
        note=film.note
    )
    films.append(new_film)
    next_id += 1
    logger.info(f"Created film id={new_film.id} total={len(films)}")
    return new_film

@app.get("/films/{film_id}", response_model=FilmResponse)
def get_film(film_id: int):
    logger.info(f"GET /films/{film_id}")
    for film in films:
        if film.id == film_id:
            return film
    raise HTTPException(status_code=404, detail="Film not found")

@app.put("/films/{film_id}", response_model=FilmResponse)
def update_film(film_id: int, film: FilmUpdate):
    logger.info(f"PUT /films/{film_id} payload={film.model_dump_json()}")
    for i, f in enumerate(films):
        if f.id == film_id:
            updated_film = FilmResponse(
                id=film_id,
                title=film.title,
                year=film.year,
                director=film.director,
                status=film.status,
                dateAdded=film.dateAdded,
                note=film.note
            )
            films[i] = updated_film
            logger.info(f"Updated film id={film_id}")
            return updated_film
    raise HTTPException(status_code=404, detail="Film not found")

@app.delete("/films/{film_id}")
def delete_film(film_id: int):
    logger.info(f"DELETE /films/{film_id}")
    for i, film in enumerate(films):
        if film.id == film_id:
            films.pop(i)
            logger.info(f"Deleted film id={film_id} total={len(films)}")
            return {"message": "Film deleted"}
    raise HTTPException(status_code=404, detail="Film not found")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=5001)
```

API реализует полный CRUD функционал:
- GET /films - получение списка всех фильмов
- POST /films - создание нового фильма
- GET /films/{id} - получение фильма по ID
- PUT /films/{id} - обновление фильма
- DELETE /films/{id} - удаление фильма

Данные хранятся в памяти (список `films`). Для production использования необходимо подключить базу данных.

## 9. Особенности реализации

### 9.1 Обработка WindowInsets

Для корректного отображения контента под системными барами (status bar и navigation bar) используется `ViewCompat.setOnApplyWindowInsetsListener`. Это обеспечивает правильные отступы на всех версиях Android.

### 9.2 Сохранение состояния

Реализовано сохранение состояния через `onSaveInstanceState` для предотвращения потери данных при повороте экрана. Список фильмов сохраняется как `ParcelableArrayList`.

### 9.3 Сортировка

Фильмы сортируются по дате добавления (новые сверху) с дополнительной сортировкой по ID для стабильности порядка.

### 9.4 Валидация данных

Обязательным для заполнения является только поле "название". Поля "год" и "режиссёр" являются опциональными и могут быть пустыми.

### 9.5 Сетевая безопасность

Для работы с HTTP трафиком на эмуляторе создан `network_security_config.xml`, который разрешает cleartext трафик для адреса `10.0.2.2` (localhost эмулятора).

## 10. Заключение

Разработано Android-приложение для трекинга фильмов с полным функционалом CRUD операций. Приложение использует современные технологии Android разработки (Kotlin, Retrofit, RecyclerView) и следует лучшим практикам (обработка WindowInsets, сохранение состояния, валидация данных).

Серверная часть реализована на FastAPI и предоставляет REST API для работы с данными. Приложение готово к использованию и может быть расширено дополнительным функционалом (поиск, фильтрация, синхронизация с облачным хранилищем).






