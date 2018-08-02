package org.altekamereren.groggomat

import android.app.ListFragment
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import org.jetbrains.anko.*
import java.util.*

public class KamererListFragment : ListFragment() {

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        listView.setOnItemLongClickListener { _, _, _, id ->
            val newFragment = KamererFragment().withArguments("kamerer" to id);
            fragmentManager.beginTransaction().replace(android.R.id.content, newFragment).addToBackStack(null).commit()
            true
        }
    }

    private var kamerererByName: List<Kamerer> = ArrayList<Kamerer>()

    fun updateData() {
        kamerererByName = (ctx as MainActivity).kamererer.values.toList().sortedBy({it.name})
        (listAdapter as ArrayAdapter<*>).notifyDataSetChanged()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        kamerererByName = (ctx as MainActivity).kamererer.values.toList().sortedBy({it.name})

        listAdapter = object: ArrayAdapter<Kamerer>(context, -1, kamerererByName){
            public inline fun <T: Any> view(crossinline f: AnkoContext<*>.() -> T): T {
                var view: T? = null
                context.UI { view = f() }
                return view!!
            }

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View? {
                val kamerer = kamerererByName[position];

                val view = view {
                    linearLayout {
                        textView {
                            text = kamerer.name
                            textSize = 16f
                            typeface = Typeface.create("", Typeface.BOLD)
                        }.lparams(width = 0) {
                            width=0
                            margin=dip(10)
                            weight=1f
                            gravity= Gravity.CENTER_VERTICAL
                        }

                        if(kamerer.weight != null) {
                            textView {
                                text = java.lang.String.format("%.2f", kamerer.alcohol)
                                textSize = 16f
                                padding = dip(10)
                            }.lparams(width = wrapContent, height = matchParent)
                        }

                        for(i in kamerer.kryss.indices) {
                            textView {
                                text = kamerer.kryss[i].toString()
                                textSize = 16f
                                typeface = Typeface.create("", Typeface.BOLD)
                                backgroundColor = KryssType.types[i].color
                                padding = dip(10)
                            }.lparams(width = wrapContent, height = matchParent) {}
                        }
                    }
                }

                return view
            }

            override fun getItemId(position: Int): Long {
                return kamerererByName[position].id
            }

            override fun hasStableIds(): Boolean {
                return true
            }

            override fun isEnabled(position: Int): Boolean {
                return true;
            }
        }
    }

    override fun onListItemClick(l: ListView?, v: View?, position: Int, id: Long) {
        val ft = fragmentManager.beginTransaction()
        val prev = fragmentManager.findFragmentByTag("dialog")
        if (prev != null) {
            ft.remove(prev)
        }
        ft.addToBackStack(null)

        // Create and show the dialog.
        val newFragment = KryssDialogFragment().withArguments("kamerer" to id)
        newFragment.show(ft, "dialog")
    }
}

