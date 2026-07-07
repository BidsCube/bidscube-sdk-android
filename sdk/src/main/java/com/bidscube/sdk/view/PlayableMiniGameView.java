package com.bidscube.sdk.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.bidscube.sdk.models.PlayableAdConfig;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Lightweight playable mini-game after intro video (triple-page ad pattern).
 * User drags a hero character to collect falling stars before time runs out.
 */
public class PlayableMiniGameView extends FrameLayout {

    public interface Listener {
        void onPlayableStarted();

        void onPlayableCompleted(int score);

        void onPlayableSkipped();
    }

    private static final int TICK_MS = 16;

    private final PlayableAdConfig config;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final List<FallingItem> items = new ArrayList<>();
    private final Paint itemPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint heroPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final TextView hintView;
    private final TextView scoreView;
    private final TextView skipView;
    private final GameCanvas gameCanvas;

    private float heroX;
    private float heroY;
    private float heroRadius;
    private int score;
    private int elapsedMs;
    private boolean finished;
    private boolean skipVisible;
    private long lastSpawnMs;

    private final Runnable tickTask = new Runnable() {
        @Override
        public void run() {
            if (finished) {
                return;
            }
            step(TICK_MS);
            handler.postDelayed(this, TICK_MS);
        }
    };

    public PlayableMiniGameView(@NonNull Context context, @NonNull PlayableAdConfig config, @NonNull Listener listener) {
        super(context);
        this.config = config;
        this.listener = listener;
        setBackgroundColor(0xFF1A237E);

        hintView = new TextView(context);
        hintView.setText(config.getHintText());
        hintView.setTextColor(Color.WHITE);
        hintView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        hintView.setPadding(dp(16), dp(48), dp(16), dp(8));
        addView(hintView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        scoreView = new TextView(context);
        scoreView.setText("0 / " + config.getGoalCollectibles());
        scoreView.setTextColor(Color.WHITE);
        scoreView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        LayoutParams scoreParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        scoreParams.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
        scoreParams.setMargins(dp(16), dp(48), dp(16), dp(16));
        scoreView.setLayoutParams(scoreParams);
        addView(scoreView);

        skipView = new TextView(context);
        skipView.setText("Skip playable");
        skipView.setTextColor(Color.WHITE);
        skipView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        skipView.setPadding(dp(12), dp(8), dp(12), dp(8));
        skipView.setBackgroundColor(0x99000000);
        skipView.setVisibility(GONE);
        skipView.setOnClickListener(v -> finishSkipped());
        LayoutParams skipParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        skipParams.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
        skipParams.setMargins(dp(16), dp(96), dp(16), dp(16));
        skipView.setLayoutParams(skipParams);
        addView(skipView);

        gameCanvas = new GameCanvas(context);
        addView(gameCanvas, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        itemPaint.setTextAlign(Paint.Align.CENTER);
        itemPaint.setTextSize(dp(28));
        heroPaint.setTextAlign(Paint.Align.CENTER);
        heroPaint.setTextSize(dp(40));
    }

    public void start() {
        post(() -> {
            heroRadius = dp(28);
            heroX = getWidth() / 2f;
            heroY = getHeight() - heroRadius - dp(32);
            items.clear();
            score = 0;
            elapsedMs = 0;
            finished = false;
            skipVisible = false;
            lastSpawnMs = 0;
            updateScoreLabel();
            listener.onPlayableStarted();
            handler.post(tickTask);
        });
    }

    public void stop() {
        finished = true;
        handler.removeCallbacks(tickTask);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (finished) {
            return super.onTouchEvent(event);
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
            heroX = event.getX();
            heroY = event.getY();
            clampHero();
            gameCanvas.invalidate();
            return true;
        }
        return super.onTouchEvent(event);
    }

    private void step(int deltaMs) {
        elapsedMs += deltaMs;
        if (elapsedMs >= config.getSkipAfterSeconds() * 1000L && !skipVisible) {
            skipVisible = true;
            skipView.setVisibility(VISIBLE);
        }
        if (elapsedMs >= config.getMaxSeconds() * 1000L) {
            finishCompleted();
            return;
        }
        if (elapsedMs - lastSpawnMs >= 700) {
            spawnItem();
            lastSpawnMs = elapsedMs;
        }
        float speed = dp(4);
        Iterator<FallingItem> it = items.iterator();
        while (it.hasNext()) {
            FallingItem item = it.next();
            item.y += speed;
            if (intersects(item)) {
                score++;
                updateScoreLabel();
                it.remove();
                if (score >= config.getGoalCollectibles()) {
                    finishCompleted();
                    return;
                }
            } else if (item.y > getHeight() + dp(32)) {
                it.remove();
            }
        }
        gameCanvas.invalidate();
    }

    private void spawnItem() {
        float x = dp(32) + random.nextFloat() * Math.max(1, getWidth() - dp(64));
        items.add(new FallingItem(x, -dp(32)));
    }

    private boolean intersects(FallingItem item) {
        float dx = heroX - item.x;
        float dy = heroY - item.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        return dist < heroRadius + dp(16);
    }

    private void clampHero() {
        heroX = Math.max(heroRadius, Math.min(getWidth() - heroRadius, heroX));
        heroY = Math.max(heroRadius + dp(80), Math.min(getHeight() - heroRadius, heroY));
    }

    private void updateScoreLabel() {
        scoreView.setText(score + " / " + config.getGoalCollectibles());
    }

    private void finishCompleted() {
        if (finished) {
            return;
        }
        stop();
        listener.onPlayableCompleted(score);
    }

    private void finishSkipped() {
        if (finished) {
            return;
        }
        stop();
        listener.onPlayableSkipped();
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private float dp(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private static final class FallingItem {
        float x;
        float y;

        FallingItem(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    private class GameCanvas extends View {
        GameCanvas(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            itemPaint.setColor(Color.parseColor("#FFD54F"));
            for (FallingItem item : items) {
                canvas.drawText("⭐", item.x, item.y, itemPaint);
            }
            heroPaint.setColor(Color.WHITE);
            canvas.drawText(config.getHeroEmoji(), heroX, heroY, heroPaint);
            heroPaint.setColor(0x44FFFFFF);
            canvas.drawOval(
                    new RectF(heroX - heroRadius, heroY - heroRadius, heroX + heroRadius, heroY + heroRadius),
                    heroPaint);
        }
    }
}
