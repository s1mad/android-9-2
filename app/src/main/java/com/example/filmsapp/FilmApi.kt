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

