package com.company.kiosk;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImageViewerActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_viewer);

        ImageView imageView = findViewById(R.id.fullImage);
        Button close = findViewById(R.id.btnImageClose);
        close.setOnClickListener(v -> finish());

        Uri uri = getIntent().getData();
        if (uri == null) {
            finish();
            return;
        }

        executor.execute(() -> {
            Bitmap bitmap = decodeSampled(uri, 2048, 2048);
            mainHandler.post(() -> {
                if (bitmap != null && !isFinishing()) {
                    imageView.setImageBitmap(bitmap);
                } else if (!isFinishing()) {
                    Toast.makeText(this, "Image open nahi hui", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private Bitmap decodeSampled(Uri uri, int maxWidth, int maxHeight) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), uri);
                return ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
                    int width = info.getSize().getWidth();
                    int height = info.getSize().getHeight();
                    float scale = Math.min(1f, Math.min(
                            maxWidth / (float) Math.max(1, width),
                            maxHeight / (float) Math.max(1, height)
                    ));
                    decoder.setTargetSize(
                            Math.max(1, Math.round(width * scale)),
                            Math.max(1, Math.round(height * scale))
                    );
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                });
            }

            try (ParcelFileDescriptor parcel = getContentResolver().openFileDescriptor(uri, "r")) {
                if (parcel == null) {
                    return null;
                }
                FileDescriptor descriptor = parcel.getFileDescriptor();
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFileDescriptor(descriptor, null, bounds);
                int sample = 1;
                while (bounds.outWidth / sample > maxWidth || bounds.outHeight / sample > maxHeight) {
                    sample *= 2;
                }
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = Math.max(1, sample);
                return BitmapFactory.decodeFileDescriptor(descriptor, null, options);
            }
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
