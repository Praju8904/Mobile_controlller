//package com.prajwal.myfirstapp;
//
//import android.view.LayoutInflater;
//import android.view.View;
//import android.view.ViewGroup;
//import android.widget.ImageView;
//import android.widget.TextView;
//import androidx.recyclerview.widget.RecyclerView;
//import org.json.JSONObject;
//import java.util.List;
//
//public class NotesAdapter extends RecyclerView.Adapter<NotesAdapter.ViewHolder> {
//
//    private List<JSONObject> items;
//    private OnItemClickListener listener;
//
//    public interface OnItemClickListener { void onItemClick(JSONObject item); }
//
//    public NotesAdapter(List<JSONObject> items, OnItemClickListener listener) {
//        this.items = items;
//        this.listener = listener;
//    }
//
//    @Override
//    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
//        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_note, parent, false);
//        return new ViewHolder(view);
//    }
//
//    @Override
//    public void onBindViewHolder(ViewHolder holder, int position) {
//        JSONObject item = items.get(position);
//        holder.title.setText(item.optString("name"));
//
//        if (item.optString("type").equals("folder")) {
//            holder.icon.setImageResource(android.R.drawable.ic_menu_crop); // Use a Folder Icon
//            holder.subtitle.setText(item.optJSONArray("children").length() + " items");
//        } else {
//            holder.icon.setImageResource(android.R.drawable.ic_menu_edit); // Use a Note Icon
//            holder.subtitle.setText("Last edited recently");
//        }
//
//        holder.itemView.setOnClickListener(v -> listener.onItemClick(item));
//    }
//
//    @Override
//    public int getItemCount() { return items.size(); }
//
//    static class ViewHolder extends RecyclerView.ViewHolder {
//        TextView title, subtitle;
//        ImageView icon;
//        ViewHolder(View v) {
//            super(v);
//            title = v.findViewById(R.id.note_title);
//            subtitle = v.findViewById(R.id.note_subtitle);
//            icon = v.findViewById(R.id.note_icon);
//        }
//    }
//}