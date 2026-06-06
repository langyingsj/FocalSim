package com.photography.focalsim;

import android.Manifest;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * =============================================
 * MainActivity - 风光摄影焦段模拟助手 (v2)
 * =============================================
 *
 * 完整焦段：9/11/12/14/16/24/35/70/200/400mm
 *
 * 功能概述：
 * 1. 启动后自动申请相机权限
 * 2. 使用 CameraX 全屏显示后置摄像头实时画面
 * 3. 底部10个焦段按钮，可横向滚动
 * 4. 右上角"加群"入口，弹出二维码对话框
 * 5. OverlayView 实时绘制取景框
 */
public class MainActivity extends AppCompatActivity {

    // ============ 常量 ============
    private static final int REQUEST_CODE_CAMERA = 1001;

    // ============ 相机相关 ============
    private PreviewView previewView;
    private ProcessCameraProvider cameraProvider;
    private Camera camera;

    // ============ UI 组件 ============
    private OverlayView overlayView;
    private TextView tvFocalValue;
    private TextView tvFovInfo;
    private MaterialButton[] focalButtons;

    // ============ 焦段数据映射 ============
    /** 全画幅对角线视场角（近似值） */
    private static final Map<Integer, String> FOV_MAP = new HashMap<>();
    /** 焦段对应的拍摄场景描述 */
    private static final Map<Integer, String> SCENE_MAP = new HashMap<>();

    static {
        FOV_MAP.put(9, "130°");
        FOV_MAP.put(11, "122°");
        FOV_MAP.put(12, "118°");
        FOV_MAP.put(14, "112°");
        FOV_MAP.put(16, "107°");
        FOV_MAP.put(24, "84°");
        FOV_MAP.put(35, "63°");
        FOV_MAP.put(70, "34°");
        FOV_MAP.put(200, "12°");
        FOV_MAP.put(400, "6°");

        SCENE_MAP.put(9, "极超广角 · 星轨/银河全景");
        SCENE_MAP.put(11, "超超广角 · 星空全景");
        SCENE_MAP.put(12, "极广角 · 建筑全景");
        SCENE_MAP.put(14, "超广角 · 大场景风光");
        SCENE_MAP.put(16, "超广角 · 星空/建筑");
        SCENE_MAP.put(24, "广角 · 风光全景");
        SCENE_MAP.put(35, "标准广角 · 人文风光");
        SCENE_MAP.put(70, "中长焦 · 山峰细节");
        SCENE_MAP.put(200, "长焦 · 远景压缩");
        SCENE_MAP.put(400, "超长焦 · 月亮/野生动物");
    }

    /** 当前选中的焦段（默认24mm） */
    private int currentFocal = 24;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupButtons();
        setupGroupButton();

        if (hasCameraPermission()) {
            startCamera();
        } else {
            requestCameraPermission();
        }
    }

    // ================================================================
    //  视图初始化
    // ================================================================

    private void initViews() {
        previewView = findViewById(R.id.previewView);
        overlayView = findViewById(R.id.overlayView);
        tvFocalValue = findViewById(R.id.tvFocalValue);
        tvFovInfo = findViewById(R.id.tvFovInfo);

        // 10个焦段按钮，顺序：9/11/12/14/16/24/35/70/200/400
        focalButtons = new MaterialButton[]{
                findViewById(R.id.btn9mm),
                findViewById(R.id.btn11mm),
                findViewById(R.id.btn12mm),
                findViewById(R.id.btn14mm),
                findViewById(R.id.btn16mm),
                findViewById(R.id.btn24mm),
                findViewById(R.id.btn35mm),
                findViewById(R.id.btn70mm),
                findViewById(R.id.btn200mm),
                findViewById(R.id.btn400mm)
        };

        updateButtonSelection(24);
    }

    /**
     * 设置10个焦段按钮的点击监听
     */
    private void setupButtons() {
        for (MaterialButton btn : focalButtons) {
            btn.setOnClickListener(v -> {
                int focal = Integer.parseInt(v.getTag().toString());
                switchFocal(focal);
            });
        }
    }

    /**
     * 设置"加群"按钮点击事件
     * 点击后弹出二维码对话框
     */
    private void setupGroupButton() {
        View btnGroup = findViewById(R.id.btnGroup);
        btnGroup.setOnClickListener(v -> showQrCodeDialog());
    }

    // ================================================================
    //  焦段切换逻辑
    // ================================================================

    /**
     * 切换到指定焦段
     */
    private void switchFocal(int focal) {
        if (focal == currentFocal) return;

        currentFocal = focal;

        // 1. 更新覆盖层
        overlayView.setFocalLength(focal);

        // 2. 更新顶部信息栏
        tvFocalValue.setText(focal + "mm");
        String fovText = "对角线视场角 " + FOV_MAP.get(focal)
                + "\n" + SCENE_MAP.get(focal);
        tvFovInfo.setText(fovText);

        // 3. 更新按钮选中状态
        updateButtonSelection(focal);
    }

    /**
     * 更新按钮选中状态
     */
    private void updateButtonSelection(int activeFocal) {
        for (MaterialButton btn : focalButtons) {
            int btnFocal = Integer.parseInt(btn.getTag().toString());
            btn.setSelected(btnFocal == activeFocal);
        }
    }

    // ================================================================
    //  二维码弹窗
    // ================================================================

    /**
     * 显示微信群二维码对话框
     * 点击"加群"按钮时调用
     */
    private void showQrCodeDialog() {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_qrcode);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        // 关闭按钮
        MaterialButton btnClose = dialog.findViewById(R.id.btnCloseQr);
        btnClose.setOnClickListener(v -> dialog.dismiss());

        // 点击背景也可关闭
        dialog.findViewById(R.id.btnCloseQr).getRootView()
                .setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    // ================================================================
    //  相机权限处理
    // ================================================================

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.CAMERA},
                REQUEST_CODE_CAMERA
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                            @NonNull String[] permissions,
                                            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "需要相机权限才能使用本应用", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    // ================================================================
    //  CameraX 相机初始化
    // ================================================================

    /**
     * 启动 CameraX 相机预览
     */
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, "相机初始化失败: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    /**
     * 绑定 CameraX 的预览用例
     */
    private void bindCameraUseCases() {
        cameraProvider.unbindAll();

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

        camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cameraProvider != null) {
            bindCameraUseCases();
        }
    }
}
