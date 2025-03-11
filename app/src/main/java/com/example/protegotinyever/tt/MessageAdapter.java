package com.example.protegotinyever.tt;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.protegotinyever.R;
import com.example.protegotinyever.mode.MessageModel;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ui.PlayerView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import android.content.ContentUris;
import android.database.Cursor;
import android.os.Build;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {
    private final List<MessageModel> messageList;
    private final String currentUser;
    private final Context context;
    private final OnFileClickListener fileClickListener;
    private SimpleExoPlayer exoPlayer;

    public MessageAdapter(List<MessageModel> messageList, String currentUser, Context context, OnFileClickListener fileClickListener) {
        this.messageList = messageList;
        this.currentUser = currentUser;
        this.context = context;
        this.fileClickListener = fileClickListener;
        initializePlayer();
    }

    private void initializePlayer() {
        exoPlayer = new SimpleExoPlayer.Builder(context).build();
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
    }

    @Override
    public int getItemViewType(int position) {
        String sender = messageList.get(position).getSender();
        return (sender != null && sender.equals(currentUser)) ? 1 : 0;
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(
                (viewType == 1) ? R.layout.message_sent : R.layout.message_received, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        MessageModel message = messageList.get(position);
        String messageText = message.getText();

        // Reset visibility
        holder.messageText.setVisibility(View.VISIBLE);
        holder.previewContainer.setVisibility(View.GONE);
        holder.messageImage.setVisibility(View.GONE);
        holder.videoPreviewContainer.setVisibility(View.GONE);
        holder.filePreviewContainer.setVisibility(View.GONE);

        if (messageText.startsWith("Received file:") || messageText.startsWith("Sent file:")) {
            String[] parts = messageText.split(" at ");
            String fileName = parts[0].replace("Received file: ", "").replace("Sent file: ", "").trim();
            String filePath = parts.length > 1 ? parts[1].trim() : null;
            
            android.util.Log.d("MessageAdapter", "Processing file message: " + fileName);
            android.util.Log.d("MessageAdapter", "Original file path: " + filePath);
            
            if (filePath != null) {
                // Try to get the file using MediaStore
                Uri fileUri = getFileUri(filePath);
                android.util.Log.d("MessageAdapter", "File URI: " + fileUri);
                
                if (fileUri != null) {
                    String mimeType = getMimeTypeFromUri(fileUri);
                    android.util.Log.d("MessageAdapter", "MIME type: " + mimeType);
                    
                    holder.previewContainer.setVisibility(View.VISIBLE);
                    holder.messageText.setVisibility(View.GONE);
                    
                    if (mimeType != null && mimeType.startsWith("image/")) {
                        setupImagePreview(holder, fileUri);
                    } else if (mimeType != null && mimeType.startsWith("video/")) {
                        setupVideoPreview(holder, fileUri);
                    } else {
                        setupGenericFilePreview(holder, fileUri, fileName);
                    }
                    
                    // Set click listener for the preview container
                    holder.previewContainer.setOnClickListener(v -> fileClickListener.onFileClick(filePath));
                } else {
                    showFallbackText(holder, fileName);
                }
            } else {
                showFallbackText(holder, fileName);
            }
        } else {
            holder.messageText.setText(messageText);
            holder.messageText.setTextColor(holder.itemView.getResources().getColor(
                getItemViewType(position) == 1 ? android.R.color.white : android.R.color.black));
        }

        // Set timestamp
        long timestamp = message.getTimestamp() > 0 ? message.getTimestamp() : System.currentTimeMillis();
        String time = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date(timestamp));
        holder.messageTime.setText(time);
    }

    private Uri getFileUri(String filePath) {
        try {
            // First try direct file access for backward compatibility
            File file = new File(filePath);
            if (!file.exists() && filePath.startsWith("/")) {
                // Try without leading slash
                file = new File(filePath.substring(1));
            }
            
            if (file.exists() && file.canRead()) {
                android.util.Log.d("MessageAdapter", "File found using direct access");
                return Uri.fromFile(file);
            }

            // Try with external storage path
            File externalDir = android.os.Environment.getExternalStorageDirectory();
            file = new File(externalDir, filePath.startsWith("/") ? filePath.substring(1) : filePath);
            if (file.exists() && file.canRead()) {
                android.util.Log.d("MessageAdapter", "File found in external storage");
                return Uri.fromFile(file);
            }

            // Try with absolute path
            file = new File("/" + filePath);
            if (file.exists() && file.canRead()) {
                android.util.Log.d("MessageAdapter", "File found with absolute path");
                return Uri.fromFile(file);
            }

            // Try with content resolver
            String fileName = filePath.substring(filePath.lastIndexOf('/') + 1);
            String[] projection = {MediaStore.Files.FileColumns._ID};
            String selection = MediaStore.Files.FileColumns.DISPLAY_NAME + "=?";
            String[] selectionArgs = {fileName};

            // Try in Downloads
            Uri downloadsUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            try (Cursor cursor = context.getContentResolver().query(
                    downloadsUri,
                    projection,
                    selection,
                    selectionArgs,
                    null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    long id = cursor.getLong(0);
                    android.util.Log.d("MessageAdapter", "File found in Downloads MediaStore");
                    return ContentUris.withAppendedId(downloadsUri, id);
                }
            }

            // Try in MediaStore Files
            Uri filesUri = MediaStore.Files.getContentUri("external");
            try (Cursor cursor = context.getContentResolver().query(
                    filesUri,
                    projection,
                    selection,
                    selectionArgs,
                    null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    long id = cursor.getLong(0);
                    android.util.Log.d("MessageAdapter", "File found in Files MediaStore");
                    return ContentUris.withAppendedId(filesUri, id);
                }
            }

            // Try using DocumentFile
            File baseDir = new File(filePath).getParentFile();
            if (baseDir != null && baseDir.exists()) {
                DocumentFile docFile = DocumentFile.fromFile(baseDir);
                if (docFile != null) {
                    DocumentFile[] files = docFile.listFiles();
                    for (DocumentFile doc : files) {
                        if (doc.getName() != null && doc.getName().equals(fileName)) {
                            android.util.Log.d("MessageAdapter", "File found using DocumentFile");
                            return doc.getUri();
                        }
                    }
                }
            }

            android.util.Log.d("MessageAdapter", "File not found in any location");
            return null;

        } catch (Exception e) {
            android.util.Log.e("MessageAdapter", "Error getting file URI", e);
            return null;
        }
    }

    private String getMimeTypeFromUri(Uri uri) {
        if (uri.getScheme() != null && uri.getScheme().equals("file")) {
            // For file:// URIs, determine MIME type from file extension
            String extension = getFileExtension(uri.getPath());
            if (extension != null) {
                String mimeType = android.webkit.MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(extension.toLowerCase());
                android.util.Log.d("MessageAdapter", "Determined MIME type from extension: " + mimeType);
                return mimeType;
            }
            return null;
        } else {
            // For content:// URIs, use ContentResolver
            try {
                String mimeType = context.getContentResolver().getType(uri);
                android.util.Log.d("MessageAdapter", "Got MIME type from ContentResolver: " + mimeType);
                return mimeType;
            } catch (Exception e) {
                android.util.Log.e("MessageAdapter", "Error getting MIME type from ContentResolver", e);
                return null;
            }
        }
    }

    private String getFileExtension(String path) {
        if (path == null) return null;
        
        try {
            String fileName = path.substring(path.lastIndexOf('/') + 1);
            int lastDot = fileName.lastIndexOf('.');
            if (lastDot >= 0) {
                return fileName.substring(lastDot + 1);
            }
        } catch (Exception e) {
            android.util.Log.e("MessageAdapter", "Error getting file extension", e);
        }
        return null;
    }

    private void setupImagePreview(MessageViewHolder holder, Uri uri) {
        android.util.Log.d("MessageAdapter", "Setting up image preview for: " + uri);
        
        holder.messageImage.setVisibility(View.VISIBLE);
        holder.videoPreviewContainer.setVisibility(View.GONE);
        holder.filePreviewContainer.setVisibility(View.GONE);
        
        try {
            // Try loading with file path first
            File file = new File(uri.getPath());
            if (file.exists() && file.canRead()) {
                Glide.with(context)
                    .load(file)
                    .into(holder.messageImage);
            } else {
                // Fall back to URI
                Glide.with(context)
                    .load(uri)
                    .into(holder.messageImage);
            }
            android.util.Log.d("MessageAdapter", "Image loaded with Glide");
        } catch (Exception e) {
            android.util.Log.e("MessageAdapter", "Error loading image", e);
            showFallbackText(holder, uri.getLastPathSegment());
        }
    }

    private void setupVideoPreview(MessageViewHolder holder, Uri uri) {
        android.util.Log.d("MessageAdapter", "Setting up video preview for: " + uri);
        
        holder.videoPreviewContainer.setVisibility(View.VISIBLE);
        holder.messageImage.setVisibility(View.GONE);
        holder.filePreviewContainer.setVisibility(View.GONE);

        try {
            // Use MediaStore to create thumbnail
            Bitmap thumbnail = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                thumbnail = context.getContentResolver().loadThumbnail(
                    uri, new android.util.Size(512, 384), null);
            } else {
                thumbnail = MediaStore.Video.Thumbnails.getThumbnail(
                    context.getContentResolver(), ContentUris.parseId(uri),
                    MediaStore.Video.Thumbnails.MINI_KIND, null);
            }
            
            if (thumbnail != null) {
                holder.videoThumbnail.setImageBitmap(thumbnail);
                android.util.Log.d("MessageAdapter", "Video thumbnail created successfully");
            } else {
                holder.videoThumbnail.setImageResource(R.drawable.ic_file_video);
                android.util.Log.d("MessageAdapter", "Failed to create video thumbnail, using default icon");
            }
        } catch (Exception e) {
            android.util.Log.e("MessageAdapter", "Error creating video thumbnail", e);
            holder.videoThumbnail.setImageResource(R.drawable.ic_file_video);
        }
    }

    private void setupGenericFilePreview(MessageViewHolder holder, Uri uri, String fileName) {
        android.util.Log.d("MessageAdapter", "Setting up generic file preview for: " + fileName);
        
        holder.filePreviewContainer.setVisibility(View.VISIBLE);
        holder.messageImage.setVisibility(View.GONE);
        holder.videoPreviewContainer.setVisibility(View.GONE);

        holder.fileName.setText(fileName);
        
        try {
            if (uri.getScheme() != null && uri.getScheme().equals("file")) {
                // For file:// URIs, get size directly from file
                File file = new File(uri.getPath());
                holder.fileSize.setText(Formatter.formatShortFileSize(context, file.length()));
            } else {
                // For content:// URIs, use ContentResolver
                String[] projection = { MediaStore.Files.FileColumns.SIZE };
                Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    long size = cursor.getLong(0);
                    holder.fileSize.setText(Formatter.formatShortFileSize(context, size));
                    cursor.close();
                }
            }
        } catch (Exception e) {
            android.util.Log.e("MessageAdapter", "Error getting file size", e);
            holder.fileSize.setText("");
        }

        String mimeType = getMimeTypeFromUri(uri);
        int iconResource = getFileIconResource(mimeType != null ? mimeType : "application/octet-stream");
        holder.fileIcon.setImageResource(iconResource);
        
        android.util.Log.d("MessageAdapter", "Generic file preview set up with icon: " + iconResource);
    }

    private void showFallbackText(MessageViewHolder holder, String fileName) {
        android.util.Log.d("MessageAdapter", "Showing fallback text for: " + fileName);
        
        holder.messageText.setVisibility(View.VISIBLE);
        holder.previewContainer.setVisibility(View.GONE);
        holder.messageText.setText(fileName);
        holder.messageText.setTextColor(context.getResources().getColor(android.R.color.holo_blue_light));
    }

    private int getFileIconResource(String mimeType) {
        if (mimeType.startsWith("audio/")) {
            return R.drawable.ic_file_audio;
        } else if (mimeType.startsWith("video/")) {
            return R.drawable.ic_file_video;
        } else if (mimeType.startsWith("image/")) {
            return R.drawable.ic_file_image;
        } else if (mimeType.startsWith("text/") || mimeType.contains("document")) {
            return R.drawable.ic_file_document;
        } else if (mimeType.contains("pdf")) {
            return R.drawable.ic_file_pdf;
        } else if (mimeType.contains("zip") || mimeType.contains("rar") || 
                  mimeType.contains("7z") || mimeType.contains("tar") || 
                  mimeType.contains("gz")) {
            return R.drawable.ic_file_archive;
        } else {
            return R.drawable.ic_file_document;
        }
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    public static class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText, messageTime, fileName, fileSize;
        ImageView messageImage, videoThumbnail, fileIcon;
        FrameLayout previewContainer, videoPreviewContainer;
        LinearLayout filePreviewContainer;
        PlayerView playerView;

        public MessageViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
            messageTime = itemView.findViewById(R.id.messageTime);
            messageImage = itemView.findViewById(R.id.messageImage);
            videoThumbnail = itemView.findViewById(R.id.videoThumbnail);
            fileIcon = itemView.findViewById(R.id.fileIcon);
            fileName = itemView.findViewById(R.id.fileName);
            fileSize = itemView.findViewById(R.id.fileSize);
            previewContainer = itemView.findViewById(R.id.previewContainer);
            videoPreviewContainer = itemView.findViewById(R.id.videoPreviewContainer);
            filePreviewContainer = itemView.findViewById(R.id.filePreviewContainer);
            playerView = itemView.findViewById(R.id.playerView);
        }
    }

    public interface OnFileClickListener {
        void onFileClick(String filePath);
    }

}