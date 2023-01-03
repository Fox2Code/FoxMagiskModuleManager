package com.fox2code.mmm.utils;

import android.content.Context;
import android.content.res.Resources;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.LinearLayoutCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

// ProgressDialog is deprecated because it's an bad UX pattern, but sometimes we have no other choice...
public class BudgetProgressDialog {
	public static AlertDialog build(Context context, String title, String message) {
		Resources r = context.getResources();
		int padding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, r.getDisplayMetrics()));
		LinearLayoutCompat v = new LinearLayoutCompat(context);
		v.setOrientation(LinearLayoutCompat.HORIZONTAL);
		ProgressBar pb = new ProgressBar(context);
		v.addView(pb, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		TextView t = new TextView(context);
		t.setGravity(Gravity.CENTER);
		v.addView(t, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, 4));
		v.setPadding(padding, padding, padding, padding);

		t.setText(message);
		return new MaterialAlertDialogBuilder(context)
				.setTitle(title)
				.setView(v)
				.setCancelable(false)
				.create();
	}

	public static AlertDialog build(Context context, int title, String message) {
		return build(context, context.getString(title), message);
	}

	public static AlertDialog build(Context context, String title, int message) {
		return build(context, title, context.getString(message));
	}

	public static AlertDialog build(Context context, int title, int message) {
		return build(context, title, context.getString(message));
	}
}
