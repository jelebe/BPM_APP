package com.besos.bpm

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.view.animation.AnimationSet
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import android.view.MotionEvent

class FeedAdapter(
    private val markerList: MutableList<MarkerItem>,
    private val onMarkerClickListener: (MarkerItem) -> Unit, // Listener para clics izquierdo (navegar al mapa)
    private val onFlipClickListener: (MarkerItem, Int) -> Unit // Listener para girar la polaroid
) : RecyclerView.Adapter<FeedAdapter.FeedViewHolder>() {

    inner class FeedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val feedImage: ImageView = itemView.findViewById(R.id.feedImage)
        val feedDescription: TextView = itemView.findViewById(R.id.feedDescription)
        val feedDate: TextView = itemView.findViewById(R.id.feedDate)
        val feedLongText: TextView = itemView.findViewById(R.id.feedLongText)
        val frontLayout: View = itemView.findViewById(R.id.frontLayout)
        val backLayout: View = itemView.findViewById(R.id.backLayout)
        val backTopTouchArea: View = itemView.findViewById(R.id.backTopTouchArea) // Nueva referencia

        init {
            // Configurar el toque en toda la tarjeta para el FRENTE
            itemView.setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_UP -> {
                        val position = adapterPosition
                        if (position != RecyclerView.NO_POSITION) {
                            val marker = markerList[position]
                            // Solo manejamos toques en el frente
                            if (!marker.isShowingBack) {
                                val width = view.width
                                val x = event.x

                                // Si toca en la parte derecha (último 30%) -> girar
                                if (x > width * 0.7) {
                                    flipCardHorizontal(view, true)
                                    onFlipClickListener(marker, position)
                                }
                                // Si toca en la parte izquierda (primer 70%) -> navegar al mapa
                                else {
                                    onMarkerClickListener(marker)
                                }
                            }
                        }
                        true
                    }
                    else -> false
                }
            }

            // Configurar el toque en el área superior de la parte trasera
            backTopTouchArea.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val marker = markerList[position]
                    // Solo manejamos toques en el dorso
                    if (marker.isShowingBack) {
                        flipCardHorizontal(itemView, false)
                        onFlipClickListener(marker, position)
                    }
                }
            }
        }

        // Función para animar el giro horizontal (de lado a lado)
        private fun flipCardHorizontal(view: View, toBack: Boolean) {
            val fromX = if (toBack) 0f else -1f
            val toX = if (toBack) 1f else 0f

            val animation = TranslateAnimation(
                Animation.RELATIVE_TO_SELF, fromX,
                Animation.RELATIVE_TO_SELF, toX,
                Animation.RELATIVE_TO_SELF, 0f,
                Animation.RELATIVE_TO_SELF, 0f
            )
            animation.duration = 300

            animation.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {
                    if (toBack) {
                        // Antes de girar al dorso, preparamos la vista trasera
                        backLayout.alpha = 0f
                        backLayout.visibility = View.VISIBLE
                    } else {
                        // Antes de volver al frente, preparamos la vista frontal
                        frontLayout.alpha = 0f
                        frontLayout.visibility = View.VISIBLE
                    }
                }

                override fun onAnimationEnd(animation: Animation?) {
                    // Cambiar visibilidad después de la animación
                    if (toBack) {
                        frontLayout.visibility = View.GONE
                        backLayout.alpha = 1f
                    } else {
                        backLayout.visibility = View.GONE
                        frontLayout.alpha = 1f
                    }
                    view.clearAnimation()
                }

                override fun onAnimationRepeat(animation: Animation?) {}
            })

            view.startAnimation(animation)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedViewHolder {
        // Cambiamos al nuevo layout que tendrá dos caras
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_feed_flippable, parent, false)
        return FeedViewHolder(view)
    }

    override fun onBindViewHolder(holder: FeedViewHolder, position: Int) {
        val marker = markerList[position]

        // Configurar la visibilidad de las caras
        if (marker.isShowingBack) {
            holder.frontLayout.visibility = View.GONE
            holder.backLayout.visibility = View.VISIBLE

            // Configurar el texto largo en la parte trasera
            holder.feedLongText.text = marker.longText.ifEmpty {
                " "
            }
        } else {
            holder.frontLayout.visibility = View.VISIBLE
            holder.backLayout.visibility = View.GONE

            // Configurar la parte frontal
            Glide.with(holder.itemView.context)
                .load(marker.imageUrl)
                .placeholder(R.drawable.ic_placeholder_image)
                .error(R.drawable.ic_placeholder_image)
                .into(holder.feedImage)

            holder.feedDescription.text = marker.description
            holder.feedDate.text = marker.date
        }
    }

    override fun getItemCount(): Int = markerList.size

    // Metodo para actualizar los datos del adaptador
    fun updateData(newData: List<MarkerItem>) {
        markerList.clear()
        markerList.addAll(newData)
        notifyDataSetChanged()
    }
}