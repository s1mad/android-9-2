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

        NotificationHelper.createNotificationChannel(this)
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
                    NotificationHelper.showNotification(
                        this@DetailActivity,
                        "Фильм обновлён",
                        "Фильм \"${film.title}\" успешно обновлён",
                        filmId
                    )
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
                    NotificationHelper.showNotification(
                        this@DetailActivity,
                        "Фильм удалён",
                        "Фильм \"${film.title}\" удалён из списка",
                        filmId
                    )
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

