package com.photography.focalsim;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * =============================================
 * OverlayView - 焦段取景框覆盖层 (v2)
 * =============================================
 *
 * 完整焦段：9/11/12/14/16/24/35/70/200/400mm
 *
 * 绘制算法：
 *   ratio = 24.0 / focalLength
 *   - ratio > 1 → 超广角组(9/11/12/14/16mm)
 *     全屏画面，绘制24mm白色虚线参考内框
 *   - ratio = 1 → 基准(24mm)，全屏无遮罩
 *   - ratio < 1 → 长焦组(35/70/200/400mm)
 *     绘制半透明遮罩 + 黄色取景框
 *
 * 取景框比例速查：
 *   9mm:  24/9  = 2.667  → 参考内框占屏 37.5%
 *   11mm: 24/11 = 2.182  → 参考内框占屏 45.8%
 *   12mm: 24/12 = 2.0    → 参考内框占屏 50%
 *   14mm: 24/14 = 1.714  → 参考内框占屏 58.3%
 *   16mm: 24/16 = 1.5    → 参考内框占屏 66.7%
 *   24mm: 24/24 = 1.0    → 全屏(基准)
 *   35mm: 24/35 ≈ 0.686  → 框占屏 68.6%
 *   70mm: 24/70 ≈ 0.343  → 框占屏 34.3%
 *   200mm: 24/200 = 0.12  → 框占屏 12%
 *   400mm: 24/400 = 0.06  → 框占屏 6%
 */
public class OverlayView extends View {

    // ============ 焦段相关 ============
    /** 当前焦段（单位mm），默认24mm */
    private int focalLength = 24;

    // ============ 画笔 ============
    private Paint framePaint;       // 黄色取景框画笔
    private Paint maskPaint;        // 半透明遮罩画笔
    private Paint gridPaint;        // 三分法网格线画笔
    private Paint crossPaint;       // 中心十字画笔
    private Paint refPaint;         // 24mm参考框画笔（白色虚线）
    private Paint labelPaint;       // 焦段标注文字画笔
    private Paint subLabelPaint;    // 场景描述小字画笔
    private Paint wideLabelPaint;   // 超广角底部焦段标注画笔

    /** 屏幕密度，用于dp转px */
    private float density;

    /**
     * XML构造函数
     */
    public OverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * 初始化所有画笔
     */
    private void init() {
        density = getResources().getDisplayMetrics().density;

        // ---------- 黄色取景框画笔 ----------
        framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        framePaint.setColor(Color.parseColor("#FFD700"));
        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setStrokeWidth(dpToPx(3));
        framePaint.setStrokeJoin(Paint.Join.MITER);

        // ---------- 半透明黑色遮罩画笔 ----------
        maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        maskPaint.setColor(Color.parseColor("#8C000000"));
        maskPaint.setStyle(Paint.Style.FILL);

        // ---------- 三分法网格线画笔 ----------
        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(dpToPx(0.8f));

        // ---------- 中心十字画笔 ----------
        crossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        crossPaint.setStyle(Paint.Style.STROKE);
        crossPaint.setStrokeWidth(dpToPx(1));

        // ---------- 24mm参考框画笔（白色虚线） ----------
        refPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        refPaint.setColor(Color.parseColor("#66FFFFFF"));
        refPaint.setStyle(Paint.Style.STROKE);
        refPaint.setStrokeWidth(dpToPx(1.5f));
        refPaint.setPathEffect(new DashPathEffect(
                new float[]{dpToPx(10), dpToPx(8)}, 0));

        // ---------- 焦段标注文字画笔（长焦框内/框外） ----------
        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(Color.parseColor("#FFD700"));
        labelPaint.setTextSize(dpToPx(15f));
        labelPaint.setFakeBoldText(true);
        labelPaint.setShadowLayer(dpToPx(3), 1, 1, Color.parseColor("#80000000"));

        // ---------- 场景描述小字画笔 ----------
        subLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        subLabelPaint.setColor(Color.parseColor("#CCFFD700"));
        subLabelPaint.setTextSize(dpToPx(12f));
        subLabelPaint.setShadowLayer(dpToPx(2), 1, 1, Color.parseColor("#80000000"));

        // ---------- 超广角底部焦段标注画笔 ----------
        wideLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wideLabelPaint.setColor(Color.parseColor("#FFD700"));
        wideLabelPaint.setTextSize(dpToPx(16f));
        wideLabelPaint.setFakeBoldText(true);
        wideLabelPaint.setShadowLayer(dpToPx(4), 1, 1, Color.parseColor("#80000000"));
    }

    // ================================================================
    //  公开接口
    // ================================================================

    /**
     * 切换焦段
     * @param focal 目标焦段
     */
    public void setFocalLength(int focal) {
        this.focalLength = focal;
        invalidate();
    }

