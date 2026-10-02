package com.tf2demo.analyzer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
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
        private val paramsButton = view.findViewById<ImageView>(R.id.paramsButton)

        fun bind(info: SettingsStore.AlgorithmInfo) {
            sw.text = info.name
            sw.isChecked = state.enabled[info.name] ?: info.defaultEnabled
            sw.setOnCheckedChangeListener { _, checked ->
                state.enabled[info.name] = checked
                onChanged()
            }

            // Always show the effective values (defaults included) so it's
            // obvious the row is configurable — desktop lists them inline too.
            val text = info.params.joinToString(", ") { param ->
                val value = state.params[info.name]?.get(param.name) ?: param.default
                "${param.name}=$value"
            }
            summary.visibility = if (info.params.isEmpty()) View.GONE else View.VISIBLE
            summary.text = text

            // The gear opens the parameter dialog; rows without parameters
            // hide it.
            paramsButton.visibility = if (info.params.isEmpty()) View.INVISIBLE else View.VISIBLE
            paramsButton.setOnClickListener { showEditDialog(info) }
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
                    com.google.android.material.materialswitch.MaterialSwitch(context)
                        .apply { isChecked = current as Boolean }
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

            val dialog = MaterialAlertDialogBuilder(context)
                .setTitle(info.name)
                .setView(container)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val target = state.params.getOrPut(info.name) { mutableMapOf() }
                    for ((param, editor) in editors) {
                        when (param.kind) {
                            SettingsStore.Kind.BOOL ->
                                target[param.name] =
                                    (editor as com.google.android.material.materialswitch.MaterialSwitch).isChecked

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
                .create()
            dialog.show()
            // Material puts the affirmative button on the right by default;
            // the concept wants OK on the left of Cancel.
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.let { ok ->
                val cancel = dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)
                (ok.parent as? android.widget.LinearLayout)?.let { buttons ->
                    buttons.removeView(ok)
                    buttons.addView(ok, 0)
                }
                cancel?.requestLayout()
            }
        }
    }
}
