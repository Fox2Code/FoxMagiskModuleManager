package com.fox2code.mmm.installer;

import android.graphics.Typeface;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class InstallerTerminal extends RecyclerView.Adapter<InstallerTerminal.TextViewHolder> {
    private final RecyclerView recyclerView;
    private final ArrayList<String> terminal;
    private final Object lock = new Object();
    private final int foreground;

    public InstallerTerminal(RecyclerView recyclerView,int foreground) {
        recyclerView.setLayoutManager(
                new LinearLayoutManager(recyclerView.getContext()));
        this.recyclerView = recyclerView;
        this.foreground = foreground;
        this.terminal = new ArrayList<>();
        this.recyclerView.setAdapter(this);
    }

    @NonNull
    @Override
    public TextViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new TextViewHolder(new TextView(parent.getContext()), this.foreground);
    }

    @Override
    public void onBindViewHolder(@NonNull TextViewHolder holder, int position) {
        holder.setText(this.terminal.get(position));
    }

    @Override
    public int getItemCount() {
        return this.terminal.size();
    }

    public void addLine(String line) {
        synchronized (lock) {
            boolean bottom = !this.recyclerView.canScrollVertically(1);
            int index = this.terminal.size();
            this.terminal.add(line);
            this.notifyItemInserted(index);
            if (bottom) this.recyclerView.scrollToPosition(index);
        }
    }

    public void setLine(int index, String line) {
        synchronized (lock) {
            this.terminal.set(index, line);
            this.notifyItemChanged(index);
        }
    }

    public void setLastLine(String line) {
        synchronized (lock) {
            int size = this.terminal.size();
            if (size == 0) {
                this.terminal.add(line);
                this.notifyItemInserted(0);
            } else {
                this.terminal.set(size - 1, line);
                this.notifyItemChanged(size - 1);
            }
        }
    }

    public String getLastLine() {
        synchronized (lock) {
            int size = this.terminal.size();
            return size == 0 ? "" :
                    this.terminal.get(size - 1);
        }
    }

    public void removeLastLine() {
        synchronized (lock) {
            int size = this.terminal.size();
            if (size != 0) {
                this.terminal.remove(size - 1);
                this.notifyItemRemoved(size - 1);
            }
        }
    }

    public void clearTerminal() {
        synchronized (lock) {
            int size = this.terminal.size();
            if (size != 0) {
                this.terminal.clear();
                this.notifyItemRangeRemoved(0, size);
            }
        }
    }

    public void scrollUp() {
        synchronized (lock) {
            this.recyclerView.scrollToPosition(0);
        }
    }

    public void scrollDown() {
        synchronized (lock) {
            this.recyclerView.scrollToPosition(this.terminal.size() - 1);
        }
    }

    public static class TextViewHolder extends RecyclerView.ViewHolder {
        private final TextView textView;

        public TextViewHolder(@NonNull TextView itemView,int foreground) {
            super(itemView);
            this.textView = itemView;
            itemView.setTypeface(Typeface.MONOSPACE);
            itemView.setTextColor(foreground);
            itemView.setTextSize(12);
            itemView.setLines(1);
            itemView.setText(" ");
        }

        private void setText(String text) {
            this.textView.setText(text.isEmpty() ? " " : text);
        }
    }
}
