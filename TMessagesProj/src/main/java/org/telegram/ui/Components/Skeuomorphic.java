package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/**
 * KamiGram skeuomorphic depth engine.
 *
 * Every raised or recessed surface in the app is drawn through this class, so the
 * whole UI shares one light model instead of each widget inventing its own shading.
 *
 * <h3>Light model</h3>
 * A single light sits at the top-left, as in physical UI metaphors. From that one
 * assumption everything else follows:
 *
 * <pre>
 *   RAISED                                RECESSED
 *   ------                                --------
 *   light shadow  offset (-d, -d)         dark  inner shadow at the top-left edge
 *   dark  shadow  offset (+d, +d)         light inner shadow at the bottom-right edge
 *   face gradient light -> dark, top-down face gradient dark -> light, top-down
 * </pre>
 *
 * <h3>Outer shadow math</h3>
 * Android's {@code Paint.setShadowLayer} casts exactly one shadow, so a dual shadow
 * needs two passes. The trick is that both passes fill with the <em>same opaque
 * colour</em>: the second pass's fill covers no new pixels, so both shadows survive
 * outside the shape while the interior stays flat.
 *
 * <pre>
 *   paint.setColor(base);
 *   paint.setShadowLayer(blur, +d, +d, darkShadow);   canvas.drawRoundRect(r, ...);
 *   paint.setShadowLayer(blur, -d, -d, lightShadow);  canvas.drawRoundRect(r, ...);
 * </pre>
 *
 * <h3>Inner shadow math</h3>
 * There is no inner-shadow API. Instead, clip to the shape, translate the canvas by
 * {@code +d}, and cast a shadow back by {@code -d}. The shape's fill lands offset
 * down-right (covered, invisible), while its shadow falls up-left <em>inside</em> the
 * clip, hugging the exact rounded outline. Repeat mirrored for the light edge:
 *
 * <pre>
 *   canvas.clipPath(shape);
 *   canvas.translate(+d, +d);  paint.setShadowLayer(blur, -d, -d, darkShadow);   draw
 *   canvas.translate(-d, -d);  paint.setShadowLayer(blur, +d, +d, lightShadow);  draw
 * </pre>
 *
 * Unlike gradient-band approximations this follows real corner curvature, which is
 * what makes a pill-shaped well read as carved rather than merely striped.
 *
 * <h3>Dark mode</h3>
 * Depth cannot be produced by mirroring the light values. On a dark surface a white
 * highlight glows instead of catching light, so dark mode leans on the shadow
 * (opaque, generous blur) and keeps the highlight as a thin low-alpha rim. Every
 * colour helper below therefore takes an {@code dark} flag rather than inverting.
 *
 * <h3>Hardware acceleration</h3>
 * {@code setShadowLayer} is honoured by the hardware canvas from API 28. Below that
 * the shadows are dropped and shapes render flat but correctly coloured, so the UI
 * degrades to a clean flat design rather than breaking. The rim strokes are ordinary
 * geometry and always draw.
 */
public final class Skeuomorphic {

    private Skeuomorphic() {
    }

    private static final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint facePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path clipPath = new Path();
    private static final RectF tmpRect = new RectF();

    // ------------------------------------------------------------------ colours

    /** Light-catching shadow, cast up-left from a raised element. */
    public static int lightShadow(int base, boolean dark) {
        return dark
                ? ColorUtils.setAlphaComponent(ColorUtils.blendARGB(base, Color.WHITE, 0.22f), 70)
                : ColorUtils.setAlphaComponent(Color.WHITE, 235);
    }

    /** Occlusion shadow, cast down-right from a raised element. */
    public static int darkShadow(int base, boolean dark) {
        return dark
                ? ColorUtils.setAlphaComponent(Color.BLACK, 205)
                : ColorUtils.setAlphaComponent(ColorUtils.blendARGB(base, 0xFF1B2A3A, 0.55f), 105);
    }

    /** Lit top edge of a raised face. */
    public static int rimLight(boolean dark) {
        return dark ? 0x26FFFFFF : 0xCCFFFFFF;
    }

    /** Shaded bottom lip of a raised face. */
    public static int rimShade(boolean dark) {
        return dark ? 0x4D000000 : 0x1A1B2A3A;
    }

