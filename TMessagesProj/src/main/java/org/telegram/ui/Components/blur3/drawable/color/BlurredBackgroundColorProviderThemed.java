package org.telegram.ui.Components.blur3.drawable.color;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/**
 * KamiGram: the colour source for every floating panel in the app — the bottom tab
 * plate, the contacts/calls headers, the search tab strip.
 *
 * Upstream renders these as translucent glass: the background is drawn at 76-85%
 * alpha over a blurred capture, with a flat white hairline on top. KamiGram is
 * skeuomorphic, so the same panels are opaque, tinted slightly away from the page
 * behind them, and edged with a two-tone bevel — a lit highlight along the top and
 * a shaded lip along the bottom — plus a real drop shadow. The blur pipeline still
 * runs underneath, but it is no longer visible through the surface; what reads
 * instead is a solid plate sitting above the content.
 *
 * Both modes are handled: in light mode the plate lifts toward white and the bevel
 * highlight is near-white; in dark mode the plate lifts toward a warm grey and the
 * highlight is a soft warm white at low alpha, so the edge catches light without
 * glowing.
 */
public class BlurredBackgroundColorProviderThemed implements BlurredBackgroundColorProvider {

    /** Opaque: no glass. */
    private static final float PLATE_ALPHA = 1f;

    /** How far the plate lifts away from the surface behind it. */
    private static final float LIFT_LIGHT = 0.055f;
    private static final float LIFT_DARK = 0.075f;

    private final Theme.ResourcesProvider resourcesProvider;
    private final int backgroundColorId;
    private float alpha;

    public BlurredBackgroundColorProviderThemed(Theme.ResourcesProvider resourcesProvider, int backgroundColorId) {
        this(resourcesProvider, backgroundColorId, PLATE_ALPHA);
    }

    public BlurredBackgroundColorProviderThemed(Theme.ResourcesProvider resourcesProvider, int backgroundColorId, float alpha) {
        this.resourcesProvider = resourcesProvider;
        this.backgroundColorId = backgroundColorId;
        this.alpha = alpha;

        updateColors();
    }

    public void setAlpha(float alpha) {
        this.alpha = alpha;
        updateColors();
    }

    private int backgroundColor, shadowColor, strokeColorTop, strokeColorBottom;

    public boolean isDark() {
        final int color = Theme.getColor(backgroundColorId, resourcesProvider);
        return AndroidUtilities.computePerceivedBrightness(color) < .721f;
    }

    public void updateColors() {
        final int color = Theme.getColor(backgroundColorId, resourcesProvider);
        final boolean dark = isDark();

        // Lift the plate off the page so the shadow has something to separate.
        final int lifted = dark
                ? ColorUtils.blendARGB(color, 0xFFFFFFFF, LIFT_DARK)
                : ColorUtils.blendARGB(color, 0xFFFFFFFF, LIFT_LIGHT);
        backgroundColor = Theme.multAlpha(lifted, alpha);

        if (dark) {
            // warm highlight, restrained so the edge reads as a bevel and not a glow
            strokeColorTop = 0x30FFF6E8;
            strokeColorBottom = 0x24000000;
            shadowColor = 0x59000000;
        } else {
            strokeColorTop = 0xF2FFFFFF;
            strokeColorBottom = 0x1F6B655C;
            shadowColor = 0x33000000;
        }
    }

    @Override
    public int getShadowColor() {
        return shadowColor;
    }

    @Override
    public int getBackgroundColor() {
        return backgroundColor;
    }

    @Override
    public int getStrokeColorTop() {
        return strokeColorTop;
    }

    @Override
    public int getStrokeColorBottom() {
        return strokeColorBottom;
    }
}
