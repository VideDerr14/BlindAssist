package com.example.blindassist;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.util.Log;
import org.tensorflow.lite.Interpreter;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ObjectDetectorHelper {

    private static final String TAG = "ObjectDetectorHelper";
    private static final String MODEL_NAME = "model.tflite";
    private static final int INPUT_SIZE = 300;
    private static final int MAX_RESULT = 10;
    private static final float MIN_CONF = 0.6f;

    private final Context context;
    private Interpreter interpreter;
    private boolean isInitialized = false;

    private final String[] LABELS = {
            "person", "bicycle", "car", "motorcycle", "airplane",
            "bus", "train", "truck", "boat", "traffic light",
            "fire hydrant", "stop sign", "parking meter", "bench", "bird",
            "cat", "dog", "horse", "sheep", "cow",
            "elephant", "bear", "zebra", "giraffe", "backpack",
            "umbrella", "handbag", "tie", "suitcase", "frisbee",
            "skis", "snowboard", "sports ball", "kite", "baseball bat",
            "baseball glove", "skateboard", "surfboard", "tennis racket", "bottle",
            "wine glass", "cup", "fork", "knife", "spoon",
            "bowl", "banana", "apple", "sandwich", "orange",
            "broccoli", "carrot", "hot dog", "pizza", "donut",
            "cake", "chair", "couch", "potted plant", "bed",
            "dining table", "toilet", "tv", "laptop", "mouse",
            "remote", "keyboard", "cell phone", "microwave", "oven",
            "toaster", "sink", "refrigerator", "book", "clock",
            "vase", "scissors", "teddy bear", "hair drier", "toothbrush"
    };

    public ObjectDetectorHelper(Context context) {
        this.context = context;
    }

    public boolean initialize() {
        try {
            MappedByteBuffer model = loadModel();
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4);
            interpreter = new Interpreter(model, options);
            isInitialized = true;
            Log.d(TAG, "Model OK");
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error: " + e.getMessage());
            return false;
        }
    }

    private MappedByteBuffer loadModel() throws IOException {
        AssetFileDescriptor fd = context.getAssets().openFd(MODEL_NAME);
        FileInputStream fis = new FileInputStream(fd.getFileDescriptor());
        FileChannel fc = fis.getChannel();
        return fc.map(FileChannel.MapMode.READ_ONLY,
                fd.getStartOffset(), fd.getDeclaredLength());
    }

    private String getDistance(RectF box) {
        float area = (box.right - box.left) * (box.bottom - box.top);
        float screenArea = INPUT_SIZE * INPUT_SIZE;
        float ratio = area / screenArea;

        if (ratio > 0.4f)       return "SANGAT DEKAT";
        else if (ratio > 0.2f)  return "DEKAT";
        else if (ratio > 0.08f) return "SEDANG";
        else if (ratio > 0.02f) return "JAUH";
        else                    return "SANGAT JAUH";
    }

    private String getDirection(RectF box) {
        float centerX = (box.left + box.right) / 2f;
        float centerY = (box.top + box.bottom) / 2f;
        float third = INPUT_SIZE / 3f;

        String horizontal;
        if (centerX < third)          horizontal = "KIRI";
        else if (centerX > third * 2) horizontal = "KANAN";
        else                          horizontal = "TENGAH";

        String vertical;
        if (centerY < third)          vertical = "ATAS";
        else if (centerY > third * 2) vertical = "BAWAH";
        else                          vertical = "";

        if (vertical.isEmpty()) return horizontal;
        return vertical + "-" + horizontal;
    }

    public String getRecommendation(List<OverlayView.DetectionResult> results) {
        if (results == null || results.isEmpty()) {
            return "JALAN BEBAS";
        }

        OverlayView.DetectionResult top = results.get(0);

        int leftCount   = 0;
        int rightCount  = 0;
        int centerCount = 0;

        for (OverlayView.DetectionResult r : results) {
            float centerX = (r.boundingBox.left + r.boundingBox.right) / 2f;
            float third = INPUT_SIZE / 3f;

            if (centerX < third)          leftCount++;
            else if (centerX > third * 2) rightCount++;
            else                          centerCount++;
        }

        if (top.distance.equals("SANGAT DEKAT") ||
                top.distance.equals("DEKAT")) {

            String dir = top.direction;

            if (dir.contains("TENGAH")) {
                if (leftCount <= rightCount) {
                    return "BELOK KIRI";
                } else {
                    return "BELOK KANAN";
                }
            }

            if (dir.contains("KIRI") && !dir.contains("KANAN")) {
                return "BELOK KANAN";
            }

            if (dir.contains("KANAN") && !dir.contains("KIRI")) {
                return "BELOK KIRI";
            }

            if (dir.contains("ATAS")) {
                return "HATI-HATI TUNDUK";
            }

            if (dir.contains("BAWAH")) {
                return "ANGKAT KAKI";
            }

            return "BERHENTI";
        }

        if (top.distance.equals("SEDANG")) {
            String dir = top.direction;
            if (dir.contains("TENGAH")) {
                if (leftCount <= rightCount) {
                    return "SIAP-SIAP BELOK KIRI";
                } else {
                    return "SIAP-SIAP BELOK KANAN";
                }
            }
            return "HATI-HATI TERUS JALAN";
        }

        return "JALAN BEBAS";
    }

    public List<OverlayView.DetectionResult> detect(Bitmap bitmap) {
        List<OverlayView.DetectionResult> list = new ArrayList<>();
        if (!isInitialized || interpreter == null || bitmap == null) {
            return list;
        }
        try {
            Bitmap resized = Bitmap.createScaledBitmap(
                    bitmap, INPUT_SIZE, INPUT_SIZE, true);

            ByteBuffer input = ByteBuffer.allocateDirect(
                    INPUT_SIZE * INPUT_SIZE * 3);
            input.order(ByteOrder.nativeOrder());

            int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
            resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0,
                    INPUT_SIZE, INPUT_SIZE);

            for (int pixel : pixels) {
                input.put((byte)((pixel >> 16) & 0xFF));
                input.put((byte)((pixel >> 8) & 0xFF));
                input.put((byte)(pixel & 0xFF));
            }

            float[][][] outputLocations = new float[1][MAX_RESULT][4];
            float[][] outputClasses    = new float[1][MAX_RESULT];
            float[][] outputScores     = new float[1][MAX_RESULT];
            float[]   numDetections    = new float[1];

            Map<Integer, Object> outputs = new HashMap<>();
            outputs.put(0, outputLocations);
            outputs.put(1, outputClasses);
            outputs.put(2, outputScores);
            outputs.put(3, numDetections);

            interpreter.runForMultipleInputsOutputs(
                    new Object[]{input}, outputs);

            int count = Math.min((int) numDetections[0], MAX_RESULT);
            for (int i = 0; i < count; i++) {
                float score = outputScores[0][i];
                if (score < MIN_CONF) continue;

                int classId = (int) outputClasses[0][i];
                String label = "objek";
                if (classId >= 0 && classId < LABELS.length) {
                    label = LABELS[classId];
                }

                float top    = outputLocations[0][i][0] * INPUT_SIZE;
                float left   = outputLocations[0][i][1] * INPUT_SIZE;
                float bottom = outputLocations[0][i][2] * INPUT_SIZE;
                float right  = outputLocations[0][i][3] * INPUT_SIZE;

                RectF box = new RectF(left, top, right, bottom);
                String distance  = getDistance(box);
                String direction = getDirection(box);

                list.add(new OverlayView.DetectionResult(
                        label, score, box, distance, direction));
            }
        } catch (Exception e) {
            Log.e(TAG, "Detect error: " + e.getMessage());
        }
        return list;
    }

    public void close() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
        }
        isInitialized = false;
    }
}