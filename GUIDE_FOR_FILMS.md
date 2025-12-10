# Гайд по созданию Android-приложения "Трекер фильмов"

## Обзор проекта

Приложение для трекинга фильмов с возможностью:
- Просмотра списка фильмов
- Добавления фильма с метаданными (название, год, режиссёр, статус)
- Редактирования фильма
- Удаления фильма
- Статусы: "Хочу посмотреть", "Смотрю", "Бросил", "Посмотрел"

## Структура проекта

### Android приложение

Все Kotlin файлы в `app/src/main/java/com/example/filmtracker/` без подпапок:
- `Film.kt` - модель данных (Parcelable)
- `FilmApi.kt` - Retrofit interface
- `ApiClient.kt` - singleton для Retrofit
- `FilmAdapter.kt` - RecyclerView adapter
- `MainActivity.kt` - список фильмов
- `AddFilmActivity.kt` - добавление фильма
- `DetailActivity.kt` - просмотр/редактирование/удаление

### Python API

- `api/main.py` - FastAPI приложение
- `api/Dockerfile` - Docker образ
- `api/docker-compose.yml` - Docker Compose конфигурация

## Критически важные моменты (решенные проблемы)

### 1. Cleartext трафик для эмулятора

**Проблема**: Android по умолчанию блокирует HTTP трафик (только HTTPS).

**Решение**:

1. Создать `app/src/main/res/xml/network_security_config.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">10.0.2.2</domain>
    </domain-config>
</network-security-config>
```

2. В `AndroidManifest.xml` добавить в `<application>`:
```xml
android:networkSecurityConfig="@xml/network_security_config"
```

3. В `ApiClient.kt` использовать:
```kotlin
private const val BASE_URL = "http://10.0.2.2:5001/"
```
(Порт 5001, т.к. 5000 часто занят на macOS)

### 2. Отступы под системные бары (WindowInsets)

**Проблема**: С Android 16+ `enableEdgeToEdge()` включен по умолчанию, контент уходит под status bar и navigation bar.

**Решение**:

1. В layout'ах корневой элемент должен иметь `id`:
```xml
<androidx.constraintlayout.widget.ConstraintLayout
    android:id="@+id/main"
    ...>
```

2. В Activity в `onCreate()`:
```kotlin
ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
    val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
    v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
    insets
}
```

3. Для экранов с формой (AddFilmActivity, DetailActivity) использовать `NestedScrollView`:
```xml
<androidx.core.widget.NestedScrollView
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fillViewport="true">
    
    <androidx.constraintlayout.widget.ConstraintLayout
        android:id="@+id/root"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:padding="16dp">
        <!-- содержимое -->
    </androidx.constraintlayout.widget.ConstraintLayout>
</androidx.core.widget.NestedScrollView>
```

### 3. Скролл на экранах создания/редактирования

**Проблема**: В горизонтальной ориентации контент не скроллится.

**Решение**: Использовать `NestedScrollView` как обёртку (см. пункт 2).

### 4. Форматирование чисел

**Проблема**: Double отображается как `1.23E10` или `123.0` вместо `123`.

**Решение**: Использовать `BigDecimal` для форматирования:
```kotlin
private fun formatNumber(value: Double): String {
    return BigDecimal.valueOf(value)
        .stripTrailingZeros()
        .toPlainString()
}
```

### 5. Сортировка списка

**Проблема**: Нужно показывать новые элементы сверху.

**Решение**:
```kotlin
private fun sortFilms(list: List<Film>): List<Film> =
    list.sortedWith(
        compareByDescending<Film> { LocalDate.parse(it.dateAdded) }
            .thenByDescending { it.id }
    )
```

### 6. Единая модель данных

**Проблема**: Дублирование `Operation` и `OperationResponse`.

**Решение**: Использовать один класс с `@Parcelize`:
```kotlin
@Parcelize
data class Film(
    val id: Int? = null,
    val title: String,
    val year: Int,
    val director: String,
    val status: String, // "want", "watching", "dropped", "watched"
    val dateAdded: String,
    val note: String
) : Parcelable
```

### 7. Логирование для отладки

**Решение**: Добавить логирование во все критические места:

```kotlin
import android.util.Log

// В API calls
Log.d("MainActivity", "loadFilms request")
Log.d("MainActivity", "loadFilms onResponse code=${response.code()} bodySize=${response.body()?.size}")

// В ошибках
Log.e("MainActivity", "loadFilms onFailure", t)
```

Добавить HTTP logging interceptor в `ApiClient.kt`:
```kotlin
import okhttp3.logging.HttpLoggingInterceptor

val logging = HttpLoggingInterceptor().apply {
    level = HttpLoggingInterceptor.Level.BODY
}

val client = OkHttpClient.Builder()
    .addInterceptor(logging)
    .build()

private val retrofit: Retrofit = Retrofit.Builder()
    .baseUrl(BASE_URL)
    .client(client)
    .addConverterFactory(GsonConverterFactory.create(gson))
    .build()
```

