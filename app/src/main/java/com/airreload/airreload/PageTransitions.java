package com.airreload.airreload;

import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;

/** Short directional transitions; canceled transitions always leave one settled page. */
final class PageTransitions {
  private View incoming;
  private View outgoing;
  private FrameLayout host;

  void finish() {
    if (incoming != null) {
      incoming.animate().setListener(null).withEndAction(null);
      incoming.animate().cancel();
      incoming.setAlpha(1f);
      incoming.setTranslationX(0f);
    }
    if (outgoing != null) {
      outgoing.animate().cancel();
      if (host != null) host.removeView(outgoing);
    }
    incoming = null;
    outgoing = null;
    host = null;
  }

  void play(FrameLayout container, View previous, View next, boolean back) {
    if (previous == null || !ValueAnimator.areAnimatorsEnabled()) return;
    host = container;
    incoming = next;
    outgoing = previous;
    next.setClickable(true);
    previous.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    previous.setEnabled(false);
    container.addView(previous, 0);
    float distance = Math.max(container.getWidth() * .09f,
        28f * container.getResources().getDisplayMetrics().density);
    float direction = back ? -1f : 1f;
    next.setTranslationX(direction * distance);
    next.setAlpha(0f);
    PathInterpolator easing = new PathInterpolator(.2f, 0f, 0f, 1f);
    previous.animate().translationX(-direction * distance * .4f).alpha(0f)
        .setDuration(220).setInterpolator(easing).start();
    next.animate().translationX(0f).alpha(1f).setDuration(300).setInterpolator(easing)
        .withEndAction(this::finish).start();
  }
}
