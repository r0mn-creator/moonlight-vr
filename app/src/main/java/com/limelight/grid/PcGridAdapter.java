package com.limelight.grid;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.limelight.PcView;
import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

public class PcGridAdapter extends GenericGridAdapter<PcView.ComputerObject> {
    // GridView's own "auto_fit" always computes columns from the available
    // width, then left-aligns whatever doesn't fill a full row - it has no
    // concept of centering a partial row. Driving numColumns from the real
    // item count instead (capped here) is what actually keeps a short PC
    // list looking centered instead of stuck in the top-left corner.
    private static final int MAX_COLUMNS = 4;
    // Holding a card this long toggles it as a favorite - deliberately much
    // longer than the system's own ~500ms long-press timeout, since this
    // needs to feel like a distinct, deliberate gesture from an ordinary
    // long-press-for-context-menu.
    private static final long FAVORITE_HOLD_MS = 2000;
    private static final String FAVORITES_PREF_KEY = "pc_favorites";
    // Finger movement past this cancels a pending favorite-hold, same idea
    // as any scroll/drag-cancels-a-click touch slop.
    private static final float TOUCH_SLOP_PX = 24f;

    private GridView gridView;

    public PcGridAdapter(Context context, PreferenceConfiguration prefs) {
        super(context, getLayoutIdForPreferences(prefs));
    }

    private static int getLayoutIdForPreferences(PreferenceConfiguration prefs) {
        return R.layout.pc_grid_item;
    }

    public void updateLayoutWithPreferences(Context context, PreferenceConfiguration prefs) {
        // This will trigger the view to reload with the new layout
        setLayoutId(getLayoutIdForPreferences(prefs));
    }

    // Called once, right after PcView hands the inflated GridView back via
    // receiveAbsListView() - kept as a plain setter rather than a
    // constructor param since the view doesn't exist yet when this adapter
    // itself is constructed.
    public void setGridView(GridView gridView) {
        this.gridView = gridView;
        updateGridPlacement();
        // The very first call above almost always runs before the view has
        // ever been laid out (getWidth()/getHeight() == 0), so the padding
        // half of updateGridPlacement() has nothing to compute against yet -
        // this guarantees at least one more pass once real measurements
        // exist, for the edge case of a list that never changes size again
        // after that (e.g. exactly one already-known PC).
        gridView.post(this::updateGridPlacement);
    }

    // Keeps numColumns matched to the real PC count, then centers whatever
    // that produces - horizontally always, vertically too as long as
    // everything still fits in one screen's worth of rows. Once there are
    // enough rows to overflow, vertical centering intentionally stops (see
    // below) so the grid behaves like a normal scrollable list with the
    // next row peeking in at the bottom, rather than fighting the scroll.
    private void updateGridPlacement() {
        if (gridView == null) {
            return;
        }
        int columns = Math.max(1, Math.min(itemList.size(), MAX_COLUMNS));
        if (gridView.getNumColumns() != columns) {
            gridView.setNumColumns(columns);
        }

        int gridWidth = gridView.getWidth();
        int gridHeight = gridView.getHeight();
        if (gridWidth <= 0 || gridHeight <= 0) {
            // Not laid out yet - the post() in setGridView(), or the next
            // natural notifyDataSetChanged() as more PCs are discovered,
            // will call back in once it is.
            return;
        }

        // GridView's own wrap_content sizing does NOT shrink to
        // numColumns*columnWidth despite what the name implies - confirmed
        // on-device (numColumns correctly read back as the value just set
        // above, but getWidth() still reported the full available width).
        // Centering the populated columns/rows is done by hand instead:
        // pad by whatever space is left over once the real content size is
        // subtracted from the real measured size.
        int columnWidthPx = gridView.getResources()
                .getDimensionPixelSize(R.dimen.pc_card_column_width);
        int rowHeightPx = gridView.getResources()
                .getDimensionPixelSize(R.dimen.pc_card_row_height);
        int contentWidth = columns * columnWidthPx;
        int rows = (int) Math.ceil(itemList.size() / (double) columns);
        int contentHeight = rows * rowHeightPx;

        int sidePad = Math.max(0, (gridWidth - contentWidth) / 2);
        // Only pad the top when everything still fits - padding it once
        // content overflows the view would just push the first row
        // partially off the top edge instead of leaving it scrollable from
        // a natural resting position.
        int topPad = contentHeight < gridHeight ? (gridHeight - contentHeight) / 2 : 0;

        if (gridView.getPaddingLeft() != sidePad || gridView.getPaddingTop() != topPad
                || gridView.getPaddingRight() != sidePad) {
            gridView.setPadding(sidePad, topPad, sidePad, gridView.getPaddingBottom());
        }
    }

    @Override
    public void notifyDataSetChanged() {
        // Every path that changes what's visible (add, remove, initial
        // load) already ends in a call to this, so hooking placement
        // maintenance in here covers all of them without needing a call at
        // each individual site.
        updateGridPlacement();
        super.notifyDataSetChanged();
    }

    public void addComputer(PcView.ComputerObject computer) {
        itemList.add(computer);
        sortList();
    }

