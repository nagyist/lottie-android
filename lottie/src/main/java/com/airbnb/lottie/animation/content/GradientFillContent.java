package com.airbnb.lottie.animation.content;

import static com.airbnb.lottie.utils.MiscUtils.clamp;

import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;

import com.airbnb.lottie.L;
import com.airbnb.lottie.LottieComposition;
import com.airbnb.lottie.LottieDrawable;
import com.airbnb.lottie.LottieProperty;
import com.airbnb.lottie.animation.LPaint;
import com.airbnb.lottie.animation.keyframe.BaseKeyframeAnimation;
import com.airbnb.lottie.animation.keyframe.ValueCallbackKeyframeAnimation;
import com.airbnb.lottie.model.KeyPath;
import com.airbnb.lottie.model.content.GradientColor;
import com.airbnb.lottie.model.content.GradientFill;
import com.airbnb.lottie.model.content.GradientType;
import com.airbnb.lottie.model.layer.BaseLayer;
import com.airbnb.lottie.utils.DropShadow;
import com.airbnb.lottie.utils.MiscUtils;
import com.airbnb.lottie.value.LottieValueCallback;

import java.util.ArrayList;
import java.util.List;

public class GradientFillContent
    implements DrawingContent, BaseKeyframeAnimation.AnimationListener, KeyPathElementContent {
  /**
   * Cache the gradients such that it runs at 30fps.
   */
  private static final int CACHE_STEPS_MS = 32;
  @NonNull private final String name;
  private final boolean hidden;
  private final BaseLayer layer;
  private final LongSparseArray<LinearGradient> linearGradientCache = new LongSparseArray<>();
  private final LongSparseArray<RadialGradient> radialGradientCache = new LongSparseArray<>();
  private final Path path = new Path();
  private final Paint paint = new LPaint(Paint.ANTI_ALIAS_FLAG);
  private final RectF boundsRect = new RectF();
  private final List<PathContent> paths = new ArrayList<>();
  private final GradientType type;
  private final BaseKeyframeAnimation<GradientColor, GradientColor> colorAnimation;
  private final BaseKeyframeAnimation<Integer, Integer> opacityAnimation;
  private final BaseKeyframeAnimation<PointF, PointF> startPointAnimation;
  private final BaseKeyframeAnimation<PointF, PointF> endPointAnimation;
  @Nullable private BaseKeyframeAnimation<ColorFilter, ColorFilter> colorFilterAnimation;
  @Nullable private ValueCallbackKeyframeAnimation colorCallbackAnimation;
  private final LottieDrawable lottieDrawable;
  private final int cacheSteps;
  @Nullable private BaseKeyframeAnimation<Float, Float> blurAnimation;
  float blurMaskFilterRadius = 0f;

  public GradientFillContent(final LottieDrawable lottieDrawable, LottieComposition composition, BaseLayer layer, GradientFill fill) {
    this.layer = layer;
    name = fill.getName();
    hidden = fill.isHidden();
    this.lottieDrawable = lottieDrawable;
    type = fill.getGradientType();
    path.setFillType(fill.getFillType());
    cacheSteps = (int) (composition.getDuration() / CACHE_STEPS_MS);

    colorAnimation = fill.getGradientColor().createAnimation();
    colorAnimation.addUpdateListener(this);
    layer.addAnimation(colorAnimation);

    opacityAnimation = fill.getOpacity().createAnimation();
    opacityAnimation.addUpdateListener(this);
    layer.addAnimation(opacityAnimation);

    startPointAnimation = fill.getStartPoint().createAnimation();
    startPointAnimation.addUpdateListener(this);
    layer.addAnimation(startPointAnimation);

    endPointAnimation = fill.getEndPoint().createAnimation();
    endPointAnimation.addUpdateListener(this);
    layer.addAnimation(endPointAnimation);

    if (layer.getBlurEffect() != null) {
      blurAnimation = layer.getBlurEffect().getBlurriness().createAnimation();
      blurAnimation.addUpdateListener(this);
      layer.addAnimation(blurAnimation);
    }
  }

  @Override public void onValueChanged() {
    lottieDrawable.invalidateSelf();
  }

  @Override public void setContents(List<Content> contentsBefore, List<Content> contentsAfter) {
    for (int i = 0; i < contentsAfter.size(); i++) {
      Content content = contentsAfter.get(i);
      if (content instanceof PathContent) {
        paths.add((PathContent) content);
      }
    }
  }

  @Override public void draw(Canvas canvas, Matrix parentMatrix, int parentAlpha, @Nullable DropShadow shadowToApply) {
    if (hidden) {
      return;
    }
    if (L.isTraceEnabled()) {
      L.beginSection("GradientFillContent#draw");
    }
    path.reset();
    for (int i = 0; i < paths.size(); i++) {
      path.addPath(paths.get(i).getPath(), parentMatrix);
    }

    path.computeBounds(boundsRect, false);

    Shader shader;
    if (type == GradientType.LINEAR) {
      shader = getLinearGradient();
    } else {
      shader = getRadialGradient();
    }
    shader.setLocalMatrix(parentMatrix);
    paint.setShader(shader);

    if (colorFilterAnimation != null) {
      paint.setColorFilter(colorFilterAnimation.getValue());
    }

    if (blurAnimation != null) {
      float blurRadius = blurAnimation.getValue();
      if (blurRadius == 0f) {
        paint.setMaskFilter(null);
      } else if (blurRadius != blurMaskFilterRadius){
        BlurMaskFilter blur = new BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL);
        paint.setMaskFilter(blur);
      }
      blurMaskFilterRadius = blurRadius;
    }

    float fillAlpha = opacityAnimation.getValue() / 100f;
    int alpha = (int) (parentAlpha * fillAlpha);
    alpha = clamp(alpha, 0, 255);
    paint.setAlpha(alpha);

    if (shadowToApply != null) {
      shadowToApply.applyWithAlpha((int)(fillAlpha * 255), paint);
    }

    canvas.drawPath(path, paint);
    if (L.isTraceEnabled()) {
      L.endSection("GradientFillContent#draw");
    }
  }

  @Override public void getBounds(RectF outBounds, Matrix parentMatrix, boolean applyParents) {
    path.reset();
    for (int i = 0; i < paths.size(); i++) {
      path.addPath(paths.get(i).getPath(), parentMatrix);
    }

    path.computeBounds(outBounds, false);
    // Add padding to account for rounding errors.
    outBounds.set(
        outBounds.left - 1,
        outBounds.top - 1,
        outBounds.right + 1,
        outBounds.bottom + 1
    );
  }

  @Override public String getName() {
    return name;
  }

  private LinearGradient getLinearGradient() {
    int gradientHash = getGradientHash();
    LinearGradient gradient = linearGradientCache.get(gradientHash);
    if (gradient != null) {
      return gradient;
    }
    PointF startPoint = startPointAnimation.getValue();
    PointF endPoint = endPointAnimation.getValue();
    GradientColor gradientColor = colorAnimation.getValue();
    int[] colors = applyDynamicColorsIfNeeded(gradientColor.getColors());
    float[] positions = gradientColor.getPositions();

    // Add check for minimum colors
    if (colors.length < 2) {
      // If only one color, duplicate it to create a solid color gradient
      colors = new int[]{colors[0], colors[0]};
      positions = new float[]{0f, 1f};
    }
    
    gradient = new LinearGradient(startPoint.x, startPoint.y, endPoint.x, endPoint.y, colors,
        positions, Shader.TileMode.CLAMP);
    linearGradientCache.put(gradientHash, gradient);
    return gradient;
  }

  private RadialGradient getRadialGradient() {
    int gradientHash = getGradientHash();
    RadialGradient gradient = radialGradientCache.get(gradientHash);
    if (gradient != null) {
      return gradient;
    }
    PointF startPoint = startPointAnimation.getValue();
    PointF endPoint = endPointAnimation.getValue();
    GradientColor gradientColor = colorAnimation.getValue();
    int[] colors = applyDynamicColorsIfNeeded(gradientColor.getColors());
    float[] positions = gradientColor.getPositions();
    
    // Add check for minimum colors
    if (colors.length < 2) {
        // If only one color, duplicate it to create a solid color gradient
        colors = new int[]{colors[0], colors[0]};
        positions = new float[]{0f, 1f};
    }
    
    float x0 = startPoint.x;
    float y0 = startPoint.y;
    float x1 = endPoint.x;
    float y1 = endPoint.y;
    float r = (float) Math.hypot(x1 - x0, y1 - y0);
    if (r <= 0) {
        r = 0.001f;
    }
    gradient = new RadialGradient(x0, y0, r, colors, positions, Shader.TileMode.CLAMP);
    radialGradientCache.put(gradientHash, gradient);
    return gradient;
  }

  private int getGradientHash() {
    int startPointProgress = Math.round(startPointAnimation.getProgress() * cacheSteps);
    int endPointProgress = Math.round(endPointAnimation.getProgress() * cacheSteps);
    int colorProgress = Math.round(colorAnimation.getProgress() * cacheSteps);
    int hash = 17;
    if (startPointProgress != 0) {
      hash = hash * 31 * startPointProgress;
    }
    if (endPointProgress != 0) {
      hash = hash * 31 * endPointProgress;
    }
    if (colorProgress != 0) {
      hash = hash * 31 * colorProgress;
    }
    return hash;
  }

  private int[] applyDynamicColorsIfNeeded(int[] colors) {
    if (colorCallbackAnimation != null) {
      Integer[] dynamicColors = (Integer[]) colorCallbackAnimation.getValue();
      if (colors.length == dynamicColors.length) {
        for (int i = 0; i < colors.length; i++) {
          colors[i] = dynamicColors[i];
        }
      } else {
        colors = new int[dynamicColors.length];
        for (int i = 0; i < dynamicColors.length; i++) {
          colors[i] = dynamicColors[i];
        }
      }
    }
    return colors;
  }

  @Override public void resolveKeyPath(
      KeyPath keyPath, int depth, List<KeyPath> accumulator, KeyPath currentPartialKeyPath) {
    MiscUtils.resolveKeyPath(keyPath, depth, accumulator, currentPartialKeyPath, this);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> void addValueCallback(T property, @Nullable LottieValueCallback<T> callback) {
    if (property == LottieProperty.OPACITY) {
      opacityAnimation.setValueCallback((LottieValueCallback<Integer>) callback);
    } else if (property == LottieProperty.COLOR_FILTER) {
      if (colorFilterAnimation != null) {
        layer.removeAnimation(colorFilterAnimation);
      }

      if (callback == null) {
        colorFilterAnimation = null;
      } else {
        colorFilterAnimation =
            new ValueCallbackKeyframeAnimation<>((LottieValueCallback<ColorFilter>) callback);
        colorFilterAnimation.addUpdateListener(this);
        layer.addAnimation(colorFilterAnimation);
      }
    } else if (property == LottieProperty.GRADIENT_COLOR) {
      if (colorCallbackAnimation != null) {
        layer.removeAnimation(colorCallbackAnimation);
      }

      if (callback == null) {
        colorCallbackAnimation = null;
      } else {
        linearGradientCache.clear();
        radialGradientCache.clear();
        colorCallbackAnimation = new ValueCallbackKeyframeAnimation<>(callback);
        colorCallbackAnimation.addUpdateListener(this);
        layer.addAnimation(colorCallbackAnimation);
      }
    } else if (property == LottieProperty.BLUR_RADIUS) {
      if (blurAnimation != null) {
        blurAnimation.setValueCallback((LottieValueCallback<Float>) callback);
      } else {
        blurAnimation =
            new ValueCallbackKeyframeAnimation<>((LottieValueCallback<Float>) callback);
        blurAnimation.addUpdateListener(this);
        layer.addAnimation(blurAnimation);
      }
    }
  }
}
