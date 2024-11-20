package me.prapon.eyeblinkdetection.vision;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PointF;
import android.media.MediaPlayer;
import android.util.Log;

import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.google.android.gms.vision.face.Landmark;

import java.util.HashMap;
import java.util.Map;

import me.prapon.eyeblinkdetection.R;

public class FaceTracker extends Tracker<Face> {
    private static final float EYE_CLOSED_THRESHOLD = 0.5f;

    private GraphicOverlay mOverlay;
    private EyesGraphics mEyesGraphics;
    private Context context;
    private MediaPlayer warningSound;
    private MediaPlayer alertSound;
    private long eyesClosedStartTime = 0;
    private boolean wasEyesClosed = false;

    @SuppressLint("UseSparseArrays")
    private Map<Integer, PointF> mPreviousProportions = new HashMap<>();
    private boolean mPreviousIsLeftOpen = true;
    private boolean mPreviousIsRightOpen = true;

    public FaceTracker(GraphicOverlay overlay, Context context) {
        mOverlay = overlay;
        this.context = context;
        initializeSounds();
    }

    private void initializeSounds() {
        try {
            warningSound = MediaPlayer.create(context, R.raw.warning_sound);
            alertSound = MediaPlayer.create(context, R.raw.alert_sound);
            warningSound.setLooping(true);
            alertSound.setLooping(true);
            Log.d("FaceTracker", "MediaPlayer initialized successfully");
        } catch (Exception e) {
            Log.e("FaceTracker", "Sound initialization error: " + e.getMessage());
        }
    }

    @Override
    public void onNewItem(int id, Face face) {
        mEyesGraphics = new EyesGraphics(mOverlay);
    }

    @Override
    public void onUpdate(FaceDetector.Detections<Face> detectionResults, Face face) {
        mOverlay.add(mEyesGraphics);
        updatePreviousProportions(face);

        PointF leftPosition = getLandmarkPosition(face, Landmark.LEFT_EYE);
        PointF rightPosition = getLandmarkPosition(face, Landmark.RIGHT_EYE);

        float leftOpenScore = face.getIsLeftEyeOpenProbability();
        float rightOpenScore = face.getIsRightEyeOpenProbability();

        if (leftOpenScore != Face.UNCOMPUTED_PROBABILITY && rightOpenScore != Face.UNCOMPUTED_PROBABILITY) {
            boolean isLeftOpen = (leftOpenScore > EYE_CLOSED_THRESHOLD);
            boolean isRightOpen = (rightOpenScore > EYE_CLOSED_THRESHOLD);
            boolean areEyesClosed = !isLeftOpen && !isRightOpen;

            if (areEyesClosed) {
                if (!wasEyesClosed) {
                    eyesClosedStartTime = System.currentTimeMillis();
                    wasEyesClosed = true;
                } else {
                    long eyesClosedDuration = System.currentTimeMillis() - eyesClosedStartTime;
                    Log.d("FaceTracker", "Eyes closed for: " + eyesClosedDuration + "ms");

                    if (eyesClosedDuration >= 8000) {
                        if (warningSound != null && !warningSound.isPlaying()) {
                            stopWarningSound();
                            warningSound.start();
                        }
                    } else if (eyesClosedDuration >= 3000) {
                        if (alertSound != null && !alertSound.isPlaying() &&
                                (warningSound == null || !warningSound.isPlaying())) {
                            alertSound.start();
                        }
                    }
                }
            } else if (wasEyesClosed) {
                wasEyesClosed = false;
                stopSounds();
            }

            mEyesGraphics.updateEyes(leftPosition, isLeftOpen, rightPosition, isRightOpen);
        }
    }

    private void stopWarningSound() {
        if (alertSound != null && alertSound.isPlaying()) {
            alertSound.pause();
            alertSound.seekTo(0);
        }
    }

    private void stopSounds() {
        stopWarningSound();
        if (warningSound != null && warningSound.isPlaying()) {
            warningSound.pause();
            warningSound.seekTo(0);
        }
    }

    @Override
    public void onMissing(FaceDetector.Detections<Face> detectionResults) {
        mOverlay.remove(mEyesGraphics);
    }

    @Override
    public void onDone() {
        mOverlay.remove(mEyesGraphics);
        if (warningSound != null) {
            warningSound.release();
            warningSound = null;
        }
        if (alertSound != null) {
            alertSound.release();
            alertSound = null;
        }
    }

    private void updatePreviousProportions(Face face) {
        for (Landmark landmark : face.getLandmarks()) {
            PointF position = landmark.getPosition();
            float xProp = (position.x - face.getPosition().x) / face.getWidth();
            float yProp = (position.y - face.getPosition().y) / face.getHeight();
            mPreviousProportions.put(landmark.getType(), new PointF(xProp, yProp));
        }
    }

    private PointF getLandmarkPosition(Face face, int landmarkId) {
        for (Landmark landmark : face.getLandmarks()) {
            if (landmark.getType() == landmarkId) {
                return landmark.getPosition();
            }
        }

        PointF prop = mPreviousProportions.get(landmarkId);
        if (prop == null) {
            return null;
        }

        float x = face.getPosition().x + (prop.x * face.getWidth());
        float y = face.getPosition().y + (prop.y * face.getHeight());
        return new PointF(x, y);
    }
}