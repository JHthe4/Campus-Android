package de.tum.`in`.tumcampusapp.component.tumui.tutionfees

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.Editor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import de.tum.`in`.tumcampusapp.R
import de.tum.`in`.tumcampusapp.component.other.navigation.NavigationDestination
import de.tum.`in`.tumcampusapp.component.other.navigation.SystemActivity
import de.tum.`in`.tumcampusapp.component.tumui.tutionfees.model.Tuition
import de.tum.`in`.tumcampusapp.component.ui.overview.CardAdapter
import de.tum.`in`.tumcampusapp.component.ui.overview.CardManager
import de.tum.`in`.tumcampusapp.component.ui.overview.card.Card
import de.tum.`in`.tumcampusapp.component.ui.overview.card.CardViewHolder
import de.tum.`in`.tumcampusapp.utils.DateTimeUtils
import org.joda.time.format.DateTimeFormat

/**
 * Card that shows information about your fees that have to be paid or have been paid
 */
class TuitionFeesCard internal constructor(context: Context, val tuition: Tuition) :
        Card(CardManager.CARD_TUITION_FEE, context, "card_tuition_fee") {

    val title: String = context.getString(R.string.tuition_fees)

    override val optionsMenuResId = R.menu.card_popup_menu

    override fun getId() = 0

    override fun getNavigationDestination(): NavigationDestination {
        return SystemActivity(TuitionFeesActivity::class.java, null)
    }

    override fun updateViewHolder(viewHolder: RecyclerView.ViewHolder) {
        super.updateViewHolder(viewHolder)

        val reregisterInfoTextView = viewHolder.itemView.findViewById<TextView>(R.id.reregister_info_text_view)
        val outstandingBalanceTextView = viewHolder.itemView.findViewById<TextView>(R.id.outstanding_balance_text_view)

        if (tuition.isPaid) {
            reregisterInfoTextView.text =
                    context.getString(R.string.reregister_success, tuition.semester)
        } else {
            val dateText = DateTimeFormat.mediumDate().print(tuition.deadline)
            reregisterInfoTextView.text = context.getString(R.string.reregister_todo, dateText)

            outstandingBalanceTextView.text =
                    context.getString(R.string.amount_dots_card, tuition.getAmountText(context))
            outstandingBalanceTextView.visibility = View.VISIBLE
        }
    }

    override fun shouldShow(prefs: SharedPreferences): Boolean {
        val prevDeadline = prefs.getString(LAST_FEE_FRIST, "")!!
        val prevAmount = prefs.getString(LAST_FEE_SOLL, tuition.amount.toString())!!

        // If app gets started for the first time and fee is already paid don't annoy user
        // by showing him that he has been re-registered successfully
        val deadline = DateTimeUtils.getDateString(tuition.deadline)
        return !(prevDeadline.isEmpty() && tuition.isPaid)
                && (prevDeadline < deadline || prevAmount > tuition.amount.toString())
    }

    public override fun discard(editor: Editor) {
        editor.putString(LAST_FEE_FRIST, DateTimeUtils.getDateString(tuition.deadline))
        editor.putString(LAST_FEE_SOLL, tuition.amount.toString())
    }

    companion object : CardAdapter.CardViewHolderFactory {
        private const val LAST_FEE_FRIST = "fee_frist"
        private const val LAST_FEE_SOLL = "fee_soll"

        override fun inflateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
            val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.card_tuition_fees, parent, false)
            return CardViewHolder(view)
        }
    }
}
