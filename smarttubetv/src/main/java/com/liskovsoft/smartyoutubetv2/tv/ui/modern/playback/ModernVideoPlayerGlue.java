package com.liskovsoft.smartyoutubetv2.tv.ui.modern.playback;

import android.content.Context;

import androidx.core.content.ContextCompat;
import androidx.leanback.media.PlayerAdapter;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ItemAlignmentFacet;
import androidx.leanback.widget.PlaybackRowPresenter;

import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tooltips.ControlButtonPresenterSelector;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tweaks.PlaybackTransportRowPresenter;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.other.VideoPlayerGlue;

import java.util.ArrayList;
import java.util.List;

/**
 * Player glue of the modern interface: single controls row with modern layouts.<br/>
 * Only previous, play/pause and next stay in the center. Every other enabled action
 * is moved to the side groups so all SmartTube actions remain available.
 */
public class ModernVideoPlayerGlue extends VideoPlayerGlue {
    // No initializer: filled while the base constructor creates the controls row
    private List<Action> mMovedActions;

    public ModernVideoPlayerGlue(Context context, PlayerAdapter playerAdapter, OnActionClickedListener actionListener) {
        super(context, playerAdapter, actionListener);
    }

    @Override
    protected ControlButtonPresenterSelector onCreateControlButtonPresenterSelector() {
        return new ModernControlButtonPresenterSelector(false);
    }

    @Override
    protected PlaybackRowPresenter onCreateRowPresenter() {
        PlaybackRowPresenter presenter = super.onCreateRowPresenter();

        if (presenter instanceof PlaybackTransportRowPresenter) {
            PlaybackTransportRowPresenter rowPresenter = (PlaybackTransportRowPresenter) presenter;
            rowPresenter.setLayouts(R.layout.modern_playback_transport_row, R.layout.modern_control_bar);
            // The base fragment aligns the controls row by its bottom edge. The modern overlay starts at the top:
            // the rows are aligned at the suggestions position, so this row's alignment point is that far from its top.
            ItemAlignmentFacet facet = new ItemAlignmentFacet();
            ItemAlignmentFacet.ItemAlignmentDef def = new ItemAlignmentFacet.ItemAlignmentDef();
            def.setItemAlignmentOffset(getContext().getResources().getDimensionPixelSize(R.dimen.modern_player_suggestions_top));
            def.setItemAlignmentOffsetPercent(0);
            facet.setAlignmentDefs(new ItemAlignmentFacet.ItemAlignmentDef[] {def});
            rowPresenter.setFacet(ItemAlignmentFacet.class, facet);
            rowPresenter.setProgressColor(ContextCompat.getColor(getContext(), R.color.modern_text_primary));
            rowPresenter.setSecondaryProgressColor(ContextCompat.getColor(getContext(), R.color.modern_white_30));
            // Every enabled player button is in the single row (the center keeps only the transport buttons)
            rowPresenter.setMaxSecondaryControls(Integer.MAX_VALUE);
        }

        return presenter;
    }

    /**
     * Click an action of the controls row by id (used by the shorts panel).
     * @return false if the action isn't in the row (disabled in the player buttons settings)
     */
    public boolean performAction(int actionId) {
        Action action = findAction(actionId);

        if (action == null) {
            return false;
        }

        onActionClicked(action);
        return true;
    }

    public boolean isActionActive(int actionId) {
        Action action = findAction(actionId);
        return action != null && ModernControlButtonPresenterSelector.isActive(action);
    }

    private Action findAction(int actionId) {
        if (getControlsRow() == null) {
            return null;
        }

        for (Object adapter : new Object[] {getControlsRow().getPrimaryActionsAdapter(), getControlsRow().getSecondaryActionsAdapter()}) {
            if (adapter instanceof ArrayObjectAdapter) {
                ArrayObjectAdapter actions = (ArrayObjectAdapter) adapter;
                for (int i = 0; i < actions.size(); i++) {
                    Action action = (Action) actions.get(i);
                    if (action.getId() == actionId) {
                        return action;
                    }
                }
            }
        }

        return null;
    }

    @Override
    protected void onCreatePrimaryActions(ArrayObjectAdapter adapter) {
        super.onCreatePrimaryActions(adapter);

        if (mMovedActions == null) {
            mMovedActions = new ArrayList<>();
        }

        for (int i = adapter.size() - 1; i >= 0; i--) {
            Action action = (Action) adapter.get(i);
            if (!ModernControlButtonPresenterSelector.isTransport(action)) {
                adapter.removeItems(i, 1);
                mMovedActions.add(0, action);
            }
        }
    }

    @Override
    protected void onCreateSecondaryActions(ArrayObjectAdapter adapter) {
        super.onCreateSecondaryActions(adapter);

        if (mMovedActions != null) {
            adapter.addAll(adapter.size(), mMovedActions);
            mMovedActions.clear();
        }
    }
}
