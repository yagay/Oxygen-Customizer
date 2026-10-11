package it.dhd.oxygencustomizer.utils.extension;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** One-shot task with main-thread callbacks and deterministic worker cleanup. */
public abstract class TaskExecutor<Params, Progress, Result> {
    private static final String TAG = "OC-TaskExecutor";
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean mCancelled = new AtomicBoolean(false);
    private final AtomicBoolean mCancellationDelivered = new AtomicBoolean(false);
    private final CountDownLatch preExecuteLatch = new CountDownLatch(1);
    private volatile Future<?> mTask;
    private volatile Status mStatus = Status.PENDING;

    public final Status getStatus() {
        return mStatus;
    }

    @SafeVarargs
    public final synchronized void execute(final Params... params) {
        if (mStatus != Status.PENDING) {
            throw new IllegalStateException("A task may only execute once: " + mStatus);
        }
        mStatus = Status.RUNNING;
        if (mCancelled.get()) {
            finishCancelled();
            mExecutor.shutdown();
            return;
        }
        mTask = mExecutor.submit(() -> {
            try {
                AtomicReference<Throwable> preError = new AtomicReference<>();
                if (!mHandler.post(() -> {
                    try {
                        if (!isCancelled()) onPreExecute();
                    } catch (Throwable failure) {
                        preError.set(failure);
                    } finally {
                        preExecuteLatch.countDown();
                    }
                })) {
                    preExecuteLatch.countDown();
                    mCancelled.set(true);
                }
                preExecuteLatch.await();
                if (preError.get() != null) throw new IllegalStateException("onPreExecute failed", preError.get());
                if (!isCancelled()) {
                    final Result result = doInBackground(params);
                    if (!isCancelled()) {
                        mHandler.post(() -> {
                            if (!isCancelled()) {
                                try {
                                    onPostExecute(result);
                                } catch (Throwable failure) {
                                    Log.e(TAG, "onPostExecute failed", failure);
                                }
                            } else {
                                deliverCancellation();
                            }
                        });
                    }
                }
            } catch (InterruptedException interruption) {
                Thread.currentThread().interrupt();
                mCancelled.set(true);
            } catch (Throwable failure) {
                Log.e(TAG, "Task failed", failure);
                mCancelled.set(true);
            } finally {
                mStatus = Status.FINISHED;
                if (isCancelled()) deliverCancellation();
                mExecutor.shutdown();
            }
        });
        // A one-shot executor must never retain a live idle thread.
        mExecutor.shutdown();
    }

    private void finishCancelled() {
        mStatus = Status.FINISHED;
        deliverCancellation();
    }

    private void deliverCancellation() {
        if (!mCancellationDelivered.compareAndSet(false, true)) return;
        mHandler.post(() -> {
            try {
                onCancelled();
            } catch (Throwable failure) {
                Log.e(TAG, "onCancelled failed", failure);
            }
        });
    }

    protected abstract void onPreExecute();

    @SuppressWarnings({"unchecked", "unsafe"})
    protected abstract Result doInBackground(Params... params);

    @SafeVarargs
    protected final void publishProgress(Progress... values) {
        if (!isCancelled()) {
            mHandler.post(() -> {
                if (!isCancelled()) onProgressUpdate(values);
            });
        }
    }

    @SuppressWarnings({"unchecked", "unsafe"})
    protected void onProgressUpdate(Progress... values) {
    }

    protected abstract void onPostExecute(Result result);

    protected void onCancelled() {
    }

    public final boolean isCancelled() {
        return mCancelled.get();
    }

    public final void cancel(boolean mayInterruptIfRunning) {
        if (mStatus == Status.FINISHED) return;
        mCancelled.set(true);
        // The UI callback may have been removed/skipped: unblock await unconditionally.
        preExecuteLatch.countDown();
        if (mayInterruptIfRunning && mTask != null) mTask.cancel(true);
        if (mStatus == Status.PENDING || (mTask != null && mTask.isCancelled())) {
            finishCancelled();
            mExecutor.shutdownNow();
        }
    }

    public enum Status {
        PENDING,
        RUNNING,
        FINISHED
    }
}