    // Favorites sort first, then everything else - both groups alphabetical
    // within themselves. This groups favorites together at the start of the
    // grid (its top-left, currently) rather than leaving them wherever
    // alphabetical order happens to land them - exact corner/edge placement
    // (top-right specifically) is a follow-up once the grid's real fill
    // direction is confirmed against how this actually reads on a full
    // multi-row grid.
    private void sortList() {
        final Set<String> favorites = getFavoriteUuids();
        Collections.sort(itemList, new Comparator<PcView.ComputerObject>() {
            @Override
            public int compare(PcView.ComputerObject lhs, PcView.ComputerObject rhs) {
                boolean lhsFav = favorites.contains(lhs.details.uuid);
                boolean rhsFav = favorites.contains(rhs.details.uuid);
                if (lhsFav != rhsFav) {
                    return lhsFav ? -1 : 1;
                }
                return lhs.details.name.toLowerCase().compareTo(rhs.details.name.toLowerCase());
            }
        });
    }

    public boolean removeComputer(PcView.ComputerObject computer) {
        return itemList.remove(computer);
    }

    private Set<String> getFavoriteUuids() {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getStringSet(FAVORITES_PREF_KEY, Collections.<String>emptySet());
    }

    private boolean isFavorite(String uuid) {
        return getFavoriteUuids().contains(uuid);
    }

    private void toggleFavorite(String uuid) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        // getStringSet()'s returned set must be treated as read-only and
        // never stored back as-is (documented SharedPreferences behavior) -
        // copy before mutating.
        Set<String> favorites = new HashSet<>(getFavoriteUuids());
        if (!favorites.remove(uuid)) {
            favorites.add(uuid);
        }
        prefs.edit().putStringSet(FAVORITES_PREF_KEY, favorites).apply();
        sortList();
        notifyDataSetChanged();
    }

    @Override
    public void populateView(View parentView, ImageView imgView, ProgressBar prgView, TextView txtView, ImageView overlayView, final PcView.ComputerObject obj) {
        imgView.setImageResource(R.drawable.ic_computer);
        if (obj.details.state == ComputerDetails.State.ONLINE) {
            imgView.setAlpha(1.0f);
        }
        else {
            imgView.setAlpha(0.4f);
        }

        if (obj.details.state == ComputerDetails.State.UNKNOWN) {
            prgView.setVisibility(View.VISIBLE);
        }
        else {
            prgView.setVisibility(View.INVISIBLE);
        }

        txtView.setText(obj.details.name);
        if (obj.details.state == ComputerDetails.State.ONLINE) {
            txtView.setAlpha(1.0f);
        }
        else {
            txtView.setAlpha(0.4f);
        }

        if (obj.details.state == ComputerDetails.State.OFFLINE) {
            overlayView.setImageResource(R.drawable.ic_pc_offline);
            overlayView.setAlpha(0.4f);
            overlayView.setVisibility(View.VISIBLE);
        }
        // We must check if the status is exactly online and unpaired
        // to avoid colliding with the loading spinner when status is unknown
        else if (obj.details.state == ComputerDetails.State.ONLINE &&
                obj.details.pairState == PairingManager.PairState.NOT_PAIRED) {
            overlayView.setImageResource(R.drawable.ic_lock);
            overlayView.setAlpha(1.0f);
            overlayView.setVisibility(View.VISIBLE);
        }
        else {
            overlayView.setVisibility(View.GONE);
        }

        final ImageView favoriteView = parentView.findViewById(R.id.grid_favorite);
        final String uuid = obj.details.uuid;
        favoriteView.setVisibility(isFavorite(uuid) ? View.VISIBLE : View.GONE);

        final Handler handler = new Handler(Looper.getMainLooper());
        // Array-of-one rather than a plain boolean - needs to be mutated
        // from inside favoriteRunnable below and read from inside the
        // OnTouchListener, two separate anonymous classes with no
        // reference to each other otherwise.
        final boolean[] fired = { false };
        final Runnable favoriteRunnable = new Runnable() {
            @Override
            public void run() {
                fired[0] = true;
                parentView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                toggleFavorite(uuid);
            }
        };
        // A plain OnTouchListener rather than setOnLongClickListener - the
        // system's long-press timeout (~500ms) isn't adjustable through
        // that API, and this needs the full 2s hold. Always returns false
        // so GridView's own click handling still runs for an ordinary tap;
        // the only time this consumes the event is the ACTION_UP right
        // after the hold actually fired, so that release doesn't also
        // register as a tap on whatever the favorite toggle just reordered
        // into this same screen position.
        parentView.setOnTouchListener(new View.OnTouchListener() {
            private float downX, downY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        fired[0] = false;
                        downX = event.getRawX();
                        downY = event.getRawY();
                        handler.postDelayed(favoriteRunnable, FAVORITE_HOLD_MS);
                        return false;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(event.getRawX() - downX) > TOUCH_SLOP_PX
                                || Math.abs(event.getRawY() - downY) > TOUCH_SLOP_PX) {
                            handler.removeCallbacks(favoriteRunnable);
                        }
                        return false;
                    case MotionEvent.ACTION_UP:
                        handler.removeCallbacks(favoriteRunnable);
                        return fired[0];
                    case MotionEvent.ACTION_CANCEL:
                        handler.removeCallbacks(favoriteRunnable);
                        return false;
                    default:
                        return false;
                }
            }
        });
    }
}
