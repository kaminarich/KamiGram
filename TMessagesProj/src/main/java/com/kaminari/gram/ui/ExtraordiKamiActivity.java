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
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Extraordikami: the hub for KamiGram's additional features.
 *
 * <h3>How the toggles are driven</h3>
 * Rows are toggled from the list's item-click listener, not from the Switch. This
 * matters and was originally wrong here:
 * <ul>
 *   <li>{@code Switch} does not consume touch events, so tapping the switch itself
 *       does nothing. Upstream screens (LiteModeSettingsActivity, and every
 *       Settings page) all drive TextCheckCell from the row click.</li>
 *   <li>{@code Switch.setChecked} invokes its listener, and binding a row calls
 *       {@code setChecked}. Attaching a listener and then binding therefore fires
 *       the listener during bind, which on a recycled view writes the previous
 *       row's value into config.</li>
 * </ul>
 * So: no checked-change listener at all, and every write goes through
 * {@link #onRowClick}.
 *
 * <h3>Grouping</h3>
 * Privacy and Interface are deterministic and client-side. Experimental toggles
 * change protocol-visible behaviour or lean on non-contractual server behaviour;
 * they are off by default and each explains its specific trade-off before being
 * enabled.
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
    private int losslessRow;
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
        losslessRow = rowCount++;
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
        listView.setOnItemClickListener((parent, view, position, id) -> onRowClick(position, view));

        return fragmentView;
    }

    private void onRowClick(int position, View view) {
        final boolean isCheck = view instanceof TextCheckCell;
        if (!isCheck) {
            return;
        }
        final TextCheckCell cell = (TextCheckCell) view;
        final boolean enabling = !cell.isChecked();

        if (position == hideOnlineRow) {
            KamiConfig.setHideOnlineStatus(enabling);
            cell.setChecked(enabling);
        } else if (position == hideTypingRow) {
            KamiConfig.setHideTypingStatus(enabling);
            cell.setChecked(enabling);
        } else if (position == hideMediaRow) {
            KamiConfig.setHideMediaStatus(enabling);
            cell.setChecked(enabling);
        } else if (position == deletedMessagesRow) {
            KamiConfig.setShowDeletedMessages(enabling);
            cell.setChecked(enabling);
        } else if (position == userIdRow) {
            KamiConfig.setShowUserIdInProfile(enabling);
            cell.setChecked(enabling);
        } else if (position == boostRow) {
            if (enabling) {
                confirmExperimental(cell,
                        "Boost Download & Upload",
                        "KamiGram will request larger chunks and run more transfers in parallel.\n\n"
                                + "Telegram enforces its speed limit on the server, so this cannot lift a cap on your account. "
                                + "What it does recover is throughput lost to round trips, which is most noticeable on fast connections.\n\n"
                                + "On a weak or metered connection it can be slower, because failed parts get retried. "
                                + "It also uses more memory and battery while transferring.",
                        () -> KamiConfig.setBoostNetwork(true));
            } else {
                KamiConfig.setBoostNetwork(false);
                cell.setChecked(false);
            }
        } else if (position == hqMediaRow) {
            if (enabling) {
                confirmExperimental(cell,
                        "Send Media in High Quality",
                        "Photos are encoded at up to 2560px and quality 99 instead of 1280px and quality 80. "
                                + "Videos use the highest quality bucket the source supports.\n\n"
                                + "Telegram still re-encodes both, so this raises the ceiling but is not lossless. "
                                + "For bit-exact originals use Send Media Losslessly below.\n\n"
                                + "Uploads will be noticeably larger and slower.",
                        () -> KamiConfig.setForceHighQualityMedia(true));
            } else {
                KamiConfig.setForceHighQualityMedia(false);
                cell.setChecked(false);
            }
        } else if (position == losslessRow) {
            if (enabling) {
                confirmExperimental(cell,
                        "Send Media Losslessly",
                        "Photos and videos are sent as files, which uploads the original bytes with no re-encoding at all. "
                                + "This is the only way to avoid Telegram's compression completely.\n\n"
                                + "The trade-off is how they arrive: recipients see a file to download rather than an inline preview, "
                                + "and the media will not appear in the chat's shared-media gallery.\n\n"
                                + "Uploads are much larger. This overrides High Quality, which still re-encodes.",
                        () -> KamiConfig.setLosslessMedia(true));
            } else {
                KamiConfig.setLosslessMedia(false);
                cell.setChecked(false);
            }
        } else if (position == loginRow) {
            if (enabling) {
                confirmExperimental(cell,
                        "Avoid Firebase Verification",
                        "When signing in, KamiGram will ask Telegram not to use in-app Firebase verification and to send an SMS or place a call instead.\n\n"
                                + "This helps because third-party clients have no verification key of their own, so when Telegram picks that method the code never arrives and sign-in dead-ends.\n\n"
                                + "Telegram still decides which method to use, and this does not remove any other requirement it places on your number.",
                        () -> KamiConfig.setBypassFirebaseLogin(true));
            } else {
                KamiConfig.setBypassFirebaseLogin(false);
                cell.setChecked(false);
            }
        }
    }

    /**
     * Experimental toggles explain the trade-off before switching on. Switching one
     * off is immediate: there is nothing to warn about.
     */
    private void confirmExperimental(TextCheckCell cell, String title, String message, Runnable onAccept) {
        if (getParentActivity() == null) {
            onAccept.run();
            cell.setChecked(true);
            return;
        }
        new AlertDialog.Builder(getParentActivity())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Enable", (dialog, which) -> {
                    onAccept.run();
                    cell.setChecked(true);
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
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
                        "Maximum resolution and quality, still re-encoded",
                        KamiConfig.forceHighQualityMedia(), true, true);
            } else if (position == losslessRow) {
                cell.setTextAndValueAndCheck("Send Media Losslessly",
                        "Send photos and videos as files, with no compression at all",
                        KamiConfig.losslessMedia(), true, true);
            } else if (position == loginRow) {
                cell.setTextAndValueAndCheck("Avoid Firebase Verification",
                        "Ask Telegram for an SMS or call instead of app verification",
                        KamiConfig.bypassFirebaseLogin(), true, false);
            }
            return cell;
        }
    }
}
