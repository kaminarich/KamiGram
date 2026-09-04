package com.kaminari.gram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ListView;

import com.kaminari.gram.KamiConfig;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Extraordikami: the hub for KamiGram's additional features.
 *
 * Two groups, because the risk profile is not the same:
 *
 * <ul>
 *   <li><b>Privacy and interface</b> - deterministic, client-side, safe to leave on.</li>
 *   <li><b>Experimental</b> - each one changes protocol-visible behaviour or leans on
 *       server behaviour that is not contractual. Off by default, and each shows a
 *       confirmation explaining the actual trade-off before it can be enabled.
 *       The disclaimers are deliberately specific rather than a generic warning:
 *       a vague "this may cause problems" teaches the user nothing.</li>
 * </ul>
 *
 * Every toggle here has exactly one enforcement point in the codebase; see
 * {@link KamiConfig} for the map.
 */
public class ExtraordiKamiActivity extends BaseFragment {

    private int rowCount;

    private int privacyHeaderRow;
    private int hideOnlineRow;
    private int hideTypingRow;
    private int hideMediaRow;
    private int privacyInfoRow;

    private int interfaceHeaderRow;
    private int deletedMessagesRow;
    private int userIdRow;
    private int interfaceInfoRow;

    private int experimentalHeaderRow;
    private int boostRow;
    private int hqMediaRow;
    private int loginRow;
    private int experimentalInfoRow;

    private ListView listView;
    private ListAdapter adapter;

    private void buildRows() {
        rowCount = 0;
        privacyHeaderRow = rowCount++;
        hideOnlineRow = rowCount++;
        hideTypingRow = rowCount++;
        hideMediaRow = rowCount++;
        privacyInfoRow = rowCount++;

        interfaceHeaderRow = rowCount++;
        deletedMessagesRow = rowCount++;
        userIdRow = rowCount++;
        interfaceInfoRow = rowCount++;

        experimentalHeaderRow = rowCount++;
        boostRow = rowCount++;
        hqMediaRow = rowCount++;
        loginRow = rowCount++;
        experimentalInfoRow = rowCount++;
    }

