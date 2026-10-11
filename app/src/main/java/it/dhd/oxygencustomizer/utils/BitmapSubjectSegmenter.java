package it.dhd.oxygencustomizer.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import com.google.android.gms.common.moduleinstall.ModuleAvailabilityResponse;
import com.google.android.gms.common.moduleinstall.ModuleInstall;
import com.google.android.gms.common.moduleinstall.ModuleInstallClient;
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.common.MlKit;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions;

import java.nio.FloatBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BitmapSubjectSegmenter {
    private static final ExecutorService MASK_WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "OC-SubjectMaskWorker");
        thread.setDaemon(true);
        return thread;
    });


    private SubjectSegmenter mSegmenter;
    final Context mContext;

    public BitmapSubjectSegmenter(Context context) {
        mContext = context;

        try {
            MlKit.initialize(context);
        } catch (Throwable ignored) {
        }

        try {
            mSegmenter = SubjectSegmentation.getClient(
                    new SubjectSegmenterOptions.Builder()
                            .enableForegroundConfidenceMask()
                            .build());
        } catch (Throwable e) {
            mSegmenter = null;
            return;
        }
        downloadModelIfNeeded();
    }

    public void downloadModelIfNeeded() {

        ModuleInstallClient moduleInstallClient = ModuleInstall.getClient(mContext);

        moduleInstallClient
                .areModulesAvailable(mSegmenter)
                .addOnSuccessListener(
                        response -> {
                            if (!response.areModulesAvailable()) {
                                moduleInstallClient
                                        .installModules(
                                                ModuleInstallRequest.newBuilder()
                                                        .addApi(mSegmenter)
                                                        .build());
                            }
                        });
    }

    public void checkModelAvailability(OnSuccessListener<ModuleAvailabilityResponse> resultListener) {

        if (mSegmenter == null) {
            resultListener.onSuccess(new ModuleAvailabilityResponse(false, 0));
            return;
        }

        ModuleInstallClient moduleInstallClient = ModuleInstall.getClient(mContext);
        moduleInstallClient.areModulesAvailable(mSegmenter).addOnSuccessListener(resultListener).addOnFailureListener(e -> {
            Log.e("BitmapSubjectSegmenter", "checkModelAvailability failed", e);
            resultListener.onSuccess(new ModuleAvailabilityResponse(false, 0));
        });
    }

    public void segmentSubject(Bitmap inputBitmap, SegmentResultListener listener) {
        if (mSegmenter == null || inputBitmap == null || inputBitmap.isRecycled()) {
            listener.onFail();
            return;
        }

        // Do not recycle inputBitmap: the wallpaper renderer may still own it.
        try {
            mSegmenter.process(InputImage.fromBitmap(inputBitmap, 0))
                    .addOnSuccessListener(MASK_WORKER, result -> {
                        Bitmap output = null;
                        try {
                            FloatBuffer mask = result.getForegroundConfidenceMask();
                            int width = inputBitmap.getWidth();
                            int height = inputBitmap.getHeight();
                            if (mask == null || mask.remaining() < (long) width * height) {
                                Log.e("BitmapSubjectSegmenter", "Missing or incomplete subject mask");
                                listener.onFail();
                                return;
                            }
                            output = inputBitmap.copy(Bitmap.Config.ARGB_8888, true);
                            if (output == null) throw new IllegalStateException("Bitmap copy failed");
                            output.setHasAlpha(true);
                            for (int y = 0; y < height; y++) {
                                for (int x = 0; x < width; x++) {
                                    if (mask.get() < .5f) {
                                        output.setPixel(x, y, Color.TRANSPARENT);
                                    }
                                }
                            }
                            Bitmap completed = output;
                            output = null; // Ownership passes to the listener.
                            listener.onSuccess(completed);
                        } catch (Throwable failure) {
                            Log.e("BitmapSubjectSegmenter", "Subject mask processing failed", failure);
                            listener.onFail();
                        } finally {
                            if (output != null && !output.isRecycled()) output.recycle();
                        }
                    })
                    .addOnFailureListener(MASK_WORKER, failure -> {
                        Log.e("BitmapSubjectSegmenter", "Subject segmentation failed", failure);
                        listener.onFail();
                    });
        } catch (Throwable failure) {
            Log.e("BitmapSubjectSegmenter", "Unable to start segmentation", failure);
            listener.onFail();
        }
    }

    public interface SegmentResultListener {
        void onSuccess(Bitmap result);

        void onFail();
    }
}
