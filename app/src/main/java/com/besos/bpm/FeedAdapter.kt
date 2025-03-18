package com.besos.bpm

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.ScaleAnimation
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class FeedAdapter(
    private val markerList: List<MarkerItem>,
    private val onMarkerClickListener: (MarkerItem) -> Unit // Listener para clics
) : RecyclerView.Adapter<FeedAdapter.FeedViewHolder>() {

    inner class FeedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val feedImage: ImageView = itemView.findViewById(R.id.feedImage)
        val feedDescription: TextView = itemView.findViewById(R.id.feedDescription)
        val feedDate: TextView = itemView.findViewById(R.id.feedDate)

        init {
            // Configurar el listener para el clic en el ítem
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onMarkerClickListener(markerList[position]) // Notificar clic
                }
            }

            // Configurar el listener para la presión prolongada
            itemView.setOnLongClickListener { view ->
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    // Animación para aumentar el tamaño de la polaroid
                    val scaleUp = ScaleAnimation(
                        1f, 1.2f, // Escala X inicial y final
                        1f, 1.2f, // Escala Y inicial y final
                        ScaleAnimation.RELATIVE_TO_SELF, 0.5f, // Punto de ancla en X
                        ScaleAnimation.RELATIVE_TO_SELF, 0.5f  // Punto de ancla en Y
                    ).apply {
                        duration = 300 // Duración de la animación en milisegundos
                        fillAfter = true // Mantener el estado final de la animación
                    }

                    // Aplicar la animación al ítem
                    view.startAnimation(scaleUp)

                    // Restaurar el tamaño después de un tiempo
                    view.postDelayed({
                        val scaleDown = ScaleAnimation(
                            1.2f, 1f, // Escala X inicial y final
                            1.2f, 1f, // Escala Y inicial y final
                            ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                            ScaleAnimation.RELATIVE_TO_SELF, 0.5f
                        ).apply {
                            duration = 600
                            fillAfter = true
                        }
                        view.startAnimation(scaleDown)
                    }, 1000) // Restaurar después de 1 segundo

                    true // Indicar que se ha manejado el evento
                } else {
                    false
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_feed, parent, false)
        return FeedViewHolder(view)
    }

    override fun onBindViewHolder(holder: FeedViewHolder, position: Int) {
        val marker = markerList[position]

        Glide.with(holder.itemView.context)
            .load(marker.imageUrl)
            .placeholder(R.drawable.ic_placeholder_image)
            .error(R.drawable.ic_placeholder_image)
            .into(holder.feedImage)

        holder.feedDescription.text = marker.description
        holder.feedDate.text = marker.date
    }

    override fun getItemCount(): Int = markerList.size
}