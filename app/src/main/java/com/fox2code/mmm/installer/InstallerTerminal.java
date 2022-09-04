package com.fox2code.mmm.installer;

import android.graphics.Typeface;
import android.text.Spannable;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fox2code.androidansi.AnsiContext;

import java.util.ArrayList;

public class InstallerTerminal extends RecyclerView.Adapter<InstallerTerminal.TextViewHolder> {
    private final RecyclerView recyclerView;
    private final ArrayList<ProcessedLine> terminal;
    private final AnsiContext ansiContext;
    private final Object lock = new Object();
    private final int foreground;
    private final boolean mmtReborn;
    private boolean ansiEnabled = false;

    public InstallerTerminal(RecyclerView recyclerView, boolean isLightTheme,
                             int foreground, boolean mmtReborn) {
        recyclerView.setLayoutManager(
                new LinearLayoutManager(recyclerView.getContext()));
        this.recyclerView = recyclerView;
        this.foreground = foreground;
        this.mmtReborn = mmtReborn;
        this.terminal = new ArrayList<>();
        this.ansiContext = (isLightTheme ? AnsiContext.LIGHT : AnsiContext.DARK).copy();
        this.recyclerView.setAdapter(this);
    }

    @NonNull
    @Override
    public TextViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new TextViewHolder(new TextView(parent.getContext()), this.foreground);
    }

    @Override
    public void onBindViewHolder(@NonNull TextViewHolder holder, int position) {
        this.terminal.get(position).setText(holder.textView);
    }

    @Override
    public int getItemCount() {
        return this.terminal.size();
    }

    public void addLine(String line) {
        synchronized (lock) {
            boolean bottom = !this.recyclerView.canScrollVertically(1);
            int index = this.terminal.size();
            this.terminal.add(this.process(line));
            this.notifyItemInserted(index);
            if (bottom) this.recyclerView.scrollToPosition(index);
        }
    }

    public void setLastLine(String line) {
        synchronized (lock) {
            int size = this.terminal.size();
            if (size == 0) {
                this.terminal.add(this.process(line));
                this.notifyItemInserted(0);
            } else {
                this.terminal.set(size - 1, this.process(line));
                this.notifyItemChanged(size - 1);
            }
        }
    }

    public String getLastLine() {
        synchronized (lock) {
            int size = this.terminal.size();
            return size == 0 ? "" :
                    this.terminal.get(size - 1).line;
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

    public void enableAnsi() {
        this.ansiEnabled = true;
    }

    public void disableAnsi() {
        this.ansiEnabled = false;
        this.ansiContext.reset();
    }

    public boolean isAnsiEnabled() {
        return this.ansiEnabled;
    }

    private ProcessedLine process(String line) {
        if (line.isEmpty()) return new ProcessedLine(" ", null);
        if (this.mmtReborn) {
            if (line.startsWith("- ")) {
                line = "[*] " + line.substring(2);
            } else if (line.startsWith("! ")) {
                line = "[!] " + line.substring(2);
            }
        }
        return new ProcessedLine(line, this.ansiEnabled ?
                this.ansiContext.parseAsSpannable(line) : null);
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
    }

    private static class ProcessedLine {
        public final String line;
        public final Spannable spannable;

        ProcessedLine(String line, Spannable spannable) {
            this.line = line;
            this.spannable = spannable;
        }

        public void setText(TextView textView) {
            textView.setText(this.spannable == null ?
                    this.line: this.spannable);
        }
    }
}