    // ================================================================
    //  核心绘制逻辑
    // ================================================================

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int W = getWidth();
        int H = getHeight();
        if (W == 0 || H == 0) return;

        float ratio = 24.0f / focalLength;

        // -------- 24mm 基准：全屏三分法网格 --------
        if (focalLength == 24) {
            drawFullFrameGrid(canvas, W, H);
            return;
        }

        // -------- 超广角组（ratio > 1）--------
        if (ratio > 1.0f) {
            drawUltraWideOverlay(canvas, W, H, ratio);
            return;
        }

        // -------- 长焦组（ratio < 1）--------
        drawTeleOverlay(canvas, W, H, ratio);
    }

    // ================================================================
    //  绘制场景1：24mm 基准 - 全屏三分法网格
    // ================================================================

    private void drawFullFrameGrid(Canvas canvas, int W, int H) {
        gridPaint.setColor(Color.parseColor("#1FFFFFFF"));
        for (int i = 1; i <= 2; i++) {
            float x = W * i / 3f;
            float y = H * i / 3f;
            canvas.drawLine(x, 0, x, H, gridPaint);
            canvas.drawLine(0, y, W, y, gridPaint);
        }
        drawCenterCross(canvas, W / 2f, H / 2f, dpToPx(20),
                Color.parseColor("#1FFFFFFF"));
    }

    // ================================================================
    //  绘制场景2：超广角组 - 全屏画面 + 24mm参考内框
    // ================================================================

    /**
     * 超广角组绘制逻辑（9/11/12/14/16mm）
     * 全屏显示超广角画面，在中央绘制24mm参考内框（白色虚线）
     *
     * @param ratio 24/focalLength（大于1）
     */
    private void drawUltraWideOverlay(Canvas canvas, int W, int H, float ratio) {
        // 全屏三分法网格
        gridPaint.setColor(Color.parseColor("#14FFFFFF"));
        for (int i = 1; i <= 2; i++) {
            float x = W * i / 3f;
            float y = H * i / 3f;
            canvas.drawLine(x, 0, x, H, gridPaint);
            canvas.drawLine(0, y, W, y, gridPaint);
        }

        // 计算24mm参考内框尺寸
        float innerRatio = 1.0f / ratio;
        float frameW = W * innerRatio;
        float frameH = H * innerRatio;
        float left = (W - frameW) / 2f;
        float top = (H - frameH) / 2f;

        // 绘制24mm参考内框（白色虚线矩形）
        RectF refRect = new RectF(left, top, left + frameW, top + frameH);
        canvas.drawRect(refRect, refPaint);

        // 参考框四角装饰线
        float bracketLen = Math.min(dpToPx(18), frameW * 0.12f);
        drawCornerBrackets(canvas, left, top, frameW, frameH, bracketLen, refPaint);

        // 参考框标签
        labelPaint.setColor(Color.parseColor("#AAFFFFFF"));
        canvas.drawText("24mm 参考", left + dpToPx(8), top - dpToPx(6), labelPaint);
        labelPaint.setColor(Color.parseColor("#FFD700")); // 恢复颜色

        // 参考框内的三分法网格
        gridPaint.setColor(Color.parseColor("#0FFFFFFF"));
        for (int i = 1; i <= 2; i++) {
            float gx = left + frameW * i / 3f;
            float gy = top + frameH * i / 3f;
            canvas.drawLine(gx, top, gx, top + frameH, gridPaint);
            canvas.drawLine(left, gy, left + frameW, gy, gridPaint);
        }

        // 当前焦段标注（屏幕左下角，避免遮挡画面中央）
        canvas.drawText(focalLength + "mm", dpToPx(16), H - dpToPx(56), wideLabelPaint);
        subLabelPaint.setTextSize(dpToPx(12f));
        canvas.drawText(getSceneDesc(focalLength), dpToPx(16), H - dpToPx(38), subLabelPaint);
    }

    // ================================================================
    //  绘制场景3：长焦组 - 遮罩 + 黄色取景框
    // ================================================================

    /**
     * 长焦组绘制逻辑（35/70/200/400mm）
     * 1. 计算取景框尺寸 = 屏幕尺寸 × ratio
     * 2. 框外区域绘制半透明黑色遮罩
     * 3. 框线使用亮黄色，线宽3dp
     * 4. 框内绘制三分法网格和中心十字（框足够大时）
     * 5. 400mm等超小框确保线宽和标注清晰可见
     *
     * @param ratio 24/focalLength（小于1）
     */
    private void drawTeleOverlay(Canvas canvas, int W, int H, float ratio) {
        float frameW = W * ratio;
        float frameH = H * ratio;
        float left = (W - frameW) / 2f;
        float top = (H - frameH) / 2f;
        float right = left + frameW;
        float bottom = top + frameH;

        // ---- 半透明遮罩 ----
        canvas.drawRect(0, 0, W, top, maskPaint);            // 上
        canvas.drawRect(0, bottom, W, H, maskPaint);          // 下
        canvas.drawRect(0, top, left, bottom, maskPaint);      // 左
        canvas.drawRect(right, top, W, bottom, maskPaint);     // 右

        // ---- 黄色取景框线 ----
        // 超小框（400mm，占屏6%）加粗线宽确保可见
        Paint activeFramePaint = framePaint;
        if (ratio < 0.15f) {
            // 创建临时加粗画笔
            Paint thickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            thickPaint.setColor(Color.parseColor("#FFD700"));
            thickPaint.setStyle(Paint.Style.STROKE);
            thickPaint.setStrokeWidth(dpToPx(3.5f));
            thickPaint.setStrokeJoin(Paint.Join.MITER);
            thickPaint.setShadowLayer(dpToPx(8), 0, 0, Color.parseColor("#66FFD700"));
            activeFramePaint = thickPaint;
        }

        RectF frameRect = new RectF(left, top, right, bottom);
        canvas.drawRect(frameRect, activeFramePaint);

        // ---- 框内元素（仅当框足够大时绘制） ----
        boolean canDrawDetails = frameW > dpToPx(60) && frameH > dpToPx(60);

        if (canDrawDetails) {
            // 三分法网格线
            gridPaint.setColor(Color.parseColor("#26FFD700"));
            for (int i = 1; i <= 2; i++) {
                float gx = left + frameW * i / 3f;
                float gy = top + frameH * i / 3f;
                canvas.drawLine(gx, top, gx, bottom, gridPaint);
                canvas.drawLine(left, gy, right, gy, gridPaint);
            }

            // 中心十字
            float cx = left + frameW / 2f;
            float cy = top + frameH / 2f;
            drawCenterCross(canvas, cx, cy, Math.min(dpToPx(14), frameW * 0.1f),
                    Color.parseColor("#33FFD700"));
        }

        // ---- 四角装饰线 ----
        float bracketLen = Math.max(dpToPx(6), Math.min(dpToPx(20), frameW * 0.15f));
        drawCornerBrackets(canvas, left, top, frameW, frameH, bracketLen, activeFramePaint);

        // ---- 焦段标注文字 ----
        if (frameH > dpToPx(55)) {
            // 框足够大，标注在框内左上角
            canvas.drawText(focalLength + "mm", left + dpToPx(8), top + dpToPx(20), labelPaint);
            canvas.drawText(getSceneDesc(focalLength), left + dpToPx(8), top + dpToPx(36), subLabelPaint);
        } else {
            // 框太小，标注在框外上方
            canvas.drawText(focalLength + "mm", left, top - dpToPx(8), labelPaint);
            canvas.drawText(getSceneDesc(focalLength), left, top - dpToPx(24), subLabelPaint);
        }
    }

    // ================================================================
    //  辅助绘制方法
    // ================================================================

    /**
     * 绘制取景框四角装饰线（摄影取景器风格）
     */
    private void drawCornerBrackets(Canvas canvas, float x, float y,
                                     float w, float h, float len, Paint paint) {
        // 左上角
        canvas.drawLine(x, y + len, x, y, paint);
        canvas.drawLine(x, y, x + len, y, paint);
        // 右上角
        canvas.drawLine(x + w - len, y, x + w, y, paint);
        canvas.drawLine(x + w, y, x + w, y + len, paint);
        // 左下角
        canvas.drawLine(x, y + h - len, x, y + h, paint);
        canvas.drawLine(x, y + h, x + len, y + h, paint);
        // 右下角
        canvas.drawLine(x + w - len, y + h, x + w, y + h, paint);
        canvas.drawLine(x + w, y + h - len, x + w, y + h, paint);
    }

    /**
     * 绘制中心十字标记
     */
    private void drawCenterCross(Canvas canvas, float cx, float cy,
                                  float len, int color) {
        crossPaint.setColor(color);
        canvas.drawLine(cx - len, cy, cx + len, cy, crossPaint);
        canvas.drawLine(cx, cy - len, cx, cy + len, crossPaint);
    }

    /**
     * 获取焦段对应的拍摄场景描述
     */
    private String getSceneDesc(int focal) {
        switch (focal) {
            case 9:   return "极超广角 · 星轨/银河全景";
            case 11:  return "超超广角 · 星空全景";
            case 12:  return "极广角 · 建筑全景";
            case 14:  return "超广角 · 大场景风光";
            case 16:  return "超广角 · 星空/建筑";
            case 24:  return "广角 · 风光全景";
            case 35:  return "标准广角 · 人文风光";
            case 70:  return "中长焦 · 山峰细节";
            case 200: return "长焦 · 远景压缩";
            case 400: return "超长焦 · 月亮/野生动物";
            default:  return "";
        }
    }

    /**
     * dp 转 px
     */
    private float dpToPx(float dp) {
        return dp * density;
    }
}
