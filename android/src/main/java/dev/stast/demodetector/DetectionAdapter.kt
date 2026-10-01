package dev.stast.demodetector

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

/** One row of the detections list: algorithm, tick and the player's SteamID64. */
data class DetectionRow(val tick: Int, val algorithm: String, val steamId: String)

/**
 * Flat list of detections, sorted by tick/player/algorithm on the Rust side
 * already. Long-pressing a row copies that player's SteamID to the clipboard,
 * mirroring the desktop analyser's behaviour.
 */
class DetectionAdapter :
    ListAdapter<DetectionRow, DetectionAdapter.Holder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<DetectionRow>() {
            override fun areItemsTheSame(old: DetectionRow, new: DetectionRow) =
                old.tick == new.tick && old.steamId == new.steamId && old.algorithm == new.algorithm

            override fun areContentsTheSame(old: DetectionRow, new: DetectionRow) = old == new
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_detection, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    class Holder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        private val algorithm = view.findViewById<TextView>(R.id.detectionAlgorithm)
        private val tick = view.findViewById<TextView>(R.id.detectionTick)
        private val player = view.findViewById<TextView>(R.id.detectionPlayer)

        fun bind(row: DetectionRow) {
            algorithm.text = row.algorithm
            tick.text = row.tick.toString()
            player.text = row.steamId
            itemView.setOnLongClickListener {
                val clipboard =
                    itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("SteamID", row.steamId))
                Toast.makeText(itemView.context, R.string.steamid_copied, Toast.LENGTH_SHORT).show()
                true
            }
        }
    }
}
