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

