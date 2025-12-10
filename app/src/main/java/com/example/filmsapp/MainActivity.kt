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

