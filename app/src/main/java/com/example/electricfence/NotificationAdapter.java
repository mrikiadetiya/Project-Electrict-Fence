package com.example.electricfence;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
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
        holder.tvTitle.setText(model.getTitle());
        holder.tvMsg.setText(model.getMessage());
        holder.tvTime.setText(model.getTimestamp());

        int color;
        switch (model.getType()) {
            case "ERROR":
                color = holder.itemView.getContext().getResources().getColor(R.color.neon_red);
                break;
            case "WARNING":
                color = holder.itemView.getContext().getResources().getColor(R.color.neon_blue);
                break;
            default:
                color = holder.itemView.getContext().getResources().getColor(R.color.status_green);
                break;
        }
        holder.indicator.setBackgroundColor(color);
    }

    @Override
    public int getItemCount() {
        return notifications.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvMsg, tvTime;
        View indicator;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_notif_title);
            tvMsg = itemView.findViewById(R.id.tv_notif_msg);
            tvTime = itemView.findViewById(R.id.tv_notif_time);
            indicator = itemView.findViewById(R.id.view_type_indicator);
        }
    }
}