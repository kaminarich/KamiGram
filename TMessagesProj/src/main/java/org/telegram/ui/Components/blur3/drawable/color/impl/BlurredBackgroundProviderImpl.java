package org.telegram.ui.Components.blur3.drawable.color.impl;

import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.graphics.Color;

import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundProvider;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundProviderBuilder;

/**
 * KamiGram: styling for every floating panel — the bottom tab plate, list headers,
 * the chat top panel, bulletins, popup menus.
 *
 * Upstream draws these as glass: the fill is 76-85% alpha over a blurred capture of
 * whatever is behind, edged with a flat hairline. KamiGram is skeuomorphic, so the
 * same panels are opaque plates: solid fill lifted slightly away from the page,
 * a lit bevel along the top edge, a shaded lip along the bottom, and a real drop
 * shadow with vertical offset. The blur pipeline still runs but is no longer
 * visible through the surface.
 *
 * Panels that float over photos or video keep a translucent scrim, because there is
 * no page colour to lift away from and a solid plate would hide the media.
 */
public class BlurredBackgroundProviderImpl {

    /** How far a plate lifts away from the surface behind it. */
    private static final float LIFT_LIGHT = 0.055f;
    private static final float LIFT_DARK = 0.075f;

    // Bevel: lit top edge, shaded bottom lip. Light mode gets a near-opaque white
    // highlight; dark mode a restrained warm white so the edge catches light
    // without glowing.
    private static final int BEVEL_TOP_LIGHT = 0xF2FFFFFF;
    private static final int BEVEL_TOP_DARK = 0x30FFF6E8;
    private static final int BEVEL_BOTTOM_LIGHT = 0x1F6B655C;
    private static final int BEVEL_BOTTOM_DARK = 0x24000000;

    private static final int SHADOW_LIGHT = 0x33000000;
    private static final int SHADOW_DARK = 0x59000000;

    /** Opaque plate colour, lifted off the page so its shadow has separation. */
    private static int plate(int color, boolean isDark) {
        return ColorUtils.setAlphaComponent(
                ColorUtils.blendARGB(color, 0xFFFFFFFF, isDark ? LIFT_DARK : LIFT_LIGHT), 255);
    }

    /** Applies the shared bevel + shadow treatment. */
    private static BlurredBackgroundProviderBuilder bevel(BlurredBackgroundProviderBuilder b,
                                                          float shadowRadius, float shadowDy) {
        return b
                .setStrokeColorTop(BEVEL_TOP_LIGHT, BEVEL_TOP_DARK)
                .setStrokeColorBottom(BEVEL_BOTTOM_LIGHT, BEVEL_BOTTOM_DARK)
                .setShadowColor(SHADOW_LIGHT, SHADOW_DARK)
                .setShadowLayer(shadowRadius, 0, shadowDy)
                .setStrokeWidth(dpf2(1f), dpf2(0.67f));
    }

    public static BlurredBackgroundProvider mainTabs(Theme.ResourcesProvider resourcesProvider) {
        return bevel(new BlurredBackgroundProviderBuilder(resourcesProvider)
                .setBackgroundColor((r, isDark) ->
                        plate(Theme.getColor(Theme.key_glass_targetMainTabs, r), isDark)),
                dpf2(7f), dpf2(2.5f))
                .build();
    }

    public static BlurredBackgroundProvider topPanel(Theme.ResourcesProvider resourcesProvider) {
        return bevel(new BlurredBackgroundProviderBuilder(resourcesProvider)
                .setBackgroundColor((r, isDark) ->
                        plate(Theme.getColor(Theme.key_glass_targetMainTopPanel, r), isDark)),
                dpf2(6f), dpf2(2f))
                .build();
    }

