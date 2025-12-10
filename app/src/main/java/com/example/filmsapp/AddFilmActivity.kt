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

