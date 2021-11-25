package com.fox2code.mmm;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.fox2code.mmm.manager.ModuleInfo;
import com.fox2code.mmm.manager.ModuleManager;
import com.fox2code.mmm.repo.RepoModule;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.topjohnwu.superuser.internal.UiThreadHandler;

import java.util.ArrayList;

public final class ModuleViewAdapter extends RecyclerView.Adapter<ModuleViewAdapter.ViewHolder> {
    private static final boolean DEBUG = false;
    public final ArrayList<ModuleHolder> moduleHolders = new ArrayList<>();

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.module_entry, parent, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final ModuleHolder moduleHolder = this.moduleHolders.get(position);
        if (holder.update(moduleHolder)) {
            UiThreadHandler.handler.post(() -> {
                if (this.moduleHolders.get(position) == moduleHolder) {
                    this.moduleHolders.remove(position);
                    this.notifyItemRemoved(position);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return this.moduleHolders.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final CardView cardView;
        private final ImageButton buttonAction;
        private final SwitchMaterial switchMaterial;
        private final TextView titleText;
        private final TextView creditText;
        private final TextView descriptionText;
        private final TextView updateText;
        private final ImageButton[] actionsButtons;
        private final ArrayList<ActionButtonType> actionButtonsTypes;
        private boolean initState;
        public ModuleHolder moduleHolder;
        public Drawable background;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            this.initState = true;
            this.cardView = itemView.findViewById(R.id.card_view);
            this.buttonAction = itemView.findViewById(R.id.button_action);
            this.switchMaterial = itemView.findViewById(R.id.switch_action);
            this.titleText = itemView.findViewById(R.id.title_text);
            this.creditText = itemView.findViewById(R.id.credit_text);
            this.descriptionText = itemView.findViewById(R.id.description_text);
            this.updateText = itemView.findViewById(R.id.updated_text);
            this.actionsButtons = new ImageButton[6];
            this.actionsButtons[0] = itemView.findViewById(R.id.button_action1);
            this.actionsButtons[1] = itemView.findViewById(R.id.button_action2);
            this.actionsButtons[2] = itemView.findViewById(R.id.button_action3);
            this.actionsButtons[3] = itemView.findViewById(R.id.button_action4);
            this.actionsButtons[4] = itemView.findViewById(R.id.button_action5);
            this.actionsButtons[5] = itemView.findViewById(R.id.button_action6);
            this.background = this.cardView.getBackground();
            // Apply default
            this.cardView.setOnClickListener(v -> {
                ModuleHolder moduleHolder = this.moduleHolder;
                if (moduleHolder != null &&
                        moduleHolder.notificationType != null) {
                    View.OnClickListener onClickListener =
                            moduleHolder.notificationType.onClickListener;
                    if (onClickListener != null)
                        onClickListener.onClick(v);
                }
            });
            this.switchMaterial.setEnabled(false);
            this.switchMaterial.setOnCheckedChangeListener((v, checked) -> {
                if (this.initState) return; // Skip if non user
                ModuleHolder moduleHolder = this.moduleHolder;
                if (moduleHolder != null && moduleHolder.moduleInfo != null) {
                    ModuleInfo moduleInfo = moduleHolder.moduleInfo;
                    if (!ModuleManager.getINSTANCE().setEnabledState(moduleInfo, checked)) {
                        this.switchMaterial.setChecked( // Reset to valid state if action failed
                                (moduleInfo.flags & ModuleInfo.FLAG_MODULE_DISABLED) == 0);
                    }
                }
            });
            this.actionButtonsTypes = new ArrayList<>();
            for (int i = 0; i < this.actionsButtons.length; i++) {
                final int index = i;
                this.actionsButtons[i].setOnClickListener(v -> {
                    if (this.initState) return; // Skip if non user
                    ModuleHolder moduleHolder = this.moduleHolder;
                    if (index < this.actionButtonsTypes.size() && moduleHolder != null) {
                        this.actionButtonsTypes.get(index)
                                .doAction((ImageButton) v, moduleHolder);
                        if (moduleHolder.shouldRemove()) {
                            this.cardView.setVisibility(View.GONE);
                        }
                    }
                });
                this.actionsButtons[i].setOnLongClickListener(v -> {
                    if (this.initState) return false; // Skip if non user
                    ModuleHolder moduleHolder = this.moduleHolder;
                    boolean didSomething = false;
                    if (index < this.actionButtonsTypes.size() && moduleHolder != null) {
                        didSomething = this.actionButtonsTypes.get(index)
                                .doActionLong((ImageButton) v, moduleHolder);
                        if (moduleHolder.shouldRemove()) {
                            this.cardView.setVisibility(View.GONE);
                        }
                    }
                    return didSomething;
                });
            }
            this.initState = false;
        }

        @NonNull
        public final String getString(@StringRes int resId) {
            return this.itemView.getContext().getString(resId);
        }

        @SuppressLint("SetTextI18n")
        public boolean update(ModuleHolder moduleHolder) {
            this.initState = true;
            if (moduleHolder.isModuleHolder() && moduleHolder.shouldRemove()) {
                this.cardView.setVisibility(View.GONE);
                this.moduleHolder = null;
                this.initState = false;
                return true;
            }
            ModuleHolder.Type type = moduleHolder.getType();
            ModuleHolder.Type vType = moduleHolder.getCompareType(type);
            this.cardView.setVisibility(View.VISIBLE);
            boolean showCaseMode = MainApplication.isShowcaseMode();
            if (moduleHolder.isModuleHolder()) {
                this.buttonAction.setVisibility(View.GONE);
                ModuleInfo localModuleInfo = moduleHolder.moduleInfo;
                if (localModuleInfo != null) {
                    this.switchMaterial.setVisibility(View.VISIBLE);
                    this.switchMaterial.setChecked((localModuleInfo.flags &
                            ModuleInfo.FLAG_MODULE_DISABLED) == 0);
                } else {
                    this.switchMaterial.setVisibility(View.GONE);
                }
                this.creditText.setVisibility(View.VISIBLE);
                this.descriptionText.setVisibility(View.VISIBLE);

                ModuleInfo moduleInfo = moduleHolder.getMainModuleInfo();
                this.titleText.setText(moduleInfo.name);
                this.creditText.setText(moduleInfo.version + " " +
                        this.getString(R.string.module_by) + " " + moduleInfo.author);
                this.descriptionText.setText(moduleInfo.description);
                String updateText = moduleHolder.getUpdateTimeText();
                if (!updateText.isEmpty()) {
                    this.updateText.setVisibility(View.VISIBLE);
                    this.updateText.setText(
                            this.getString(R.string.module_last_update) + " " + updateText + "\n" +
                            this.getString(R.string.module_repo) + " " + moduleHolder.getRepoName());
                } else if (moduleHolder.moduleId.equals("hosts")) {
                    this.updateText.setVisibility(View.VISIBLE);
                    this.updateText.setText(R.string.magisk_builtin_module);
                } else if (moduleHolder.moduleId.equals("substratum")) {
                    this.updateText.setVisibility(View.VISIBLE);
                    this.updateText.setText(R.string.substratum_builtin_module);
                } else {
                    this.updateText.setVisibility(View.GONE);
                }
                this.actionButtonsTypes.clear();
                moduleHolder.getButtons(itemView.getContext(), this.actionButtonsTypes, showCaseMode);
                this.switchMaterial.setEnabled(!showCaseMode &&
                        !moduleHolder.hasFlag(ModuleInfo.FLAG_MODULE_UPDATING));
                for (int i = 0; i < this.actionsButtons.length; i++) {
                    ImageButton imageButton = this.actionsButtons[i];
                    if (i < this.actionButtonsTypes.size()) {
                        imageButton.setVisibility(View.VISIBLE);
                        this.actionButtonsTypes.get(i)
                                .update(imageButton, moduleHolder);
                    } else {
                        imageButton.setVisibility(View.GONE);
                    }
                }
                this.cardView.setClickable(false);
                if (moduleHolder.isModuleHolder() &&
                        moduleHolder.hasFlag(ModuleInfo.FLAG_MODULE_ACTIVE)) {
                    this.titleText.setTypeface(Typeface.DEFAULT_BOLD);
                } else {
                    this.titleText.setTypeface(Typeface.DEFAULT);
                }
            } else {
                this.buttonAction.setVisibility(
                        type == ModuleHolder.Type.NOTIFICATION ?
                                View.VISIBLE : View.GONE);
                this.switchMaterial.setVisibility(View.GONE);
                this.creditText.setVisibility(View.GONE);
                this.descriptionText.setVisibility(View.GONE);
                this.updateText.setVisibility(View.GONE);
                this.titleText.setText(" ");
                this.creditText.setText(" ");
                this.descriptionText.setText(" ");
                this.switchMaterial.setEnabled(false);
                this.actionButtonsTypes.clear();
                for (ImageButton button:this.actionsButtons) {
                    button.setVisibility(View.GONE);
                }
                if (type == ModuleHolder.Type.NOTIFICATION) {
                    NotificationType notificationType = moduleHolder.notificationType;
                    this.titleText.setText(notificationType.textId);
                    this.buttonAction.setImageResource(notificationType.iconId);
                    this.cardView.setClickable(notificationType.onClickListener != null);
                    this.titleText.setTypeface(notificationType.special ?
                            Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                } else {
                    this.cardView.setClickable(false);
                    this.titleText.setTypeface(Typeface.DEFAULT);
                }
            }
            if (type == ModuleHolder.Type.SEPARATOR) {
                this.titleText.setText(moduleHolder.separator.title);
            }
            if (DEBUG) {
                this.titleText.setText(this.titleText.getText() + " " +
                        formatType(type) + " " + formatType(vType));
            }
            // Coloration system
            Drawable drawable = this.cardView.getBackground();
            if (drawable != null) this.background = drawable;
            if (type.hasBackground) {
                if (drawable == null) {
                    this.cardView.setBackground(this.background);
                }
                int backgroundAttr = R.attr.colorBackgroundFloating;
                if (type == ModuleHolder.Type.NOTIFICATION) {
                    backgroundAttr = moduleHolder.notificationType.backgroundAttr;
                }
                Resources.Theme theme = this.cardView.getContext().getTheme();
                TypedValue value = new TypedValue();
                theme.resolveAttribute(backgroundAttr, value, true);
                @ColorInt int color = value.data;
                // Fix card background being invisible on light theme
                if (color == Color.WHITE) color = 0xFFF8F8F8;
                this.cardView.setCardBackgroundColor(color);
            } else {
                this.cardView.setBackground(null);
            }
            if (type == ModuleHolder.Type.FOOTER) {
                this.titleText.setMinHeight(moduleHolder.footerPx);
            } else {
                this.titleText.setMinHeight(0);
            }
            this.moduleHolder = moduleHolder;
            this.initState = false;
            return false;
        }
    }

    private static String formatType(ModuleHolder.Type type) {
        return type.name().substring(0, 3) + "_" + type.ordinal();
    }
}
