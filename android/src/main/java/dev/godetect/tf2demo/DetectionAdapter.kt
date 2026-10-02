package dev.godetect.tf2demo

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** One detection: tick, algorithm, the player's SteamID64 and its data payload. */
data class DetectionRow(
    val tick: Int,
    val algorithm: String,
    val steamId: String,
    /** Raw JSON of the detection's `data` object, for the details dialog. */
    val dataJson: String? = null,
)

/**
 * Detection tree per the concept: top level — players (nickname, detection
 * count, SteamID on the right; long-press on the row copies the SteamID),
 * nested — algorithm groups, inside those — gray rows with tick numbers.
 * The chevron glyph is the same triangle in both states; expansion rotates it
 * smoothly by 90° instead of swapping icons.
 */
class DetectionAdapter(
    var playerNames: Map<Long, String> = emptyMap(),
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    /** Player-row action button: open the profile on the remembered site. */
    var onOpenProfile: ((String) -> Unit)? = null

    /** Algorithm-row long-press: show what this detector looks for. */

    sealed class Item {
        /** Expandable row (player or algorithm group). */
        class Group(
            val key: String,
            val label: String,
            val meta: String,
            val expanded: Boolean,
            /** Value copied on long-press, if any (player SteamID). */
            val copyValue: String?,
        ) : Item()

        /** Leaf row inside the gray detail area; long-press copies its tick. */
        class Entry(val text: String, val copyValue: String, val dataJson: String?) : Item()
    }

    private data class PlayerNode(val steamId: Long, val algorithms: Map<String, List<DetectionRow>>)

    private var players: List<PlayerNode> = emptyList()
    private val expandedPlayers = mutableSetOf<Long>()
    private val expandedAlgorithms = mutableSetOf<String>()
    private var items: List<Item> = emptyList()

    /** Details dialog callback: tick row tapped. */
    var onTickDetails: ((String?) -> Unit)? = null

    /** Replaces the dataset: players sorted by detection count, then nickname. */
    fun submit(rows: List<DetectionRow>) {
        players = rows.groupBy { it.steamId }
            .map { (steamId, detections) ->
                PlayerNode(
                    steamId = steamId.toLongOrNull() ?: 0L,
                    algorithms = detections.groupBy { it.algorithm }
                        .mapValues { (_, list) -> list.sortedBy { it.tick } },
                )
            }
            .sortedWith(
                compareByDescending<PlayerNode> { node -> node.algorithms.values.sumOf { it.size } }
                    .thenBy { displayName(it) }
            )
        rebuild()
    }

    private fun displayName(node: PlayerNode): String =
        playerNames[node.steamId] ?: node.steamId.toString()

    private fun rebuild() {
        items = buildList {
            for (node in players) {
                val playerExpanded = node.steamId in expandedPlayers
                val total = node.algorithms.values.sumOf { it.size }
                add(
                    Item.Group(
                        key = "p${node.steamId}",
                        label = displayName(node),
                        meta = "$total · ${node.steamId}",
                        expanded = playerExpanded,
                        copyValue = node.steamId.toString(),
                    )
                )
                if (!playerExpanded) continue
                for ((algorithm, detections) in node.algorithms.toSortedMap()) {
                    val key = "a${node.steamId}/$algorithm"
                    add(
                        Item.Group(
                            key = key,
                            label = algorithm,
                            meta = detections.size.toString(),
                            expanded = key in expandedAlgorithms,
                            copyValue = null,
                        )
                    )
                    if (key in expandedAlgorithms) {
                        detections.forEachIndexed { index, row ->
                            // Copy copies the bare tick number, no ordinal prefix.
                            add(
                                Item.Entry(
                                    "${index + 1}. ${row.tick}",
                                    row.tick.toString(),
                                    row.dataJson,
                                )
                            )
                        }
                    }
                }
            }
        }
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is Item.Group -> TYPE_GROUP
        is Item.Entry -> TYPE_TICK
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_GROUP) {
            GroupHolder(inflater.inflate(R.layout.item_group, parent, false))
        } else {
            TickHolder(inflater.inflate(R.layout.item_tick, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is Item.Group -> (holder as GroupHolder).bind(item)
            is Item.Entry -> (holder as TickHolder).bind(item, onTickDetails)
        }
    }

    inner class GroupHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val chevron = view.findViewById<ImageView>(R.id.groupChevron)
        private val name = view.findViewById<TextView>(R.id.groupName)
        private val meta = view.findViewById<TextView>(R.id.groupMeta)
        private val action = view.findViewById<ImageView>(R.id.groupAction)

        fun bind(group: Item.Group) {
            name.text = group.label
            meta.text = group.meta
            // Algorithm groups sit one level to the right of player rows.
            val density = itemView.resources.displayMetrics.density
            val indentDp = if (group.key.startsWith("a")) 28 else 4
            itemView.setPaddingRelative(
                (indentDp * density).toInt(),
                itemView.paddingTop,
                itemView.paddingEnd,
                itemView.paddingBottom,
            )            // The glyph is always a right-pointing triangle; expansion is the
            // rotation. Recycled rows get the final rotation without animation.
            chevron.rotation = if (group.expanded) 90f else 0f
            if (group.copyValue != null) {
                itemView.setOnLongClickListener {
                    DetectionAdapter.copyToClipboard(itemView.context, group.copyValue)
                    true
                }
                // The open-profile button only makes sense on player rows.
                action.visibility = View.VISIBLE
                action.setOnClickListener { view ->
                    view.isPressed = false
                    onOpenProfile?.invoke(group.copyValue)
                }
            } else {
                itemView.isLongClickable = false
                action.visibility = View.GONE
                // Algorithm rows: long-press explains the detector. The key
                // carries the player prefix; the label is the algorithm name.
                itemView.setOnLongClickListener { view ->
                    AlgorithmInfo.description(group.label)?.let { resId ->
                        MaterialAlertDialogBuilder(view.context)
                            .setTitle(group.label)
                            .setMessage(resId)
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    }
                    AlgorithmInfo.description(group.label) != null
                }
            }
            itemView.setOnClickListener { view ->
                val target = if (group.expanded) 0f else 90f
                chevron.animate().rotation(target).setDuration(150).start()
                // Let the rotation finish before the list re-flattens itself.
                view.postDelayed({ toggle(group.key) }, 150)
            }
        }
    }

    class TickHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val label = view.findViewById<TextView>(R.id.tickEntry)

        fun bind(entry: Item.Entry, onDetails: ((String?) -> Unit)?) {
            label.text = entry.text
            itemView.setOnClickListener { onDetails?.invoke(entry.dataJson) }
            itemView.setOnLongClickListener {
                DetectionAdapter.copyToClipboard(itemView.context, entry.copyValue)
                true
            }
        }
    }

    private fun toggle(key: String) {
        when {
            key.startsWith("p") && key.removePrefix("p").toLongOrNull() != null -> {
                val steamId = key.removePrefix("p").toLong()
                if (steamId in expandedPlayers) expandedPlayers.remove(steamId)
                else expandedPlayers.add(steamId)
            }

            key.startsWith("a") -> {
                if (key in expandedAlgorithms) expandedAlgorithms.remove(key)
                else expandedAlgorithms.add(key)
            }
        }
        rebuild()
    }

    companion object {
        fun copyToClipboard(context: Context, value: String) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("SteamID", value))
            Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
        }

        private const val TYPE_GROUP = 0
        private const val TYPE_TICK = 1
    }
}
