package com.fox2code.mmm.module;

import androidx.annotation.DrawableRes;

import com.fox2code.mmm.R;

import java.util.Comparator;

public enum ModuleSorter implements Comparator<ModuleHolder> {
    UPDATE(R.drawable.ic_baseline_update_24) {
        @Override
        public ModuleSorter next() {
            return ALPHA;
        }
    },
    ALPHA(R.drawable.ic_baseline_sort_by_alpha_24) {
        @Override
        public int compare(ModuleHolder holder1, ModuleHolder holder2) {
            ModuleHolder.Type type1 = holder1.getType();
            ModuleHolder.Type type2 = holder2.getType();
            if (type1 == type2 && type1 == ModuleHolder.Type.INSTALLABLE) {
                int compare = Integer.compare(holder1.filterLevel, holder2.filterLevel);
                if (compare != 0) return compare;
                compare = holder1.getMainModuleNameLowercase()
                        .compareTo(holder2.getMainModuleNameLowercase());
                if (compare != 0) return compare;
            }
            return super.compare(holder1, holder2);
        }

        @Override
        public ModuleSorter next() {
            return UPDATE;
        }
    };

    @DrawableRes
    public final int icon;

    ModuleSorter(int icon) {
        this.icon = icon;
    }

    @Override
    public int compare(ModuleHolder holder1, ModuleHolder holder2) {
        return holder1.compareTo(holder2);
    }

    public abstract ModuleSorter next();
}
