package com.example.blindassist;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "BlindAssist";
    private static final int CAMERA_PERMISSION = 100;

    private PreviewView previewView;
    private OverlayView overlayView;
    private TextView tvStatus, tvFps, tvDetectedObject;
    private TextView tvDistance, tvObjectCount, tvWarning, tvDirection;
    private TextView tvRecommendation;
    private ProgressBar progressConfidence;
    private Button btnToggleDetection, btnToggleSound, btnToggleHaptic;

    private ObjectDetectorHelper detectorHelper;
    private ExecutorService cameraExecutor;
    private TextToSpeech tts;
    private Vibrator vibrator;

    private boolean isDetecting = true;
    private boolean isSoundOn = true;
    private boolean isHapticOn = true;
    private boolean ttsReady = false;

    private String lastSpoken = "";
    private long lastSpeakTime = 0;
    private long lastHapticTime = 0;
    private long lastFrameTime = 0;
    private float fps = 0f;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initVibrator();
        initTTS();
        initDetector();
        initButtons();

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
        }
    }

    private void initViews() {
        previewView        = findViewById(R.id.previewView);
        overlayView        = findViewById(R.id.overlayView);
        tvStatus           = findViewById(R.id.tvStatus);
        tvFps              = findViewById(R.id.tvFps);
        tvDetectedObject   = findViewById(R.id.tvDetectedObject);
        tvDistance         = findViewById(R.id.tvDistance);
        tvObjectCount      = findViewById(R.id.tvObjectCount);
        tvWarning          = findViewById(R.id.tvWarning);
        tvDirection        = findViewById(R.id.tvDirection);
        tvRecommendation   = findViewById(R.id.tvRecommendation);
        progressConfidence = findViewById(R.id.progressConfidence);
        btnToggleDetection = findViewById(R.id.btnToggleDetection);
        btnToggleSound     = findViewById(R.id.btnToggleSound);
        btnToggleHaptic    = findViewById(R.id.btnToggleHaptic);
    }

    private void initVibrator() {
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
    }

    private void initTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("id", "ID"));
                tts.setSpeechRate(0.9f);
                ttsReady = true;
            }
        });
    }

    private void initDetector() {
        cameraExecutor = Executors.newSingleThreadExecutor();
        detectorHelper = new ObjectDetectorHelper(this);
        boolean ok = detectorHelper.initialize();
        tvStatus.setText(ok ? "Model siap ✓" : "Model gagal ✗");
    }

    private void initButtons() {
        btnToggleDetection.setOnClickListener(v -> {
            isDetecting = !isDetecting;
            btnToggleDetection.setText(isDetecting ? "⏸ Pause" : "▶ Lanjut");
            if (!isDetecting) {
                overlayView.clearResults();
                tvDetectedObject.setText("-");
                tvWarning.setVisibility(View.GONE);
                tvDirection.setText("");
                tvRecommendation.setText("👉 JALAN BEBAS");
            }
        });

        btnToggleSound.setOnClickListener(v -> {
            isSoundOn = !isSoundOn;
            btnToggleSound.setText(isSoundOn ? "🔊 Suara" : "🔇 Bisu");
            if (!isSoundOn && tts != null) tts.stop();
        });

        btnToggleHaptic.setOnClickListener(v -> {
            isHapticOn = !isHapticOn;
            btnToggleHaptic.setText(isHapticOn ? "📳 Haptic" : "📴 Haptic");
        });
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(
                                ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                analysis.setAnalyzer(cameraExecutor, imageProxy -> {
                    if (isDetecting) {
                        processFrame(imageProxy);
                    } else {
                        imageProxy.close();
                    }
                });

                CameraSelector selector = CameraSelector.DEFAULT_BACK_CAMERA;
                provider.unbindAll();
                provider.bindToLifecycle(
                        this, selector, preview, analysis);

            } catch (Exception e) {
                Log.e(TAG, "Camera error: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void processFrame(ImageProxy imageProxy) {
        try {
            Bitmap bitmap = imageProxy.toBitmap();
            int w = imageProxy.getWidth();
            int h = imageProxy.getHeight();

            long now = System.currentTimeMillis();
            if (lastFrameTime > 0) {
                fps = 1000f / (now - lastFrameTime);
            }
            lastFrameTime = now;

            List<OverlayView.DetectionResult> results =
                    detectorHelper.detect(bitmap);

            mainHandler.post(() -> updateUI(results, w, h));

        } catch (Exception e) {
            Log.e(TAG, "Frame error: " + e.getMessage());
        } finally {
            imageProxy.close();
        }
    }

    private void updateUI(List<OverlayView.DetectionResult> results,
                          int w, int h) {
        overlayView.setResults(results, w, h);
        tvFps.setText(String.format(Locale.US, "%.0f FPS", fps));
        tvObjectCount.setText(results.size() + " objek");

        String recommendation = detectorHelper.getRecommendation(results);

        if (!results.isEmpty()) {
            OverlayView.DetectionResult top = results.get(0);
            int conf = (int)(top.confidence * 100);

            tvDetectedObject.setText(top.direction);
            tvDistance.setText("Jarak: " + top.distance);
            tvDirection.setText(getDirectionArrow(top.direction));
            progressConfidence.setProgress(conf);
            tvStatus.setText("Mendeteksi...");
            tvRecommendation.setText("👉 " + recommendation);

            boolean isClose = top.distance.equals("SANGAT DEKAT")
                    || top.distance.equals("DEKAT");

            if (isClose) {
                tvWarning.setVisibility(View.VISIBLE);
                tvWarning.setText("⚠ PERINGATAN: OBJEK " +
                        top.distance + "!");
                triggerHaptic(top.distance);
            } else {
                tvWarning.setVisibility(View.GONE);
            }

            long now = System.currentTimeMillis();
            String toSpeak = top.direction + ". " +
                    top.distance + ". " + recommendation;
            long delay = isClose ? 2000 : 4000;

            if (isSoundOn && ttsReady &&
                    (!toSpeak.equals(lastSpoken) ||
                            (now - lastSpeakTime) > delay)) {
                tts.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null, null);
                lastSpoken = toSpeak;
                lastSpeakTime = now;
            }

        } else {
            tvDetectedObject.setText("-");
            tvDistance.setText("Jarak: -");
            tvDirection.setText("");
            progressConfidence.setProgress(0);
            tvWarning.setVisibility(View.GONE);
            tvStatus.setText("Mencari...");
            tvRecommendation.setText("👉 JALAN BEBAS");
        }
    }

    private String getDirectionArrow(String direction) {
        switch (direction) {
            case "KIRI":         return "←";
            case "KANAN":        return "→";
            case "TENGAH":       return "↑";
            case "ATAS-KIRI":    return "↖";
            case "ATAS-KANAN":   return "↗";
            case "BAWAH-KIRI":   return "↙";
            case "BAWAH-KANAN":  return "↘";
            case "ATAS-TENGAH":  return "↑";
            case "BAWAH-TENGAH": return "↓";
            default:             return "•";
        }
    }

    private void triggerHaptic(String distance) {
        if (!isHapticOn) return;

        long now = System.currentTimeMillis();
        long interval = distance.equals("SANGAT DEKAT") ? 500 : 1500;
        if (now - lastHapticTime < interval) return;
        lastHapticTime = now;

        try {
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (distance.equals("SANGAT DEKAT")) {
                        long[] pattern = {0, 200, 100, 200};
                        vibrator.vibrate(
                                VibrationEffect.createWaveform(pattern, -1));
                    } else {
                        vibrator.vibrate(
                                VibrationEffect.createOneShot(300,
                                        VibrationEffect.DEFAULT_AMPLITUDE));
                    }
                } else {
                    vibrator.vibrate(300);
                }
            } else {
                overlayView.performHapticFeedback(
                        android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            }
        } catch (Exception e) {
            Log.e(TAG, "Haptic error: " + e.getMessage());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this,
                        "Izin kamera diperlukan!",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (detectorHelper != null) detectorHelper.close();
        if (cameraExecutor != null) cameraExecutor.shutdown();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}