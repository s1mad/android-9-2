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