    public static boolean isDark(Theme.ResourcesProvider resourcesProvider) {
        return resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
    }

    // ------------------------------------------------------------------- raised

    /**
     * Draws a raised rounded rectangle: dual outer shadows, a top-lit face gradient,
     * a bright top rim and a shaded bottom lip.
     *
     * @param depthDp how far the element stands off the page, in dp
     */
    public static void drawRaisedRound(Canvas canvas, RectF rect, float radius,
                                      int base, boolean dark, float depthDp) {
        if (rect.isEmpty()) {
            return;
        }
        final float d = AndroidUtilities.dpf2(depthDp * 0.72f);
        final float blur = AndroidUtilities.dpf2(depthDp * 1.15f);

        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(base);

        // two passes, same opaque fill: one shadow each side, interior unaffected
        paint.setShadowLayer(blur, d, d, darkShadow(base, dark));
        canvas.drawRoundRect(rect, radius, radius, paint);
        paint.setShadowLayer(blur, -d, -d, lightShadow(base, dark));
        canvas.drawRoundRect(rect, radius, radius, paint);
        paint.clearShadowLayer();

        drawFace(canvas, rect, radius, base, dark);
        drawRim(canvas, rect, radius, dark);
    }

    /** Raised circle: knobs, avatars, floating buttons. */
    public static void drawRaisedCircle(Canvas canvas, float cx, float cy, float radius,
                                        int base, boolean dark, float depthDp) {
        if (radius <= 0) {
            return;
        }
        final float d = AndroidUtilities.dpf2(depthDp * 0.72f);
        final float blur = AndroidUtilities.dpf2(depthDp * 1.15f);

        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(base);

        paint.setShadowLayer(blur, d, d, darkShadow(base, dark));
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setShadowLayer(blur, -d, -d, lightShadow(base, dark));
        canvas.drawCircle(cx, cy, radius, paint);
        paint.clearShadowLayer();

        tmpRect.set(cx - radius, cy - radius, cx + radius, cy + radius);
        drawFace(canvas, tmpRect, radius, base, dark);
        drawRim(canvas, tmpRect, radius, dark);
    }