### 8. Сохранение состояния (onSaveInstanceState)

**Проблема**: При повороте экрана данные теряются.

**Решение**:
```kotlin
override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putParcelableArrayList(KEY_FILMS, ArrayList(films))
}

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // ...
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
```

### 9. Адаптивность для разных экранов

**Решение**:
- Использовать `ConstraintLayout` для всех layout'ов
- Использовать `dp` для размеров, `sp` для текста
- Использовать `0dp` (match_constraint) вместо `match_parent` в ConstraintLayout
- Добавить margins для отступов между элементами

## Пошаговая реализация

### Шаг 1: Зависимости

В `gradle/libs.versions.toml`:
```toml
[versions]
retrofit = "2.9.0"
gson = "2.10.1"
recyclerview = "1.3.2"
lifecycle = "2.8.6"
cardview = "1.0.0"
okhttp = "4.12.0"

[libraries]
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
retrofit-gson = { group = "com.squareup.retrofit2", name = "converter-gson", version.ref = "retrofit" }
gson = { group = "com.google.code.gson", name = "gson", version.ref = "gson" }
androidx-recyclerview = { group = "androidx.recyclerview", name = "recyclerview", version.ref = "recyclerview" }
androidx-lifecycle-viewmodel = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-ktx", version.ref = "lifecycle" }
androidx-lifecycle-livedata = { group = "androidx.lifecycle", name = "lifecycle-livedata-ktx", version.ref = "lifecycle" }
androidx-cardview = { group = "androidx.cardview", name = "cardview", version.ref = "cardview" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-logging = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }
```

В `app/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-parcelize")
}

dependencies {
    // ... существующие зависимости
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.gson)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.cardview)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
}
```

### Шаг 2: Permissions в AndroidManifest.xml

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<application
    android:networkSecurityConfig="@xml/network_security_config"
    ...>
    <activity android:name=".ui.MainActivity" ... />
    <activity android:name=".ui.AddFilmActivity" android:exported="false" />
    <activity android:name=".ui.DetailActivity" android:exported="false" />
</application>
```

### Шаг 3: Модель данных

`app/src/main/java/com/example/filmtracker/Film.kt`:
```kotlin
package com.example.filmtracker

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Film(
    val id: Int? = null,
    val title: String,
    val year: Int,
    val director: String,
    val status: String, // "want", "watching", "dropped", "watched"
    val dateAdded: String,
    val note: String
) : Parcelable
```

### Шаг 4: API слой

`app/src/main/java/com/example/filmtracker/ApiClient.kt`:
```kotlin
package com.example.filmtracker

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

`app/src/main/java/com/example/filmtracker/FilmApi.kt`:
```kotlin
package com.example.filmtracker

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

### Шаг 5: Layouts

`app/src/main/res/layout/activity_main.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/main"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:context=".ui.MainActivity">

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/recyclerViewFilms"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:layout_marginTop="16dp"
        app:layout_constraintBottom_toTopOf="@+id/fabAdd"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent"
        tools:listitem="@layout/item_film" />

    <com.google.android.material.floatingactionbutton.FloatingActionButton
        android:id="@+id/fabAdd"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_margin="16dp"
        android:contentDescription="Добавить фильм"
        android:src="@android:drawable/ic_input_add"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:tint="@android:color/white" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

