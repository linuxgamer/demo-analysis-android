package dev.stast.demodetector

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * Algorithm list: a switch per algorithm (enable/disable, like the desktop
 * checkboxes) and a tap-to-edit parameters dialog for algorithms that have
 * parameters.
 */
class AlgorithmAdapter(
    private val schema: List<SettingsStore.AlgorithmInfo>,
    private val state: SettingsStore.State,
    private val onChanged: () -> Unit,
) : RecyclerView.Adapter<AlgorithmAdapter.Holder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_algorithm, parent, false)
        return Holder(view)
    }

    override fun getItemCount() = schema.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(schema[position])
    }

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val sw = view.findViewById<MaterialSwitch>(R.id.algorithmSwitch)
        private val summary = view.findViewById<TextView>(R.id.paramsSummary)

        fun bind(info: SettingsStore.AlgorithmInfo) {
            sw.text = info.name
            sw.isChecked = state.enabled[info.name] ?: info.defaultEnabled
            sw.setOnCheckedChangeListener { _, checked ->
                state.enabled[info.name] = checked
                onChanged()
            }

            val values = state.params[info.name]
            summary.visibility = if (values.isNullOrEmpty()) View.GONE else View.VISIBLE
            summary.text = values?.entries?.joinToString(", ") { "${it.key}=${it.value}" }

            // No parameters to edit — the whole row just toggles.
            if (info.params.isEmpty()) return

            itemView.setOnClickListener { showEditDialog(info) }
        }

        private fun showEditDialog(info: SettingsStore.AlgorithmInfo) {
            val context = itemView.context
            val container = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(48, 24, 48, 0)
            }
            val editors = mutableListOf<Pair<SettingsStore.ParamInfo, View>>()
            for (param in info.params) {
                val kindLabel = when (param.kind) {
                    SettingsStore.Kind.FLOAT -> "float"
                    SettingsStore.Kind.INT -> "int"
                    SettingsStore.Kind.BOOL -> "bool"
                }
                val label = TextView(context).apply { text = "${param.name} ($kindLabel)" }
                val current = state.params[info.name]?.get(param.name) ?: param.default
                val editor: View = if (param.kind == SettingsStore.Kind.BOOL) {
                    Switch(context).apply { isChecked = current as Boolean }
                } else {
                    EditText(context).apply {
                        setText(current.toString())
                        inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                            android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or
                            android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
                    }
                }
                container.addView(label)
                container.addView(editor)
                editors.add(param to editor)
            }

            MaterialAlertDialogBuilder(context)
                .setTitle(info.name)
                .setView(container)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val target = state.params.getOrPut(info.name) { mutableMapOf() }
                    for ((param, editor) in editors) {
                        when (param.kind) {
                            SettingsStore.Kind.BOOL ->
                                target[param.name] = (editor as Switch).isChecked

                            SettingsStore.Kind.INT -> (editor as EditText).text.toString()
                                .toFloatOrNull()?.let { target[param.name] = it.toInt() }

                            SettingsStore.Kind.FLOAT -> (editor as EditText).text.toString()
                                .toFloatOrNull()?.let { target[param.name] = it }
                        }
                    }
                    onChanged()
                    notifyItemChanged(bindingAdapterPosition)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }
}
