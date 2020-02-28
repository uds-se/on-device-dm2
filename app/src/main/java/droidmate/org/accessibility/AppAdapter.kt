package droidmate.org.accessibility

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView

class AppAdapter(context: Context, private val appList: List<App>) : BaseAdapter() {
    private val layoutInflater = LayoutInflater.from(context)

    override fun getCount(): Int {
        return appList.size
    }

    override fun getItem(id: Int): App {
        return if (count < id) {
            appList[id]
        } else {
            appList.last()
        }
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(
        position: Int,
        convertView: View?,
        parent: ViewGroup
    ): View? {
        val view: View?
        val viewHolder: ViewHolder?

        if (convertView == null) {
            view = layoutInflater.inflate(R.layout.installed_app_list, parent, false)
            viewHolder = ViewHolder(view)
            view.tag = viewHolder
        } else {
            view = convertView
            viewHolder = view.tag as ViewHolder
        }

        viewHolder.appName.text = appList[position].name
        viewHolder.appPackage.text = appList[position].packageName
        viewHolder.appIcon.setImageDrawable(appList[position].icon)
        return view
    }

    internal inner class ViewHolder(row: View) {
        var appName: TextView = row.findViewById<View>(R.id.app_name) as TextView
        var appPackage: TextView = row.findViewById<View>(R.id.app_package) as TextView
        var appIcon: ImageView = row.findViewById<View>(R.id.app_icon) as ImageView
    }
}