package com.kaminari.gram.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ListView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Extraordikami: the hub for KamiGram's additional features.
 *
 * Kept deliberately small and fork-local (nothing here touches upstream files).
 * Each row is a toggle backed by {@link com.kaminari.gram.KamiConfig}, and each
 * toggle's enforcement lives at exactly one place in upstream code:
 *
 * - Show Deleted Messages: MessagesController.kamiKeepDeletedMessages
 * - Show User ID in Profile: ProfileActivity's header subtitle
 */
public class ExtraordiKamiActivity extends BaseFragment {

    private static final int ROW_DELETED_MESSAGES = 0;
    private static final int ROW_USER_ID = 1;
    private static final int ROW_COUNT = 2;

    private ListView listView;
    private ListAdapter adapter;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setTitle("Extraordikami");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new ListView(context);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setVerticalScrollBarEnabled(false);
        listView.setSelector(new android.graphics.drawable.ColorDrawable(0));
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        adapter = new ListAdapter(context);
        listView.setAdapter(adapter);

        return fragmentView;
    }

    private class ListAdapter extends BaseAdapter {

        private final Context context;

        ListAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getCount() {
            return ROW_COUNT + 2; // two toggles + header + footer
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean isEnabled(int position) {
            return position == ROW_DELETED_MESSAGES || position == ROW_USER_ID;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            int type = getItemViewType(position);
            if (type == 0) {
                TextInfoPrivacyCell cell;
                if (convertView == null) {
                    cell = new TextInfoPrivacyCell(context, 20);
                } else {
                    cell = (TextInfoPrivacyCell) convertView;
                }
                if (position == 0) {
                    cell.setText("Additional KamiGram features");
                } else {
                    cell.setText("Deleted messages are kept on this device only. They are marked with a DELETED label and survive restarts.");
                }
                return cell;
            }
            int row = position - 1;
            TextCheckCell cell;
            if (convertView == null) {
                cell = new TextCheckCell(context);
                cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                final int finalRow = row;
                cell.getCheckBox().setOnCheckedChangeListener((view, checked) -> {
                    if (finalRow == ROW_DELETED_MESSAGES) {
                        com.kaminari.gram.KamiConfig.setShowDeletedMessages(checked);
                    } else if (finalRow == ROW_USER_ID) {
                        com.kaminari.gram.KamiConfig.setShowUserIdInProfile(checked);
                    }
                });
            } else {
                cell = (TextCheckCell) convertView;
            }
            if (row == ROW_DELETED_MESSAGES) {
                cell.setTextAndValueAndCheck(
                        "Show Deleted Messages",
                        "Messages deleted for everyone stay in the chat, labeled as deleted",
                        com.kaminari.gram.KamiConfig.showDeletedMessages,
                        true,
                        true);
            } else if (row == ROW_USER_ID) {
                cell.setTextAndValueAndCheck(
                        "Show User ID in Profile",
                        "Display @username and the numeric ID in profile headers",
                        com.kaminari.gram.KamiConfig.showUserIdInProfile,
                        true,
                        false);
            }
            return cell;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            return position == 0 || position == getCount() - 1 ? 0 : 1;
        }
    }
}
