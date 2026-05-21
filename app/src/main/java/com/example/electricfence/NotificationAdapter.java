package com.example.electricfence;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    private List<NotificationModel> notifications;

    public NotificationAdapter(List<NotificationModel> notifications) {
        this.notifications = notifications;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NotificationModel model = notifications.get(position);
        
        holder.tvMsg.setText(model.getMessage());
        holder.tvTimeAgo.setText(model.getTimestamp()); // In real app, calculate "5 mins ago"
        
        // Setup Type Tag & Colors
        String type = model.getType() != null ? model.getType().toUpperCase() : "INFO";
        switch (type) {
            case "ERROR":
            case "BAHAYA":
                holder.tvType.setText("Bahaya");
                holder.tvType.setBackgroundResource(R.drawable.badge_type_error);
                holder.ivIcon.setImageResource(android.R.drawable.ic_dialog_alert);
                holder.ivIcon.setColorFilter(ContextCompat.getColor(holder.itemView.getContext(), R.color.neon_red));
                holder.tvStatusText.setText("Belum terselesaikan");
                holder.tvStatusText.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.neon_red));
                holder.ivStatusIcon.setImageResource(android.R.drawable.ic_dialog_alert);
                holder.ivStatusIcon.setColorFilter(ContextCompat.getColor(holder.itemView.getContext(), R.color.neon_red));
                break;
            case "WARNING":
            case "PERINGATAN":
                holder.tvType.setText("Peringatan");
                holder.tvType.setBackgroundResource(R.drawable.badge_type_warning);
                holder.ivIcon.setImageResource(android.R.drawable.ic_lock_idle_lock);
                holder.ivIcon.setColorFilter(ContextCompat.getColor(holder.itemView.getContext(), R.color.neon_yellow));
                holder.tvStatusText.setText("Terselesaikan");
                holder.tvStatusText.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.status_green));
                holder.ivStatusIcon.setImageResource(android.R.drawable.checkbox_on_background);
                holder.ivStatusIcon.setColorFilter(ContextCompat.getColor(holder.itemView.getContext(), R.color.status_green));
                break;
            default: // INFO
                holder.tvType.setText("Info");
                holder.tvType.setBackgroundResource(R.drawable.badge_type_info);
                holder.ivIcon.setImageResource(android.R.drawable.ic_lock_power_off);
                holder.ivIcon.setColorFilter(ContextCompat.getColor(holder.itemView.getContext(), R.color.status_green));
                holder.tvStatusText.setText("Terselesaikan");
                holder.tvStatusText.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.status_green));
                holder.ivStatusIcon.setImageResource(android.R.drawable.checkbox_on_background);
                holder.ivStatusIcon.setColorFilter(ContextCompat.getColor(holder.itemView.getContext(), R.color.status_green));
                break;
        }
    }

    @Override
    public int getItemCount() {
        return notifications.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvType, tvTimeAgo, tvMsg, tvStatusText;
        ImageView ivIcon, ivStatusIcon;
        LinearLayout llStatus;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvType = itemView.findViewById(R.id.tv_notif_type);
            tvTimeAgo = itemView.findViewById(R.id.tv_notif_time_ago);
            tvMsg = itemView.findViewById(R.id.tv_notif_msg);
            tvStatusText = itemView.findViewById(R.id.tv_status_text);
            ivIcon = itemView.findViewById(R.id.iv_notif_icon);
            ivStatusIcon = itemView.findViewById(R.id.iv_status_icon);
            llStatus = itemView.findViewById(R.id.ll_status);
        }
    }
}