    @Override
    public View createView(Context context) {
        buildRows();

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

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = frameLayout;

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

    /**
     * Experimental toggles explain the specific trade-off before switching on.
     * Turning one off is immediate: there is nothing to warn about.
     */
    private void confirmExperimental(String title, String message, Runnable onAccept) {
        if (getParentActivity() == null) {
            onAccept.run();
            return;
        }
        new AlertDialog.Builder(getParentActivity())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Enable", (dialog, which) -> {
                    onAccept.run();
                    if (adapter != null) {
                        adapter.notifyDataSetChanged();
                    }
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), (dialog, which) -> {
                    if (adapter != null) {
                        adapter.notifyDataSetChanged();
                    }
                })
                .show();
    }

    private class ListAdapter extends BaseAdapter {

        private static final int TYPE_CHECK = 0;
        private static final int TYPE_HEADER = 1;
        private static final int TYPE_INFO = 2;

        private final Context context;

        ListAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getCount() {
            return rowCount;
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
            return getItemViewType(position) == TYPE_CHECK;
        }

        @Override
        public int getViewTypeCount() {
            return 3;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == privacyHeaderRow || position == interfaceHeaderRow || position == experimentalHeaderRow) {
                return TYPE_HEADER;
            }
            if (position == privacyInfoRow || position == interfaceInfoRow || position == experimentalInfoRow) {
                return TYPE_INFO;
            }
            return TYPE_CHECK;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final int type = getItemViewType(position);

            if (type == TYPE_HEADER) {
                HeaderCell cell = convertView instanceof HeaderCell ? (HeaderCell) convertView : new HeaderCell(context);
                cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                if (position == privacyHeaderRow) {
                    cell.setText("Privacy");
                } else if (position == interfaceHeaderRow) {
                    cell.setText("Interface");
                } else {
                    cell.setText("Experimental");
                }
                return cell;
            }

            if (type == TYPE_INFO) {
                TextInfoPrivacyCell cell = convertView instanceof TextInfoPrivacyCell
                        ? (TextInfoPrivacyCell) convertView : new TextInfoPrivacyCell(context);
                if (position == privacyInfoRow) {
                    cell.setText("Hiding a status stops KamiGram sending it. Nothing is faked, so people you talk to simply never see you as online, typing, or sending media. Telegram may still show your last seen time according to your Privacy settings.");
                } else if (position == interfaceInfoRow) {
                    cell.setText("Deleted messages are kept on this device only. They stay in the chat with a deleted label and survive restarts.");
                } else {
                    cell.setText("Experimental features change how KamiGram talks to Telegram's servers. Each one explains its trade-off before you turn it on.");
                }
                return cell;
            }

            TextCheckCell cell;
            if (convertView instanceof TextCheckCell) {
                cell = (TextCheckCell) convertView;
            } else {
                cell = new TextCheckCell(context);
                cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            }
            final int row = position;
            cell.getCheckBox().setOnCheckedChangeListener((view, checked) -> onToggle(row, checked));

            if (position == hideOnlineRow) {
                cell.setTextAndValueAndCheck("Hide Online Status",
                        "Never report that you are online",
                        KamiConfig.hideOnlineStatus(), true, true);
            } else if (position == hideTypingRow) {
                cell.setTextAndValueAndCheck("Hide Typing Status",
                        "Others will not see \"typing...\"",
                        KamiConfig.hideTypingStatus(), true, true);
            } else if (position == hideMediaRow) {
                cell.setTextAndValueAndCheck("Hide Media Status",
                        "Others will not see \"sending a photo...\" or \"sending a video...\"",
                        KamiConfig.hideMediaStatus(), true, false);
            } else if (position == deletedMessagesRow) {
                cell.setTextAndValueAndCheck("Show Deleted Messages",
                        "Messages deleted for everyone stay in the chat, labeled as deleted",
                        KamiConfig.showDeletedMessages(), true, true);
            } else if (position == userIdRow) {
                cell.setTextAndValueAndCheck("Show User ID in Profile",
                        "Display @username and the numeric ID in profile headers",
                        KamiConfig.showUserIdInProfile(), true, false);
            } else if (position == boostRow) {
                cell.setTextAndValueAndCheck("Boost Download & Upload",
                        "Larger chunks and more parallel transfers",
                        KamiConfig.boostNetwork(), true, true);
            } else if (position == hqMediaRow) {
                cell.setTextAndValueAndCheck("Send Media in High Quality",
                        "Force maximum resolution and quality for photos and videos",
                        KamiConfig.forceHighQualityMedia(), true, true);
            } else if (position == loginRow) {
                cell.setTextAndValueAndCheck("Avoid Firebase Verification",
                        "Ask Telegram for an SMS or call instead of app verification",
                        KamiConfig.bypassFirebaseLogin(), true, false);
            }
            return cell;
        }

        private void onToggle(int row, boolean checked) {
            if (row == hideOnlineRow) {
                KamiConfig.setHideOnlineStatus(checked);
            } else if (row == hideTypingRow) {
                KamiConfig.setHideTypingStatus(checked);
            } else if (row == hideMediaRow) {
                KamiConfig.setHideMediaStatus(checked);
            } else if (row == deletedMessagesRow) {
                KamiConfig.setShowDeletedMessages(checked);
            } else if (row == userIdRow) {
                KamiConfig.setShowUserIdInProfile(checked);
            } else if (row == boostRow) {
                if (checked) {
                    confirmExperimental("Boost Download & Upload",
                            "KamiGram will request larger chunks and run more transfers in parallel.\n\n"
                                    + "Telegram enforces its speed limit on the server, so this cannot lift a cap on your account. "
                                    + "What it does help with is throughput lost to round trips, which is most noticeable on fast connections.\n\n"
                                    + "On a weak or metered connection it can be slower, because failed parts are retried. "
                                    + "It also uses more memory and battery while transferring.",
                            () -> KamiConfig.setBoostNetwork(true));
                } else {
                    KamiConfig.setBoostNetwork(false);
                }
            } else if (row == hqMediaRow) {
                if (checked) {
                    confirmExperimental("Send Media in High Quality",
                            "Photos are encoded at up to 2560px and quality 99 instead of 1280px and quality 80. "
                                    + "Videos use the highest quality bucket the source supports.\n\n"
                                    + "Telegram always re-encodes photos and videos, so this raises the ceiling but is not truly lossless. "
                                    + "For a bit-exact original, send the file as a document instead.\n\n"
                                    + "Uploads will be considerably larger and slower.",
                            () -> KamiConfig.setForceHighQualityMedia(true));
                } else {
                    KamiConfig.setForceHighQualityMedia(false);
                }
            } else if (row == loginRow) {
                if (checked) {
                    confirmExperimental("Avoid Firebase Verification",
                            "When signing in, KamiGram will ask Telegram not to use in-app Firebase verification and to send an SMS or place a call instead.\n\n"
                                    + "This helps because third-party clients have no verification key of their own, so when Telegram chooses that method the code never arrives and sign-in dead-ends.\n\n"
                                    + "Telegram still decides which method to use, and this does not remove any other requirement it may place on your number.",
                            () -> KamiConfig.setBypassFirebaseLogin(true));
                } else {
                    KamiConfig.setBypassFirebaseLogin(false);
                }
            }
        }
    }
}