    public static BlurredBackgroundProvider scrimMenuBackground(Theme.ResourcesProvider resourcesProvider) {
        return bevel(new BlurredBackgroundProviderBuilder(resourcesProvider)
                .setBackgroundColor((r, isDark) ->
                        plate(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground, r), isDark)),
                dpf2(8f), dpf2(3f))
                .build();
    }

    public static BlurredBackgroundProvider attachMenuSearch(Theme.ResourcesProvider resourcesProvider) {
        return bevel(new BlurredBackgroundProviderBuilder(resourcesProvider)
                .setBackgroundColor((r, isDark) ->
                        plate(Theme.getColor(Theme.key_chat_emojiSearchBackground, r), isDark)),
                dpf2(4f), dpf2(1.25f))
                .build();
    }

    public static BlurredBackgroundProvider searchFloatingDate(Theme.ResourcesProvider resourcesProvider) {
        // floats over the message list, which may be a photo wallpaper: stays a scrim
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
                .setBackgroundColor((r, isDark) -> 0x4D000000)
                .setStrokeColorTop(0x33FFFFFF, 0x28FFFFFF)
                .setStrokeColorBottom(0x1F000000, 0x1F000000)
                .setShadowColor(0x40000000, 0x40000000)
                .setShadowLayer(dpf2(3f), 0, dpf2(1f))
                .setStrokeWidth(dpf2(0.67f), dpf2(0.67f))
                .build();
    }

    public static BlurredBackgroundProvider topPanelChatActivity(Theme.ResourcesProvider resourcesProvider) {
        return bevel(new BlurredBackgroundProviderBuilder(resourcesProvider)
                .setBackgroundColor((r, isDark) ->
                        plate(Theme.getColor(Theme.key_chat_topPanelBackground, r), isDark)),
                dpf2(6f), dpf2(2f))
                .build();
    }

    public static BlurredBackgroundProvider bulletin(Theme.ResourcesProvider resourcesProvider) {
        return bevel(new BlurredBackgroundProviderBuilder(resourcesProvider)
                .setBackgroundColor((r, isDark) ->
                        plate(Theme.getColor(Theme.key_undo_background, r), isDark)),
                dpf2(8f), dpf2(3f))
                .build();
    }

    public static BlurredBackgroundProvider inputFieldDialogActivity(Theme.ResourcesProvider resourcesProvider) {
        return topPanel(resourcesProvider);
    }

    public static BlurredBackgroundProvider inputFieldShareAlert(Theme.ResourcesProvider resourcesProvider) {
        // a text field is recessed, not raised: shading on top, highlight on the lip
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
                .setBackgroundColor((r, isDark) -> {
                    final int base = Theme.getColor(Theme.key_chat_messagePanelBackground, r);
                    return ColorUtils.setAlphaComponent(
                            ColorUtils.blendARGB(base, 0xFF000000, isDark ? 0.16f : 0.05f), 255);
                })
                .setStrokeColorTop(0x1F6B655C, 0x40000000)
                .setStrokeColorBottom(0xB3FFFFFF, 0x1AFFF6E8)
                .setShadowColor(0, 0)
                .setStrokeWidth(dpf2(1f), dpf2(0.67f))
                .build();
    }

    public static BlurredBackgroundProvider photoViewer(Theme.ResourcesProvider resourcesProvider) {
        // over media: no page colour to lift from, keep it transparent
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
                .setBackgroundColor((r, isDark) -> 0)
                .setStrokeColorTop(0x28FFFFFF, 0x28FFFFFF)
                .setStrokeColorBottom(0x14FFFFFF, 0x14FFFFFF)
                .setStrokeWidth(dpf2(2 / 3f), dpf2(2 / 3f))
                .build();
    }

    public static BlurredBackgroundProvider photoViewerMenu(Theme.ResourcesProvider resourcesProvider) {
        // over media: scrim, but with a bevel so it still reads as a physical panel
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
                .setBackgroundColor((r, isDark) -> 0x59000000)
                .setStrokeColorTop(0x33FFFFFF, 0x33FFFFFF)
                .setStrokeColorBottom(0x1F000000, 0x1F000000)
                .setShadowColor(0x4D000000, 0x4D000000)
                .setShadowLayer(dpf2(4f), 0, dpf2(1.5f))
                .setStrokeWidth(dpf2(0.67f), dpf2(0.67f))
                .build();
    }

    public static BlurredBackgroundProvider premiumButton(Theme.ResourcesProvider resourcesProvider) {
        return bevel(new BlurredBackgroundProviderBuilder(resourcesProvider)
                .setBackgroundColor((r, isDark) ->
                        plate(Theme.getColor(Theme.key_dialogBackground, r), isDark)),
                dpf2(6f), dpf2(2f))
                .build();
    }

    public static BlurredBackgroundProvider shadow(Theme.ResourcesProvider resourcesProvider) {
        return bevel(new BlurredBackgroundProviderBuilder(resourcesProvider), dpf2(7f), dpf2(2.5f))
                .build();
    }

    public static int solveSrcColor(int bgColor, int outColor, float alpha) {
        alpha = MathUtils.clamp(alpha, 0, 1);

        // Edge cases
        if (alpha <= 0f) {
            return Color.argb(0, 0, 0, 0);
        }
        if (alpha >= 1f) {
            return Color.argb(255, Color.red(outColor), Color.green(outColor), Color.blue(outColor));
        }

        final int bgR = Color.red(bgColor);
        final int bgG = Color.green(bgColor);
        final int bgB = Color.blue(bgColor);

        final int outR = Color.red(outColor);
        final int outG = Color.green(outColor);
        final int outB = Color.blue(outColor);

        final float invA = 1f - alpha;

        final int srcR = MathUtils.clamp(Math.round((outR - bgR * invA) / alpha), 0, 255);
        final int srcG = MathUtils.clamp(Math.round((outG - bgG * invA) / alpha), 0, 255);
        final int srcB = MathUtils.clamp(Math.round((outB - bgB * invA) / alpha), 0, 255);

        final int a8 = MathUtils.clamp(Math.round(alpha * 255f), 0, 255);

        return Color.argb(a8, srcR, srcG, srcB);
    }

    public static boolean checkBlurEnabled(Theme.ResourcesProvider resourcesProvider) {
        return checkBlurEnabled(UserConfig.selectedAccount, resourcesProvider);
    }

    public static boolean checkBlurEnabled(int currentAccount, Theme.ResourcesProvider resourcesProvider) {
        final boolean isDark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
        final boolean isLight = !isDark;
        boolean blurEnabled = SharedConfig.chatBlurEnabled();
        if (blurEnabled && isLight) {
            if (MessagesController.getInstance(currentAccount).config.disableBlurInLightTheme.get()) {
                blurEnabled = false;
            }
        }
        if (blurEnabled && isDark) {
            if (MessagesController.getInstance(currentAccount).config.disableBlurInDarkTheme.get()) {
                blurEnabled = false;
            }
        }
        return blurEnabled;
    }
}
