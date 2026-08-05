package com.company.kiosk;

import android.Manifest;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Size;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GalleryActivity extends Activity {
    private static final int REQUEST_MEDIA = 5101;

    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Uri> images = new ArrayList<>();

    private GridView gridView;
    private TextView statusView;
    private ImageAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);

        gridView = findViewById(R.id.galleryGrid);
        statusView = findViewById(R.id.txtGalleryStatus);
        Button home = findViewById(R.id.btnGalleryHome);
        Button refresh = findViewById(R.id.btnGalleryRefresh);

        adapter = new ImageAdapter();
        gridView.setAdapter(adapter);
        gridView.setOnItemClickListener((parent, view, position, id) -> {
            Intent intent = new Intent(this, ImageViewerActivity.class);
            intent.setData(images.get(position));
            startActivity(intent);
        });

        home.setOnClickListener(v -> finish());
        refresh.setOnClickListener(v -> checkPermissionAndLoad());
        checkPermissionAndLoad();
    }

    private void checkPermissionAndLoad() {
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            loadImages();
        } else {
            statusView.setText("Gallery permission required");
            requestPermissions(new String[]{permission}, REQUEST_MEDIA);
        }
    }

    private void loadImages() {
        statusView.setText("Loading photos...");
        executor.execute(() -> {
            List<Uri> loaded = new ArrayList<>();
            String[] projection = new String[]{MediaStore.Images.Media._ID};
            try (Cursor cursor = getContentResolver().query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    MediaStore.Images.Media.DATE_ADDED + " DESC"
            )) {
                if (cursor != null) {
                    int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                    while (cursor.moveToNext() && loaded.size() < 500) {
                        long id = cursor.getLong(idColumn);
                        loaded.add(ContentUris.withAppendedId(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                id
                        ));
                    }
                }
            } catch (RuntimeException ignored) {
            }

            mainHandler.post(() -> {
                images.clear();
                images.addAll(loaded);
                adapter.notifyDataSetChanged();
                statusView.setText(loaded.isEmpty()
                        ? "No photos found"
                        : loaded.size() + " photos");
            });
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_MEDIA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadImages();
            } else {
                statusView.setText("Gallery permission denied");
            }
        }
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private final class ImageAdapter extends BaseAdapter {
        private final int tileSize = (int) (112 * getResources().getDisplayMetrics().density);

        @Override
        public int getCount() {
            return images.size();
        }

        @Override
        public Object getItem(int position) {
            return images.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ImageView imageView;
            if (convertView instanceof ImageView) {
                imageView = (ImageView) convertView;
            } else {
                imageView = new ImageView(GalleryActivity.this);
                imageView.setLayoutParams(new AbsListView.LayoutParams(tileSize, tileSize));
                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                imageView.setBackgroundColor(0xFFE1E6EA);
            }

            Uri uri = images.get(position);
            imageView.setImageDrawable(null);
            imageView.setTag(uri.toString());
            executor.execute(() -> {
                Bitmap thumbnail = loadThumbnail(uri);
                mainHandler.post(() -> {
                    if (uri.toString().equals(imageView.getTag()) && thumbnail != null) {
                        imageView.setImageBitmap(thumbnail);
                    }
                });
            });
            return imageView;
        }

        private Bitmap loadThumbnail(Uri uri) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    return getContentResolver().loadThumbnail(uri, new Size(tileSize, tileSize), null);
                }
                String idText = uri.getLastPathSegment();
                if (idText == null) {
                    return null;
                }
                long id = Long.parseLong(idText);
                return MediaStore.Images.Thumbnails.getThumbnail(
                        getContentResolver(),
                        id,
                        MediaStore.Images.Thumbnails.MINI_KIND,
                        null
                );
            } catch (Exception ignored) {
                return null;
            }
        }
    }
}
