package com.ahmedjazzar.rosetta;

import android.app.Dialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Locale;

/**
 * This fragment is responsible for displaying the supported locales and performing any necessary
 * action that allows user to select, cancel, and commit changes.
 *
 * Created by ahmedjazzar on 1/19/16.
 */

public class LanguagesListDialogFragment extends DialogFragment {

    private final int DIALOG_TITLE_ID = R.string.language;
    private final int DIALOG_POSITIVE_ID = R.string.ok;
    private final int DIALOG_NEGATIVE_ID = R.string.cancel;

    private int mSelectedLanguage = -1;
    private final Logger mLogger;

    public LanguagesListDialogFragment()  {
        String TAG = LanguagesListDialogFragment.class.getName();
        this.mLogger = new Logger(TAG);
    }

    /**
     * @return a Dialog fragment
     */
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity());
        mLogger.debug("Building DialogFragment.");

        builder.setTitle(getString(DIALOG_TITLE_ID))
                .setSingleChoiceItems(
                        getLanguages(),
                        getCurrentLocaleIndex(),
                        (dialogInterface, which) -> onLanguageSelectedLocalized(which))
                .setPositiveButton(
                        getString(DIALOG_POSITIVE_ID).toUpperCase(),
                        (dialogInterface, which) -> onPositiveClick())
                .setNegativeButton(
                        getString(DIALOG_NEGATIVE_ID).toUpperCase(),
                        (dialogInterface, which) -> onNegativeClick());

        mLogger.verbose("DialogFragment built.");
        return builder.create();
    }

    /**
     * @param which the position of the selected locale
     */
    protected void onLanguageSelected(int which)  {
        // just update the selected locale
        mSelectedLanguage = which;
    }

    /**
     * Localizing the dialog buttons and title
     * @param which the position of the selected locale
     */
    protected void onLanguageSelectedLocalized(int which)  {

        // update the selected locale
        mSelectedLanguage = which;
        AlertDialog dialog = (AlertDialog) getDialog();

        mLogger.debug("Displaying dialog main strings in the selected " +
                "locale");

        onLanguageSelectedLocalized(
                which,
                null,
                dialog.getButton(AlertDialog.BUTTON_POSITIVE),
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE));
    }

    /**
     * the position of the selected locale given the ids
     * @param which the position of the selected locale
     * @param titleView dialog's title text view
     * @param positiveButton positive button
     * @param negativeButton negative button
     */
    protected void onLanguageSelectedLocalized(int which, TextView titleView, Button positiveButton,
                                               Button negativeButton)  {

        // update the selected locale
        mSelectedLanguage = which;
        Locale locale = LocalesUtils.getLocaleFromIndex(mSelectedLanguage);
        AlertDialog dialog = (AlertDialog) getDialog();
        FragmentActivity activity = getActivity();

        mLogger.debug("Displaying dialog main strings in the selected " +
                "locale");

        assert activity != null;
        String LocalizedTitle = LocalesUtils.getInSpecificLocale(activity, locale, DIALOG_TITLE_ID);
        if(titleView == null)   {
            // Display dialog title in the selected locale
            assert dialog != null;
            dialog.setTitle(LocalizedTitle);
        } else  {
            titleView.setText(LocalizedTitle);
        }

        // Display positive button text in the selected locale
        positiveButton.setText(LocalesUtils.getInSpecificLocale(
                activity, locale, DIALOG_POSITIVE_ID));

        // Display negative button text in the selected locale
        negativeButton.setText(LocalesUtils.getInSpecificLocale(
                activity, locale, DIALOG_NEGATIVE_ID));
    }

    /**
     * called when the user approved changing locale
     */
    protected void onPositiveClick() {

        // if the user did not select the same locale go ahead, else ignore
        if (mSelectedLanguage != -1 &&
                mSelectedLanguage != LocalesUtils.getCurrentLocaleIndex()) {

            // Try changing the locale
            if (LocalesUtils.setAppLocale(
                    getActivity(), mSelectedLanguage)) {

                mLogger.info("App locale changed successfully.");
                LocalesUtils.refreshApplication(requireActivity());
            } else {
                mLogger.error("Unsuccessful trial to change the App locale.");
                // TODO: notify the user that his request not placed
            }
        } else  {
            dismiss();
        }
    }

    /**
     * called when the user discarded changing locale
     */
    protected void onNegativeClick() {
        mLogger.verbose("User discarded changing language.");
        mLogger.debug("Return to the original locale.");
        this.onLanguageSelectedLocalized(this.getCurrentLocaleIndex());
    }

    /**
     *
     * @return available languages
     */
    protected String[] getLanguages() {
        ArrayList<String> languages =  LocalesUtils.getLocalesWithDisplayName();
        return languages.toArray(new String[languages.size()]);
    }

    /**
     *
     * @return the index of the locale that app is using now
     */
    protected int getCurrentLocaleIndex()   {
        return LocalesUtils.getCurrentLocaleIndex();
    }

}