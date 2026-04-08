package com.buddy.app.ui

import android.graphics.Color
import android.view.*
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.buddy.app.R
import java.text.SimpleDateFormat
import java.util.*

class MessageAdapter : RecyclerView.Adapter<MessageAdapter.VH>() {
    private val items = mutableListOf<BuddyMessage>()

    companion object { private const val USER = 0; private const val BUDDY = 1 }

    inner class VH(view: View, private val isUser: Boolean) : RecyclerView.ViewHolder(view) {
        val tvText: TextView = view.findViewById(R.id.tvMessage)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
        val tvBadge: TextView? = if (!isUser) view.findViewById(R.id.tvBadge) else null

        fun bind(m: BuddyMessage) {
            tvText.text = m.text
            tvTime.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(m.timestamp))

            // Show category badge for proactive messages
            tvBadge?.let { badge ->
                if (m.category.isNotBlank() && !m.isUser) {
                    badge.visibility = View.VISIBLE
                    badge.text = m.category.uppercase()
                    val (bg, tx) = when (m.category) {
                        "news"         -> "#FF6B35" to "#FFF"
                        "tech"         -> "#00D4FF" to "#000"
                        "science"      -> "#7C3AED" to "#FFF"
                        "health"       -> "#00FF88" to "#000"
                        "productivity" -> "#FFD700" to "#000"
                        "wisdom"       -> "#FF69B4" to "#000"
                        "warning"      -> "#FF4444" to "#FFF"
                        "finance"      -> "#32CD32" to "#000"
                        else           -> "#1A3A5C" to "#FFF"
                    }
                    badge.setBackgroundColor(Color.parseColor(bg))
                    badge.setTextColor(Color.parseColor(tx))
                } else {
                    badge.visibility = View.GONE
                }
            }
        }
    }

    override fun getItemViewType(pos: Int) = if (items[pos].isUser) USER else BUDDY

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layout = if (viewType == USER) R.layout.item_user else R.layout.item_buddy
        return VH(LayoutInflater.from(parent.context).inflate(layout, parent, false), viewType == USER)
    }

    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(items[pos])
    override fun getItemCount() = items.size

    fun add(m: BuddyMessage) { items.add(m); notifyItemInserted(items.size - 1) }
    fun clear() { items.clear(); notifyDataSetChanged() }
    fun getAll() = items.toList()
}
