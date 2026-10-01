package com.tf2demo.analyzer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

/** One detection: algorithm, tick and the player's SteamID64. */
data class DetectionRow(val tick: Int, val algorithm: String, val steamId: String)

/**
 * Detections grouped by algorithm with collapsible groups (mirrors the desktop
 * analyser's grouping idea). Long-pressing a detection copies that player's
 * SteamID to the clipboard.
 */
class DetectionAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    sealed class Item {
        class Group(val algorithm: String, val count: Int, val expanded: Boolean) : Item()
        class Detection(val row: DetectionRow) : Item()
    }

    private var groups: List<Pair<String, List<DetectionRow>>> = emptyList()
    private val expanded = mutableSetOf<String>()
    private var items: List<Item> = emptyList()

    /** Replaces the dataset; rows are grouped alphabetically by algorithm. */
    fun submit(rows: List<DetectionRow>) {
        groups = rows.groupBy { it.algorithm }
            .toSortedMap()
            .map { (algorithm, detections) -> algorithm to detections.sortedBy { it.tick } }
        rebuild()
    }

    private fun rebuild() {
        items = buildList {
            for ((algorithm, detections) in groups) {
                add(Item.Group(algorithm, detections.size, algorithm in expanded))
                if (algorithm in expanded) detections.forEach { add(Item.Detection(it)) }
            }
        }
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is Item.Group -> TYPE_GROUP
        is Item.Detection -> TYPE_DETECTION
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_GROUP) {
            GroupHolder(inflater.inflate(R.layout.item_group, parent, false))
        } else {
            DetectionHolder(inflater.inflate(R.layout.item_detection, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is Item.Group -> (holder as GroupHolder).bind(item)
            is Item.Detection -> (holder as DetectionHolder).bind(item.row)
        }
    }

    inner class GroupHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val chevron = view.findViewById<TextView>(R.id.groupChevron)
        private val name = view.findViewById<TextView>(R.id.groupName)
        private val count = view.findViewById<TextView>(R.id.groupCount)

        fun bind(group: Item.Group) {
            chevron.text = if (group.expanded) "▾" else "▸"
            name.text = group.algorithm
            count.text = group.count.toString()
            itemView.setOnClickListener {
                if (group.expanded) expanded.remove(group.algorithm) else expanded.add(group.algorithm)
                rebuild()
            }
        }
    }

    class DetectionHolder(view: View) : RecyclerView.ViewHolder(view) {
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
                Toast.makeText(itemView.context, R.string.copied, Toast.LENGTH_SHORT).show()
                true
            }
        }
    }

    companion object {
        private const val TYPE_GROUP = 0
        private const val TYPE_DETECTION = 1
    }
}
