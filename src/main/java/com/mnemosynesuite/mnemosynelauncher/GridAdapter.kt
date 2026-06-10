package com.mnemosynesuite.mnemosynelauncher

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class GridAdapter(
    private val context: Context,
    private val items: List<GridItem>,
    private val categoryAppsLabelsEnabled: Boolean,
    private val isListView: Boolean = false,
    private val onItemSelected: (String) -> Unit,
    private val onItemClicked: (String) -> Unit,
    private val onItemLongClicked: (String) -> Unit,
    private val launchApp: Boolean = false) :
    RecyclerView.Adapter<GridAdapter.GridViewHolder>() {

    class GridViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.cellIcon)
        val text: TextView = view.findViewById(R.id.cellText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GridViewHolder {
        val layout = if (isListView) R.layout.item_list_cell else R.layout.item_grid_cell
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)

        //view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            view.defaultFocusHighlightEnabled = false
        }

        // Only apply the rigid height calculation if it's the Grid view
        if (!isListView) {
            val totalHeight = parent.measuredHeight - parent.paddingTop - parent.paddingBottom
            val displayMetrics = parent.context.resources.displayMetrics
            val isLandscape = displayMetrics.widthPixels >= displayMetrics.heightPixels

            // Force the height for the grid structure
            view.layoutParams.height = if (isLandscape) totalHeight / 3 else totalHeight / 4
        } else {
            // Optional: Ensure list items have a reasonable height
            // For a list, we usually want the items to be as tall as their content
            view.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        }

        return GridViewHolder(view)
    }

    fun zoomAnimate(recyclerView: RecyclerView) {
        val displayMetrics = recyclerView.context.resources.displayMetrics
        val screenCenterX = displayMetrics.widthPixels / 2f
        val screenCenterY = displayMetrics.heightPixels / 2f

        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i) ?: continue
            val holder = recyclerView.getChildViewHolder(child) as? GridViewHolder ?: continue
            val position = recyclerView.getChildAdapterPosition(child)

            listOf(holder.icon, holder.text).forEach { view ->
                if (view.visibility == View.GONE) return@forEach

                view.post {
                    val loc = IntArray(2)
                    view.getLocationOnScreen(loc)
                    val endX = loc[0] + view.width / 2f
                    val endY = loc[1] + view.height / 2f

                    val translateAnim = android.view.animation.TranslateAnimation(
                        screenCenterX - endX, 0f,
                        screenCenterY - endY, 0f
                    )
                    val scaleAnim = android.view.animation.ScaleAnimation(
                        0f, 1f, 0f, 1f,
                        android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
                        android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f
                    )
                    val animSet = android.view.animation.AnimationSet(true).apply {
                        addAnimation(translateAnim)
                        addAnimation(scaleAnim)
                        duration = 350
                        interpolator = android.view.animation.OvershootInterpolator(1.2f)
                        startOffset = (position * 10).toLong()
                    }
                    view.startAnimation(animSet)
                }
            }
        }
    }

    fun emergeAnimate(recyclerView: RecyclerView) {
        val displayMetrics = recyclerView.context.resources.displayMetrics

        var originX = displayMetrics.widthPixels / 2f
        var originY = displayMetrics.heightPixels / 2f

        // 2. Look for the 5th child holder (Index 4)
        val targetChild = recyclerView.getChildAt(4)
        if (targetChild != null) {
            val targetHolder = recyclerView.getChildViewHolder(targetChild) as? GridViewHolder
            if (targetHolder != null) {
                val targetLoc = IntArray(2)
                // Use the target icon's center as the precise epicenter of the explosion/emergence
                targetHolder.icon.getLocationOnScreen(targetLoc)
                //originX = targetLoc[0] + targetHolder.icon.width / 2f
                originY = targetLoc[1] + targetHolder.icon.height / 2f
            }
        }

        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i) ?: continue
            val holder = recyclerView.getChildViewHolder(child) as? GridViewHolder ?: continue
            val position = recyclerView.getChildAdapterPosition(child)

            listOf(holder.icon, holder.text).forEach { view ->
                if (view.visibility == View.GONE) return@forEach

                view.post {
                    val loc = IntArray(2)
                    view.getLocationOnScreen(loc)
                    val endX = loc[0] + view.width / 2f
                    val endY = loc[1] + view.height / 2f

                    val translateAnim = android.view.animation.TranslateAnimation(
                        originX - endX, 0f,
                        originY - endY, 0f
                    )
                    val animSet = android.view.animation.AnimationSet(true).apply {
                        addAnimation(translateAnim)
                        duration = 350
                        interpolator = android.view.animation.DecelerateInterpolator(1.5f)
                    }
                    view.startAnimation(animSet)
                }
            }
        }
    }

    override fun onBindViewHolder(holder: GridViewHolder, position: Int) {
        val item = items[position]
        holder.text.text = item.title
        if(item.iconDrawable == null) holder.icon.setImageResource(item.iconResId)
        else holder.icon.setImageDrawable(item.iconDrawable)

        val scale = holder.itemView.context.resources.displayMetrics.density
        var paddingPx=0

        val grid_menu_padding_factor = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
            .getInt("grid_menu_padding_factor", 0)

        if(!isListView) {
            paddingPx = (grid_menu_padding_factor * scale).toInt()
        }


        val context = holder.itemView.context
        if(context.dpadEnabled) {
            holder.itemView.isFocusable = true
            holder.itemView.isFocusableInTouchMode = true
        }

        holder.itemView.setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
        if (categoryAppsLabelsEnabled || isListView) {
            holder.text.visibility = View.VISIBLE // Do nothing / leave visible
        } else {
            holder.text.visibility = View.GONE    // Disable and collapse text views completely
            holder.itemView.setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
        }

//        holder.itemView.setOnClickListener {
//            Toast.makeText(holder.itemView.context, "Clicked: ${item.title}", Toast.LENGTH_SHORT).show()
//        }

// Add this:
        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                (context as? MainActivity)?.moveHighlightToView(view)
                onItemSelected(if (!launchApp) item.title else ((item.packageName as? String) ?: ""))
            } else {
                // Handle selection loss checks
                view.post {
                    // Check if the current window focus path is still pointing anywhere inside the grid array layout
                    val parentRecyclerView = view.parent as? RecyclerView
                    if (parentRecyclerView != null) {
                        // If the overall recyclerview layout structure doesn't contain the currently
                        // focused view node element, it means selection left the menu completely!
                        val currentFocusedChild = parentRecyclerView.focusedChild
                        if (currentFocusedChild == null) {
                            (context as? MainActivity)?.hideMenuHighlight()
                        }
                    }
                }
            }
            val scale = if (hasFocus) 1.6f else 1.0f
            holder.icon.animate()
                .scaleX(scale)
                .scaleY(scale)
                .setDuration(200)
                .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
                .start()

            if (!launchApp) {
                val chosenPack = (context as? MainActivity)?.selectedIconPack ?: "default"
                val isIconPackActive = chosenPack != "default" && chosenPack.isNotEmpty()
                if (isIconPackActive) {
                    if (item.iconDrawableHover != null) {
                        // --- 1. ICON PACK HOVER LOGIC ---
                        holder.icon.animate()
                            .alpha(0.4f)
                            .setDuration(70)
                            .withEndAction {
                                holder.icon.setImageDrawable(if (hasFocus) item.iconDrawableHover else item.iconDrawable)
                                holder.icon.animate().alpha(1f).setDuration(250).start()
                            }
                            .start()
                    }
                } else {
                    // --- 2. LOCAL R.DRAWABLE HOVER LOGIC ---
                    val baseName = holder.itemView.context.resources.getResourceEntryName(item.iconResId)
                    val hoverResId = holder.itemView.context.resources.getIdentifier("${baseName}_hover", "drawable", holder.itemView.context.packageName)

                    if (hoverResId != 0) {
                        holder.icon.animate()
                            .alpha(0.4f)
                            .setDuration(70)
                            .withEndAction {
                                holder.icon.setImageResource(if (hasFocus) hoverResId else item.iconResId)
                                holder.icon.animate().alpha(1f).setDuration(250).start()
                            }
                            .start()
                    }
                }
            }
        }

        holder.itemView.setOnClickListener {
            if(!launchApp) onItemClicked(item.title)
            else onItemClicked( ((item.packageName as? String) ?: "") )
        }

        holder.itemView.setOnLongClickListener {
            if (!launchApp) {
                onItemLongClicked(item.title)
            } else {
                onItemLongClicked( ((item.packageName as? String) ?: "") )
            }
            true // Return true to indicate the long click event was handled
        }

        //zoomAnimate(this.context.recent)

//        val animation = AnimationUtils.loadAnimation(holder.itemView.context, R.anim.icon_pop)
//        animation.startOffset = (position * 20).toLong()
//        holder.icon.startAnimation(animation)
    }

    override fun getItemCount(): Int = items.size
}