    /**
     * Top-lit face gradient. Subtle by design: the shadows carry most of the depth,
     * and an aggressive gradient here makes flat colours look dirty.
     */
    private static void drawFace(Canvas canvas, RectF rect, float radius, int base, boolean dark) {
        final int top = ColorUtils.blendARGB(base, Color.WHITE, dark ? 0.10f : 0.55f);
        final int bottom = ColorUtils.blendARGB(base, Color.BLACK, dark ? 0.12f : 0.045f);
        facePaint.setShader(new LinearGradient(
                rect.left, rect.top, rect.left, rect.bottom,
                new int[]{top, base, bottom},
                new float[]{0f, 0.58f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawRoundRect(rect, radius, radius, facePaint);
        facePaint.setShader(null);
    }

    /** Bright top rim plus shaded bottom lip, drawn as two clipped half strokes. */
    private static void drawRim(Canvas canvas, RectF rect, float radius, boolean dark) {
        final float w = AndroidUtilities.dpf2(dark ? 0.83f : 1f);
        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeWidth(w);

        tmpRect.set(rect);
        tmpRect.inset(w / 2f, w / 2f);

        int save = canvas.save();
        canvas.clipRect(rect.left, rect.top, rect.right, rect.centerY());
        rimPaint.setColor(rimLight(dark));
        canvas.drawRoundRect(tmpRect, radius, radius, rimPaint);
        canvas.restoreToCount(save);

        save = canvas.save();
        canvas.clipRect(rect.left, rect.centerY(), rect.right, rect.bottom);
        rimPaint.setColor(rimShade(dark));
        canvas.drawRoundRect(tmpRect, radius, radius, rimPaint);
        canvas.restoreToCount(save);
    }

    // ----------------------------------------------------------------- recessed

    /**
     * Draws a recessed rounded rectangle: a well carved into the page. Used for
     * switch tracks, search fields and the message composer.
     */
    public static void drawRecessedRound(Canvas canvas, RectF rect, float radius,
                                         int base, boolean dark, float depthDp) {
        if (rect.isEmpty()) {
            return;
        }
        final float d = AndroidUtilities.dpf2(Math.max(0.83f, depthDp * 0.62f));
        final float blur = AndroidUtilities.dpf2(depthDp * 1.5f);

        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);

        // floor of the well: slightly darker than the surrounding page
        final int floor = ColorUtils.blendARGB(base, Color.BLACK, dark ? 0.18f : 0.05f);
        paint.setColor(floor);
        canvas.drawRoundRect(rect, radius, radius, paint);

        clipPath.reset();
        clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW);

        final int outer = canvas.save();
        canvas.clipPath(clipPath);

        // dark inner shadow hugging the top-left edge
        int save = canvas.save();
        canvas.translate(d, d);
        paint.setShadowLayer(blur, -d, -d, darkShadow(base, dark));
        canvas.drawRoundRect(rect, radius, radius, paint);
        canvas.restoreToCount(save);

        // light inner shadow along the bottom-right edge
        save = canvas.save();
        canvas.translate(-d, -d);
        paint.setShadowLayer(blur, d, d, lightShadow(base, dark));
        canvas.drawRoundRect(rect, radius, radius, paint);
        canvas.restoreToCount(save);

        paint.clearShadowLayer();
        canvas.restoreToCount(outer);

        // hairline lip so the rim reads even where shadows are unsupported
        final float w = AndroidUtilities.dpf2(dark ? 0.83f : 1f);
        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeWidth(w);
        tmpRect.set(rect);
        tmpRect.inset(w / 2f, w / 2f);

        save = canvas.save();
        canvas.clipRect(rect.left, rect.centerY(), rect.right, rect.bottom);
        rimPaint.setColor(rimLight(dark));
        canvas.drawRoundRect(tmpRect, radius, radius, rimPaint);
        canvas.restoreToCount(save);
    }

    /** Pill-shaped convenience wrapper: radius is half the height. */
    public static void drawRecessedPill(Canvas canvas, RectF rect, int base, boolean dark, float depthDp) {
        drawRecessedRound(canvas, rect, rect.height() / 2f, base, dark, depthDp);
    }

    /** Pill-shaped convenience wrapper: radius is half the height. */
    public static void drawRaisedPill(Canvas canvas, RectF rect, int base, boolean dark, float depthDp) {
        drawRaisedRound(canvas, rect, rect.height() / 2f, base, dark, depthDp);
    }

    // ----------------------------------------------------------------- coin frame

    /**
     * An embossed coin edge drawn strictly <em>inside</em> the circle, so avatars can
     * be framed no matter how their host view clips. This is the same treatment as
     * the raised unread badge, expressed as a ring instead of a filled pill:
     *
     * <ul>
     *   <li>a lit arc along the top-left, where the light model says light lands</li>
     *   <li>a shaded arc along the bottom-right</li>
     *   <li>a soft inner shadow cast from the rim onto the picture, so the photo
     *       appears to sit below the coin's raised edge</li>
     * </ul>
     *
     * Called at the end of every avatar draw (BackupImageView), so profile photos,
     * channel headers, chat avatars and generated avatars all share one frame.
     *
     * @param r the avatar radius, in px
     */
    public static void drawCoinFrame(Canvas canvas, float cx, float cy, float r, boolean dark) {
        if (r <= 0) {
            return;
        }
        final float w = Math.max(1f, AndroidUtilities.dpf2(dark ? 0.83f : 1f));

        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeWidth(w);

        // lit arc: top-left quadrant
        rimPaint.setColor(rimLight(dark));
        canvas.drawArc(cx - r + w / 2f, cy - r + w / 2f,
                cx + r - w / 2f, cy + r - w / 2f,
                135f, 110f, false, rimPaint);

        // shaded arc: bottom-right quadrant
        rimPaint.setColor(rimShade(dark));
        canvas.drawArc(cx - r + w / 2f, cy - r + w / 2f,
                cx + r - w / 2f, cy + r - w / 2f,
                -45f, 110f, false, rimPaint);

        // inner shadow falling from the lit edge onto the picture: a translucent
        // dark ring just inside the rim, thicker toward the top
        final float shadowW = Math.max(1f, AndroidUtilities.dpf2(1.7f));
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(shadowW);
        paint.setColor(dark ? 0x59000000 : 0x2E1B2A3A);
        canvas.drawArc(cx - r + w + shadowW / 2f, cy - r + w + shadowW / 2f,
                cx + r - w - shadowW / 2f, cy + r - w - shadowW / 2f,
                120f, 150f, false, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    // ---------------------------------------------------------------- drawables

    /**
     * Raised circle as a {@link Drawable}, so existing view backgrounds pick up depth
     * without touching their layout code. Backs every circular button in the app.
     */
    public static class RaisedCircleDrawable extends Drawable {

        private final int color;
        private final float depthDp;
        private final Theme.ResourcesProvider resourcesProvider;
        private int alpha = 255;

        public RaisedCircleDrawable(int color, float depthDp, Theme.ResourcesProvider resourcesProvider) {
            this.color = color;
            this.depthDp = depthDp;
            this.resourcesProvider = resourcesProvider;
        }

        @Override
        public void draw(Canvas canvas) {
            final Rect b = getBounds();
            if (b.isEmpty()) {
                return;
            }
            // leave room for the shadow so it is not clipped by the view bounds
            final float inset = AndroidUtilities.dpf2(depthDp);
            final float r = Math.min(b.width(), b.height()) / 2f - inset;
            if (r <= 0) {
                return;
            }
            final int c = alpha == 255 ? color : ColorUtils.setAlphaComponent(color, alpha * Color.alpha(color) / 255);
            drawRaisedCircle(canvas, b.exactCenterX(), b.exactCenterY(), r, c,
                    isDark(resourcesProvider), depthDp);
        }

        @Override
        public void setAlpha(int alpha) {
            this.alpha = alpha;
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /**
     * Raised rounded rectangle as a {@link Drawable}: settings groups, list cards,
     * profile blocks.
     */
    public static class RaisedRoundDrawable extends Drawable {

        private final int color;
        private final float radiusDp;
        private final float depthDp;
        private final Theme.ResourcesProvider resourcesProvider;
        private final RectF rect = new RectF();
        private int alpha = 255;

        public RaisedRoundDrawable(int color, float radiusDp, float depthDp, Theme.ResourcesProvider resourcesProvider) {
            this.color = color;
            this.radiusDp = radiusDp;
            this.depthDp = depthDp;
            this.resourcesProvider = resourcesProvider;
        }

        @Override
        public void draw(Canvas canvas) {
            final Rect b = getBounds();
            if (b.isEmpty()) {
                return;
            }
            final float inset = AndroidUtilities.dpf2(depthDp);
            rect.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset);
            final int c = alpha == 255 ? color : ColorUtils.setAlphaComponent(color, alpha * Color.alpha(color) / 255);
            drawRaisedRound(canvas, rect, AndroidUtilities.dpf2(radiusDp), c,
                    isDark(resourcesProvider), depthDp);
        }

        @Override
        public void setAlpha(int alpha) {
            this.alpha = alpha;
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** Recessed well as a {@link Drawable}: search fields, input tracks. */
    public static class RecessedRoundDrawable extends Drawable {

        private final int color;
        private final float radiusDp;
        private final float depthDp;
        private final Theme.ResourcesProvider resourcesProvider;
        private final RectF rect = new RectF();
        private int alpha = 255;

        public RecessedRoundDrawable(int color, float radiusDp, float depthDp, Theme.ResourcesProvider resourcesProvider) {
            this.color = color;
            this.radiusDp = radiusDp;
            this.depthDp = depthDp;
            this.resourcesProvider = resourcesProvider;
        }

        @Override
        public void draw(Canvas canvas) {
            final Rect b = getBounds();
            if (b.isEmpty()) {
                return;
            }
            rect.set(b);
            final float radius = radiusDp < 0 ? rect.height() / 2f : AndroidUtilities.dpf2(radiusDp);
            final int c = alpha == 255 ? color : ColorUtils.setAlphaComponent(color, alpha * Color.alpha(color) / 255);
            drawRecessedRound(canvas, rect, radius, c, isDark(resourcesProvider), depthDp);
        }

        @Override
        public void setAlpha(int alpha) {
            this.alpha = alpha;
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