`app/src/main/res/layout/activity_add_film.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.core.widget.NestedScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fillViewport="true"
    tools:context=".ui.AddFilmActivity">

    <androidx.constraintlayout.widget.ConstraintLayout
        android:id="@+id/root"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:padding="16dp">

        <TextView
            android:id="@+id/textViewTitle"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Добавить фильм"
            android:textSize="24sp"
            android:textStyle="bold"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toTopOf="parent" />

        <TextView
            android:id="@+id/textViewFilmTitleLabel"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Название:"
            android:textSize="16sp"
            android:layout_marginTop="24dp"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toBottomOf="@+id/textViewTitle" />

        <EditText
            android:id="@+id/editTextFilmTitle"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:hint="Введите название фильма"
            android:inputType="text"
            android:layout_marginTop="8dp"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toBottomOf="@+id/textViewFilmTitleLabel" />

        <TextView
            android:id="@+id/textViewYearLabel"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Год:"
            android:textSize="16sp"
            android:layout_marginTop="16dp"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toBottomOf="@+id/editTextFilmTitle" />

        <EditText
            android:id="@+id/editTextYear"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:hint="Год выпуска"
            android:inputType="number"
            android:layout_marginTop="8dp"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toBottomOf="@+id/textViewYearLabel" />

        <TextView
            android:id="@+id/textViewDirectorLabel"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Режиссёр:"
            android:textSize="16sp"
            android:layout_marginTop="16dp"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toBottomOf="@+id/editTextYear" />

        <EditText
            android:id="@+id/editTextDirector"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:hint="Введите режиссёра"
            android:inputType="text"
            android:layout_marginTop="8dp"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toBottomOf="@+id/textViewDirectorLabel" />

        <TextView
            android:id="@+id/textViewStatusLabel"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Статус:"
            android:textSize="16sp"
            android:layout_marginTop="16dp"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toBottomOf="@+id/editTextDirector" />

        <Spinner
            android:id="@+id/spinnerStatus"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toBottomOf="@+id/textViewStatusLabel" />

        <TextView
            android:id="@+id/textViewNoteLabel"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Заметка:"
            android:textSize="16sp"
            android:layout_marginTop="16dp"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toBottomOf="@+id/spinnerStatus" />

        <EditText
            android:id="@+id/editTextNote"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:hint="Введите заметку"
            android:inputType="textMultiLine"
            android:minLines="3"
            android:layout_marginTop="8dp"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toBottomOf="@+id/textViewNoteLabel" />

        <Button
            android:id="@+id/buttonSave"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:text="Сохранить"
            android:layout_marginTop="24dp"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toBottomOf="@+id/editTextNote" />

    </androidx.constraintlayout.widget.ConstraintLayout>

</androidx.core.widget.NestedScrollView>
```

`app/src/main/res/layout/item_film.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.cardview.widget.CardView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_margin="8dp"
    app:cardCornerRadius="8dp"
    app:cardElevation="4dp">

    <androidx.constraintlayout.widget.ConstraintLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:padding="16dp">

        <TextView
            android:id="@+id/textViewTitle"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:text="Название фильма"
            android:textStyle="bold"
            android:textSize="18sp"
            app:layout_constraintEnd_toStartOf="@+id/textViewStatus"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toTopOf="parent" />

        <TextView
            android:id="@+id/textViewStatus"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Статус"
            android:textSize="14sp"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintTop_toTopOf="parent" />

        <TextView
            android:id="@+id/textViewYear"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Год"
            android:textSize="14sp"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toBottomOf="@+id/textViewTitle" />

        <TextView
            android:id="@+id/textViewDirector"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:text="Режиссёр"
            android:textSize="14sp"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintStart_toEndOf="@+id/textViewYear"
            app:layout_constraintTop_toTopOf="@+id/textViewYear" />

        <TextView
            android:id="@+id/textViewNote"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:text="Заметка"
            android:textSize="12sp"
            android:layout_marginTop="8dp"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toBottomOf="@+id/textViewYear" />

    </androidx.constraintlayout.widget.ConstraintLayout>

</androidx.cardview.widget.CardView>
```

### Шаг 6: Python API

`api/main.py`:
```python
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List
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
    year: int
    director: str
    status: str
    dateAdded: str
    note: str

class FilmResponse(BaseModel):
    id: int
    title: str
    year: int
    director: str
    status: str
    dateAdded: str
    note: str

class FilmUpdate(BaseModel):
    title: str
    year: int
    director: str
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

`api/Dockerfile`:
```dockerfile
FROM python:3.11-slim

WORKDIR /app

COPY main.py .

RUN pip install --no-cache-dir fastapi uvicorn

EXPOSE 5001

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "5001"]
```

`api/docker-compose.yml`:
```yaml
version: '3.8'

services:
  api:
    build: .
    ports:
      - "5001:5001"
    container_name: film-api
```

### Шаг 7: Запуск API

```bash
cd api
docker compose up --build
```

## Чеклист перед запуском

- [ ] Создан `network_security_config.xml` с разрешением cleartext для `10.0.2.2`
- [ ] В `AndroidManifest.xml` добавлен `networkSecurityConfig` и все permissions
- [ ] Все Activity зарегистрированы в манифесте
- [ ] В layout'ах корневые элементы имеют `id` для WindowInsets
- [ ] В Activity добавлена обработка WindowInsets
- [ ] Экраны с формами обёрнуты в `NestedScrollView`
- [ ] Используется один класс модели с `@Parcelize`
- [ ] Добавлено логирование в API calls
- [ ] Реализовано сохранение состояния через `onSaveInstanceState`
- [ ] API запущен на порту 5001
- [ ] В `ApiClient` указан правильный `BASE_URL`

## Частые ошибки и решения

1. **403 Forbidden при запросах**: Проверить, что API запущен и порт правильный (5001, не 5000)
2. **Контент под системными барами**: Проверить WindowInsets обработку
3. **Нет скролла в горизонтальной ориентации**: Использовать `NestedScrollView`
4. **Данные теряются при повороте**: Реализовать `onSaveInstanceState`
5. **Числа отображаются с E**: Использовать `BigDecimal.stripTrailingZeros().toPlainString()`

