package com.campustrade.auth;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.content.res.ColorStateList;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import com.campustrade.auth.data.AuthRepository;
import com.campustrade.auth.data.CloudSync;

// Login and sign-up screen. Checks campus email and calls Firebase.
public class Auth {

    private final MainActivity host;

    public Auth(MainActivity host) {
        this.host = host;
    }

    // Build the login/sign-up screen and reset old admin state.
    void showAuthScreen() {
        host.stopPresenceTracking();
        host.markOffline();
        host.adminAccess = false;
        host.adminOnlySession = false;
        ScrollView scroll = new ScrollView(host);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(host.page);

        LinearLayout shell = new LinearLayout(host);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setGravity(Gravity.CENTER);
        shell.setPadding(host.ui.dp(20), host.ui.dp(16), host.ui.dp(20), host.ui.dp(16));
        scroll.addView(shell, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout card = new LinearLayout(host);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(host.page);
        shell.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout hero = new LinearLayout(host);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(host.ui.dp(8), host.ui.dp(28), host.ui.dp(8), host.ui.dp(24));
        hero.setBackground(host.ui.bottomRule(host.text));
        card.addView(hero);

        View mark = host.ui.wordmark();
        hero.addView(mark, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48)));

        host.heroTitle = host.ui.titleText("", host.text, 40);
        host.heroTitle.setPadding(0, host.ui.dp(16), 0, 0);
        hero.addView(host.heroTitle);
        host.heroSubtitle = host.ui.bodyText("", host.muted, 14);
        host.heroSubtitle.setPadding(0, host.ui.dp(5), 0, 0);
        hero.addView(host.heroSubtitle);

        LinearLayout form = new LinearLayout(host);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(host.ui.dp(8), host.ui.dp(20), host.ui.dp(8), host.ui.dp(18));
        card.addView(form);

        LinearLayout tabs = new LinearLayout(host);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(host.ui.dp(2), host.ui.dp(2), host.ui.dp(2), host.ui.dp(2));
        tabs.setBackground(host.ui.bottomRule(host.line));
        form.addView(tabs, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                host.ui.dp(38)
        ));

        host.loginTab = host.ui.tab("Log in");
        host.signupTab = host.ui.tab("Sign up");
        host.adminTab = host.ui.tab("Admin");
        tabs.addView(host.loginTab, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        tabs.addView(host.signupTab, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        // Admin tools are never shown on the student login screen.
        host.adminTab.setVisibility(View.GONE);

        host.adminGroup = host.ui.group(form);
        host.adminGroup.addView(host.ui.label("Admin passcode"));
        host.adminPasscodeInput = host.ui.input("Moderator passcode",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        host.adminGroup.addView(host.adminPasscodeInput);
        TextView adminHelp = host.ui.smallText("Moderator access only. Admin tools can remove listings and student profiles.");
        adminHelp.setPadding(0, host.ui.dp(10), 0, 0);
        host.adminGroup.addView(adminHelp);
        Button adminUnlock = host.ui.primaryButton("Unlock admin");
        LinearLayout.LayoutParams adminUnlockParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48));
        adminUnlockParams.topMargin = host.ui.dp(18);
        host.adminGroup.addView(adminUnlock, adminUnlockParams);
        adminUnlock.setOnClickListener(v -> host.admin.attemptAdminLogin());
        TextView adminBack = host.ui.textButton("Back to student login");
        adminBack.setOnClickListener(v -> setMode(false));
        host.adminGroup.addView(adminBack, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48)));
        host.adminPasscodeInput.setOnEditorActionListener((v, actionId, event) -> {
            host.admin.attemptAdminLogin();
            return true;
        });
        host.adminGroup.setVisibility(View.GONE);

        host.signupFields = host.ui.group(form);
        host.signupFields.addView(host.ui.label("Full name"));
        host.fullNameInput = host.ui.input("Your full name", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        host.signupFields.addView(host.fullNameInput);

        host.authEmailGroup = host.ui.group(form);
        host.authEmailGroup.addView(host.ui.label("Eduvos email"));
        host.emailInput = host.ui.input("you@vossie.net", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        host.authEmailGroup.addView(host.emailInput);
        final TextView emailHint = host.ui.smallText("Use your name@vossie.net campus email.");
        emailHint.setPadding(0, host.ui.dp(6), 0, 0);
        emailHint.setVisibility(View.GONE);
        host.authEmailGroup.addView(emailHint);

        host.studentNumberGroup = host.ui.group(form);
        host.studentNumberGroup.addView(host.ui.label("Student number"));
        host.studentNumberInput = host.ui.input("Student number", InputType.TYPE_CLASS_TEXT);
        host.studentNumberGroup.addView(host.studentNumberInput);
        host.studentNumberGroup.addView(host.ui.label("Course"));
        Spinner courseSpinner = new Spinner(host);
        ArrayAdapter<String> courseAdapter = host.ui.themedSpinnerAdapter(host.COURSES);
        courseSpinner.setAdapter(courseAdapter);
        courseSpinner.setPopupBackgroundDrawable(new android.graphics.drawable.ColorDrawable(host.surface()));
        courseSpinner.setBackgroundTintList(ColorStateList.valueOf(host.text));
        courseSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                host.selectedCourse = host.COURSES[position];
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        host.studentNumberGroup.addView(courseSpinner);
        host.studentNumberGroup.addView(host.ui.label("Year of study"));
        Spinner yearSpinner = new Spinner(host);
        ArrayAdapter<String> yearAdapter = host.ui.themedSpinnerAdapter(host.YEARS);
        yearSpinner.setAdapter(yearAdapter);
        yearSpinner.setPopupBackgroundDrawable(new android.graphics.drawable.ColorDrawable(host.surface()));
        yearSpinner.setBackgroundTintList(ColorStateList.valueOf(host.text));
        yearSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                host.selectedYear = host.YEARS[position];
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        host.studentNumberGroup.addView(yearSpinner);

        host.authPasswordGroup = host.ui.group(form);
        host.authPasswordGroup.addView(host.ui.label("Password"));
        LinearLayout passwordRow = host.ui.passwordField("Enter your password");
        host.passwordInput = (EditText) passwordRow.getChildAt(0);
        host.passwordToggle = (TextView) passwordRow.getChildAt(1);
        host.authPasswordGroup.addView(passwordRow);
        host.passwordStrengthText = host.ui.smallText("Use 10+ characters with uppercase, lowercase, a number and a symbol.");
        host.passwordStrengthText.setPadding(0, host.ui.dp(8), 0, 0);
        host.authPasswordGroup.addView(host.passwordStrengthText);

        host.confirmPasswordGroup = host.ui.group(form);
        host.confirmPasswordGroup.addView(host.ui.label("Confirm password"));
        LinearLayout confirmRow = host.ui.passwordField("Repeat your password");
        host.confirmPasswordInput = (EditText) confirmRow.getChildAt(0);
        host.confirmPasswordToggle = (TextView) confirmRow.getChildAt(1);
        host.confirmPasswordGroup.addView(confirmRow);
        final TextView confirmHint = host.ui.smallText("Passwords must match.");
        confirmHint.setPadding(0, host.ui.dp(6), 0, 0);
        confirmHint.setVisibility(View.GONE);
        host.confirmPasswordGroup.addView(confirmHint);

        host.loginOptions = new LinearLayout(host);
        host.loginOptions.setGravity(Gravity.CENTER_VERTICAL);
        host.loginOptions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams optionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        optionsParams.topMargin = host.ui.dp(14);
        form.addView(host.loginOptions, optionsParams);

        host.keepLoggedInCheck = new CheckBox(host);
        host.ui.themedToggle(host.keepLoggedInCheck);
        host.loginOptions.addView(host.keepLoggedInCheck);
        host.loginOptions.addView(host.ui.smallText("Keep me logged in"), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView forgot = host.ui.linkText("Forgot password?");
        forgot.setOnClickListener(v -> {
            String email = host.normalizeEmail(host.ui.text(host.emailInput));
            if (email.isEmpty() || email.indexOf('@') < 0) {
                host.emailInput.setError("Enter your student email first, then tap Forgot password.");
                return;
            }
            sendResetEmail(email);
        });
        host.loginOptions.addView(forgot);

        host.signupTerms = new LinearLayout(host);
        host.signupTerms.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams termsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        termsParams.topMargin = host.ui.dp(14);
        form.addView(host.signupTerms, termsParams);

        LinearLayout termsRow = new LinearLayout(host);
        termsRow.setGravity(Gravity.CENTER_VERTICAL);
        termsRow.setOrientation(LinearLayout.HORIZONTAL);
        host.signupTerms.addView(termsRow);

        host.termsCheck = new CheckBox(host);
        host.ui.themedToggle(host.termsCheck);
        host.termsCheck.setMinimumWidth(host.ui.dp(48));
        host.termsCheck.setMinimumHeight(host.ui.dp(48));
        host.termsCheck.setLayoutParams(new LinearLayout.LayoutParams(host.ui.dp(48), host.ui.dp(48)));
        host.termsCheck.setClickable(true);
        host.termsCheck.setFocusable(true);
        termsRow.addView(host.termsCheck);
        termsRow.addView(host.ui.smallText("I agree to the Terms & Conditions."),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        termsRow.setOnClickListener(v -> host.termsCheck.toggle());

        TextView termsLink = host.ui.linkText("Read the full Terms & Conditions");
        termsLink.setPadding(host.ui.dp(48), 0, 0, 0);
        termsLink.setOnClickListener(v -> Terms.show(host));
        host.signupTerms.addView(termsLink);

        host.authNotice = host.ui.bodyText("Sign in with your verified Eduvos @vossie.net email. Sign-up sends a verification link; you can log in after confirming it.",
                host.nightActive ? Color.rgb(255, 226, 168) : Color.rgb(92, 74, 30), 13);
        host.authNotice.setPadding(host.ui.dp(12), host.ui.dp(12), host.ui.dp(12), host.ui.dp(12));
        host.authNotice.setBackground(host.ui.round(host.nightActive ? Color.rgb(58, 48, 26) : Color.rgb(255, 240, 203), 12));
        LinearLayout.LayoutParams noticeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        noticeParams.topMargin = host.ui.dp(16);
        form.addView(host.authNotice, noticeParams);

        if (host.loginTab != null) host.loginTab.setOnClickListener(v -> setMode(false));
        if (host.signupTab != null) host.signupTab.setOnClickListener(v -> setMode(true));
        if (host.adminTab != null) host.adminTab.setOnClickListener(v -> setAdminMode());
        if (host.passwordToggle != null) host.passwordToggle.setOnClickListener(v -> host.ui.togglePassword(host.passwordInput, true));
        if (host.confirmPasswordToggle != null) host.confirmPasswordToggle.setOnClickListener(v -> host.ui.togglePassword(host.confirmPasswordInput, false));
        if (host.termsCheck != null) host.termsCheck.setOnCheckedChangeListener((buttonView, isChecked) -> validateAuth());

        TextWatcher watcher = new MainActivity.SimpleWatcher() {
            @Override public void afterTextChanged(Editable s) {
                host.ui.updatePasswordStrength();
                updateInlineHints(emailHint, confirmHint);
                validateAuth();
            }
        };
        host.fullNameInput.addTextChangedListener(watcher);
        host.emailInput.addTextChangedListener(watcher);
        host.studentNumberInput.addTextChangedListener(watcher);
        host.passwordInput.addTextChangedListener(watcher);
        host.confirmPasswordInput.addTextChangedListener(watcher);

        // Keep the main button at the bottom so the long form can't hide it.
        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(host.page);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        host.authFooter = new LinearLayout(host);
        host.authFooter.setOrientation(LinearLayout.VERTICAL);
        host.authFooter.setPadding(host.ui.dp(20), host.ui.dp(10), host.ui.dp(20), host.ui.dp(14));
        host.authFooter.setBackground(host.ui.edgeRule(host.text, true));
        host.submitButton = host.ui.primaryButton("Log in");
        host.authFooter.addView(host.submitButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(50)));
        host.switchPrompt = new TextView(host);
        host.switchPrompt.setGravity(Gravity.CENTER);
        host.switchPrompt.setTextColor(host.muted);
        host.switchPrompt.setTextSize(12);
        host.switchPrompt.setPadding(0, host.ui.dp(8), 0, 0);
        host.authFooter.addView(host.switchPrompt);
        root.addView(host.authFooter, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        host.switchPrompt.setOnClickListener(v -> setMode(!host.signupMode));
        host.submitButton.setOnClickListener(v -> submitAuth());

        host.setContentView(root);
        setMode(false);
    }

    // Sends the reset link and shows the five-minute resend window.
    private void sendResetEmail(String email) {
        new AuthRepository().resetPassword(email, new AuthRepository.SimpleCallback() {
            @Override public void onOk() {
                if (host.isDestroyed() || host.isFinishing()) return;
                showLinkCountdown(email, 5 * 60, false);
            }
            @Override public void onBad(String message) {
                if (host.isDestroyed() || host.isFinishing()) return;
                host.ui.dialog().setTitle("Reset email didn't send")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show();
            }
        });
    }

    /** In-app reset request form: current password, new password, then confirmation. */
    private void showResetPasswordForm(final String email) {
        if (host.isDestroyed() || host.isFinishing()) return;
        LinearLayout form = host.ui.workflowForm();
        form.addView(host.ui.bodyText("For " + email
                        + ". Enter your current password first, then choose and confirm a new password. "
                        + "If you don't know your current password, use the reset link in your email.",
                host.muted, 11));
        EditText currentPassword = host.ui.listingField(form, "Current password",
                "Enter the password you use now",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        TextView currentError = host.ui.fieldError();
        form.addView(currentError);
        EditText newPassword = host.ui.listingField(form, "New password",
                "10+ characters, with upper, lower, number and symbol",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        TextView newError = host.ui.fieldError();
        form.addView(newError);
        EditText confirmPassword = host.ui.listingField(form, "Re-enter new password",
                "Type the new password again",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        TextView confirmError = host.ui.fieldError();
        form.addView(confirmError);
        TextView generalError = host.ui.fieldError();
        form.addView(generalError);
        ScrollView scroll = new ScrollView(host);
        scroll.addView(form);
        AlertDialog dialog = host.ui.dialog()
                .setTitle("Set new password")
                .setView(scroll)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Update password", null)
                .create();
        currentPassword.addTextChangedListener(new MainActivity.SimpleWatcher() {
            @Override public void afterTextChanged(Editable s) {
                host.ui.clearFieldError(currentPassword, currentError);
                generalError.setVisibility(View.GONE);
            }
        });
        newPassword.addTextChangedListener(new MainActivity.SimpleWatcher() {
            @Override public void afterTextChanged(Editable s) {
                host.ui.clearFieldError(newPassword, newError);
                if (!host.ui.text(confirmPassword).isEmpty()
                        && !host.ui.text(confirmPassword).equals(s.toString())) {
                    host.ui.showFieldError(confirmPassword, confirmError, "The new passwords don't match.");
                } else {
                    host.ui.clearFieldError(confirmPassword, confirmError);
                }
            }
        });
        confirmPassword.addTextChangedListener(new MainActivity.SimpleWatcher() {
            @Override public void afterTextChanged(Editable s) {
                String confirm = s.toString();
                if (!confirm.isEmpty() && !confirm.equals(host.ui.text(newPassword))) {
                    host.ui.showFieldError(confirmPassword, confirmError, "The new passwords don't match.");
                } else {
                    host.ui.clearFieldError(confirmPassword, confirmError);
                }
            }
        });
        dialog.setOnShowListener(shown -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String currentValue = host.ui.text(currentPassword);
                    String newValue = host.ui.text(newPassword);
                    String confirmValue = host.ui.text(confirmPassword);
                    host.ui.clearFieldError(currentPassword, currentError);
                    host.ui.clearFieldError(newPassword, newError);
                    host.ui.clearFieldError(confirmPassword, confirmError);
                    generalError.setVisibility(View.GONE);
                    boolean valid = true;
                    if (currentValue.isEmpty()) {
                        host.ui.showFieldError(currentPassword, currentError, "Enter your current password.");
                        valid = false;
                    }
                    if (!host.ui.isStrongPassword(newValue)) {
                        host.ui.showFieldError(newPassword, newError,
                                "Use 10+ characters with uppercase, lowercase, a number and a symbol.");
                        valid = false;
                    } else if (newValue.equals(currentValue)) {
                        host.ui.showFieldError(newPassword, newError,
                                "Choose a password different from your current password.");
                        valid = false;
                    }
                    if (confirmValue.isEmpty()) {
                        host.ui.showFieldError(confirmPassword, confirmError, "Re-enter the new password.");
                        valid = false;
                    } else if (!newValue.equals(confirmValue)) {
                        host.ui.showFieldError(confirmPassword, confirmError, "The new passwords don't match.");
                        valid = false;
                    }
                    if (!valid) return;
                    Button update = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                    update.setEnabled(false);
                    update.setText("Updating...");
                    new AuthRepository().resetPasswordWithCurrent(email, currentValue, newValue,
                            new AuthRepository.SimpleCallback() {
                                @Override public void onOk() {
                                    dialog.dismiss();
                                    if (host.emailInput != null) host.emailInput.setText(email);
                                    host.ui.showSimpleMessage("Password updated",
                                            "Your password was changed. Log in with the new password.");
                                }
                                @Override public void onBad(String message) {
                                    update.setEnabled(true);
                                    update.setText("Update password");
                                    String lower = message == null ? ""
                                            : message.toLowerCase(java.util.Locale.ROOT);
                                    if (lower.contains("current password") || lower.contains("incorrect")
                                            || lower.contains("credential") || lower.contains("wrong")) {
                                        host.ui.showFieldError(currentPassword, currentError,
                                                "Current password is incorrect.");
                                        currentPassword.requestFocus();
                                    } else {
                                        generalError.setText(message == null || message.isEmpty()
                                                ? "Password didn't change. Try again." : message);
                                        generalError.setVisibility(View.VISIBLE);
                                    }
                                }
                            });
                }));
        dialog.setOnDismissListener(d -> {
            currentPassword.setText("");
            newPassword.setText("");
            confirmPassword.setText("");
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    /** The reset window is five minutes. Resend is always one tap away. */
    private void showLinkCountdown(final String email, final int seconds, final boolean verification) {
        final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        final AlertDialog[] holder = new AlertDialog[1];
        final int[] left = {seconds};
        final boolean[] stopped = {false};
        final Runnable[] tickHolder = new Runnable[1];
        final Runnable tick = new Runnable() {
            @Override public void run() {
                if (stopped[0] || holder[0] == null) return;
                int minutes = Math.max(0, left[0]) / 60;
                int secs = Math.max(0, left[0]) % 60;
                String title = verification ? "Verify your email" : "Reset email requested";
                String body = verification
                        ? "We sent a verification link to " + email
                        + ".\n\nThis verification window closes in "
                        + String.format(java.util.Locale.ROOT, "%d:%02d", minutes, secs)
                        + ". If it expires, CampusTrade sends another link automatically."
                        : "If " + email + " is registered, a reset link is on its way. "
                        + "Check your inbox and spam folder.\n\nThe link window closes in "
                        + String.format(java.util.Locale.ROOT, "%d:%02d", minutes, secs)
                        + ". Use Resend for a fresh link, or Set new password to update it in the app.";
                holder[0].setTitle(title);
                holder[0].setMessage(body);
                if (verification) {
                    holder[0].getButton(AlertDialog.BUTTON_NEUTRAL).setVisibility(
                            left[0] > 0 ? android.view.View.VISIBLE : android.view.View.GONE);
                }
                if (left[0]-- <= 0) {
                    if (verification) {
                        new AuthRepository().resendVerification(new AuthRepository.SimpleCallback() {
                            @Override public void onOk() {
                                left[0] = 15 * 60;
                                stopped[0] = false;
                                holder[0].setMessage("A fresh verification link was sent to " + email
                                        + ".\n\nThe new window closes in 15:00.");
                                handler.postDelayed(tickHolder[0], 1000);
                            }
                            @Override public void onBad(String message) {
                                holder[0].setMessage("The verification window ended and the link "
                                        + "couldn't be resent automatically.\n\n" + message
                                        + "\n\nClose this and log in with your password to try again.");
                            }
                        });
                        stopped[0] = true;
                    } else {
                        holder[0].setMessage("The five-minute reset window has ended. "
                                + "Tap Resend for a fresh link, or close this and use Forgot password again.");
                        stopped[0] = true;
                    }
                    return;
                }
                handler.postDelayed(this, 1000);
            }
        };
        tickHolder[0] = tick;
        AlertDialog dialog = host.ui.dialog()
                .setTitle(verification ? "Verify your email" : "Reset email requested")
                .setMessage("Preparing your link window...")
                .setPositiveButton(verification ? "I've verified" : "Close", null)
                .setNegativeButton(verification ? "Close" : "Resend",
                        verification ? null : (d, which) -> sendResetEmail(email))
                .setNeutralButton(verification ? "Resend now" : "Set new password",
                        verification ? (d, which) -> resendVerificationNow(email)
                                : (d, which) -> {
                                    d.dismiss();
                                    showResetPasswordForm(email);
                                })
                .create();
        holder[0] = dialog;
        dialog.setOnShowListener(d -> {
            if (verification) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v ->
                        checkVerificationNow(dialog, email));
            }
        });
        dialog.setOnDismissListener(d -> {
            stopped[0] = true;
            handler.removeCallbacks(tick);
        });
        dialog.show();
        tick.run();
    }

    private void resendVerificationNow(final String email) {
        new AuthRepository().resendVerification(new AuthRepository.SimpleCallback() {
            @Override public void onOk() {
                host.ui.showSimpleMessage("Verification sent",
                        "A fresh verification link is on its way to " + email + ".");
            }
            @Override public void onBad(String message) {
                host.ui.showSimpleMessage("Couldn't resend", message);
            }
        });
    }

    private void checkVerificationNow(AlertDialog dialog, final String email) {
        new AuthRepository().checkEmailVerified(new AuthRepository.SimpleCallback() {
            @Override public void onOk() {
                dialog.dismiss();
                String uid = new AuthRepository().currentUid();
                if (uid == null) {
                    host.ui.showSimpleMessage("Email verified", "Log in with your password to continue.");
                    return;
                }
                host.currentUid = uid;
                CloudSync.directoryPut(host, uid, email);
                new AuthRepository().loadUser(uid, new AuthRepository.UserCallback() {
                    @Override public void onOk(com.campustrade.auth.model.User user) {
                        if (host.isDestroyed() || host.isFinishing()) return;
                        String name = user != null && user.getStuName() != null && !user.getStuName().isEmpty()
                                ? user.getStuName() : email;
                        CloudSync.saveSession(host, uid, email, name);
                        completeAuth(email, name);
                    }
                    @Override public void onBad(String message) {
                        host.ui.showSimpleMessage("Email verified", "Log in with your password to continue.");
                    }
                });
            }
            @Override public void onBad(String message) {
                host.ui.showSimpleMessage("Not verified yet", message);
            }
        });
    }

    private void showVerificationDialog(String email, String message) {
        if (host.isDestroyed() || host.isFinishing()) return;
        host.ui.dialog().setTitle("Verify your email")
                .setMessage(message)
                .setPositiveButton("Start verification timer",
                        (d, which) -> showLinkCountdown(email, 15 * 60, true))
                .setNegativeButton("Close", null)
                .show();
    }

    /** Suspended students can file an appeal before the login is cleared. */
    private void showSuspensionDialog(final String uid, final String email, String message) {
        LinearLayout form = new LinearLayout(host);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(host.ui.dp(20), host.ui.dp(8), host.ui.dp(20), 0);
        TextView copy = host.ui.bodyText(message + "\n\nTell the campus team why your account should be restored.",
                host.muted, 13);
        form.addView(copy);
        final EditText reason = host.ui.input("Appeal reason (at least 20 characters)", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        reason.setMinLines(4);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = host.ui.dp(12);
        form.addView(reason, params);
        AlertDialog dialog = host.ui.dialog()
                .setTitle("Account suspended")
                .setView(form)
                .setPositiveButton("Send appeal", null)
                .setNegativeButton("Close", (d, which) -> {
                    new AuthRepository().logout();
                    host.submitButton.setText("Log in");
                    validateAuth();
                })
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String text = host.ui.text(reason).trim();
            if (text.length() < 20) {
                reason.setError("Add a little more detail (20+ characters).");
                return;
            }
            new AuthRepository().submitAppeal(uid, email, host.currentStudentName, text,
                    new AuthRepository.SimpleCallback() {
                        @Override public void onOk() {
                            dialog.dismiss();
                            new AuthRepository().logout();
                            host.submitButton.setText("Log in");
                            validateAuth();
                            host.ui.showSimpleMessage("Appeal sent",
                                    "The campus team will review your appeal and contact " + email + ".");
                        }
                        @Override public void onBad(String error) {
                            host.ui.showSimpleMessage("Appeal didn't send",
                                    error + "\n\nEmail the campus team from another account if this keeps happening.");
                        }
                    });
        }));
        dialog.show();
    }

    // Switch between login and sign-up without rebuilding the screen.
    public void setMode(boolean signup) {
        host.signupMode = signup;
        host.heroTitle.setText(signup ? "Join Eduvos" : "Welcome back");
        host.heroSubtitle.setText(signup ? "Create your account with your @vossie.net email and student number." : "Log in with your email and password.");
        host.submitButton.setText(signup ? "Create account" : "Log in");
        host.passwordInput.setHint(signup ? "Strong password" : "Enter your password");
        host.switchPrompt.setText(signup ? "Already have an account? Log in" : "New to Eduvos? Sign up");
        host.passwordStrengthText.setVisibility(signup ? View.VISIBLE : View.GONE);
        host.signupFields.setVisibility(signup ? View.VISIBLE : View.GONE);
        host.studentNumberGroup.setVisibility(signup ? View.VISIBLE : View.GONE);
        host.confirmPasswordGroup.setVisibility(signup ? View.VISIBLE : View.GONE);
        // The same acceptance gate applies to logging in and signing up.
        host.signupTerms.setVisibility(View.VISIBLE);
        host.loginOptions.setVisibility(signup ? View.GONE : View.VISIBLE);
        host.adminGroup.setVisibility(View.GONE);
        host.authEmailGroup.setVisibility(View.VISIBLE);
        host.authPasswordGroup.setVisibility(View.VISIBLE);
        host.authNotice.setVisibility(View.VISIBLE);
        if (host.authFooter != null) host.authFooter.setVisibility(View.VISIBLE);
        host.submitButton.setVisibility(View.VISIBLE);
        host.switchPrompt.setVisibility(View.VISIBLE);
        host.adminPasscodeInput.setText("");
        host.adminPasscodeInput.setError(null);
        host.ui.styleTab(host.loginTab, !signup);
        host.ui.styleTab(host.signupTab, signup);
        host.ui.styleTab(host.adminTab, false);
        host.ui.updatePasswordStrength();
        validateAuth();
    }

    // Show the passcode form. Release builds keep admin locked.
    public void setAdminMode() {
        host.signupMode = false;
        host.heroTitle.setText("Admin access");
        host.heroSubtitle.setText("Enter the moderator passcode to open campus tools.");
        host.adminGroup.setVisibility(View.VISIBLE);
        host.signupFields.setVisibility(View.GONE);
        host.authEmailGroup.setVisibility(View.GONE);
        host.studentNumberGroup.setVisibility(View.GONE);
        host.authPasswordGroup.setVisibility(View.GONE);
        host.confirmPasswordGroup.setVisibility(View.GONE);
        host.loginOptions.setVisibility(View.GONE);
        host.signupTerms.setVisibility(View.GONE);
        host.submitButton.setVisibility(View.GONE);
        host.switchPrompt.setVisibility(View.GONE);
        host.authNotice.setVisibility(View.GONE);
        if (host.authFooter != null) host.authFooter.setVisibility(View.GONE);
        host.ui.styleTab(host.loginTab, false);
        host.ui.styleTab(host.signupTab, false);
        host.ui.styleTab(host.adminTab, true);
        host.adminPasscodeInput.setError(null);
        host.adminPasscodeInput.requestFocus();
        host.ui.updatePasswordStrength();
        validateAuth();
    }

    // Check the form, then send the login or sign-up request.
    public void submitAuth() {
        if (host.authSubmitting) return;
        if (!validateAuthWithErrors()) {
            validateAuth();
            return;
        }

        host.submitButton.setEnabled(false);
        host.authSubmitting = true;
        host.submitButton.setText(host.signupMode ? "Creating account..." : "Logging in...");
        final String loginEmail = host.normalizeEmail(host.ui.text(host.emailInput));
        final String signupName = host.signupMode ? host.ui.text(host.fullNameInput).trim() : "";
        final boolean creating = host.signupMode;
        final String student = host.ui.text(host.studentNumberInput).trim();
        final boolean keep = host.keepLoggedInCheck.isChecked();
        final char[] password = host.ui.text(host.passwordInput).toCharArray();
        final String passwordText = new String(password);
        java.util.Arrays.fill(password, '\0');
        final AuthRepository repo = new AuthRepository();
        AuthRepository.AuthCallback cb = new AuthRepository.AuthCallback() {
            @Override public void onOk(String uid) {
                if (host.isDestroyed() || host.isFinishing()) return;
                if (creating) {
                    // Never open the app before the email is verified.
                    new AuthRepository().logout();
                    host.authSubmitting = false;
                    host.submitButton.setText("Create account");
                    validateAuth();
                    host.ui.showSimpleMessage("Verify your email",
                            "Check your inbox for the verification link, then log in.");
                    return;
                }
                host.authSubmitting = false;
                com.campustrade.auth.data.SecurePrefs.get(host, host.PREFS).edit().putBoolean("keep_logged_in", keep).apply();
                host.currentUid = uid;
                CloudSync.directoryPut(host, uid, loginEmail);
                repo.loadUser(uid, new AuthRepository.UserCallback() {
                    @Override public void onOk(com.campustrade.auth.model.User user) {
                        if (host.isDestroyed() || host.isFinishing()) return;
                        if (user == null) {
                            new AuthRepository().logout();
                            host.submitButton.setText("Log in");
                            validateAuth();
                            host.ui.showSimpleMessage("We couldn't find your profile",
                                    "Your sign-in works, but your student profile is missing. "
                                            + "Ask a campus moderator to fix it for you.");
                            return;
                        }
                        host.adminAccount = user.getIsAdmin();
                        String accountStatus = user.getAccountStatus();
                        if ("suspended".equalsIgnoreCase(accountStatus) || "deactivated".equalsIgnoreCase(accountStatus)
                                || "deleted".equalsIgnoreCase(accountStatus)) {
                            host.authSubmitting = false;
                            host.submitButton.setText("Log in");
                            validateAuth();
                            showSuspensionDialog(uid, loginEmail,
                                    "This account isn't active right now. You can send an appeal to the campus team.");
                            return;
                        }
                        new AuthRepository().touchLastActive(uid);
                        String savedNumber = user.getStudentNumber();
                        if (!student.isEmpty() && savedNumber != null && !savedNumber.isEmpty()
                                && !savedNumber.equalsIgnoreCase(student)) {
                            new AuthRepository().logout();
                            host.submitButton.setText("Log in");
                            validateAuth();
                            host.ui.showSimpleMessage("Log in",
                                    "That student number doesn't match this email.");
                            return;
                        }
                        String name = (user.getStuName() != null && !user.getStuName().isEmpty())
                                ? user.getStuName()
                                : com.campustrade.auth.data.SecurePrefs.get(host, host.PREFS + "_profiles")
                                        .getString("full_name:" + loginEmail, loginEmail);
                        android.content.SharedPreferences.Editor profileEdit =
                                com.campustrade.auth.data.SecurePrefs.get(host, host.PREFS + "_profiles").edit();
                        if (user.getCourse() != null && !user.getCourse().isEmpty()) {
                            profileEdit.putString("course:" + loginEmail, user.getCourse());
                        }
                        if (user.getYear() != null && !user.getYear().isEmpty()) {
                            profileEdit.putString("year:" + loginEmail, user.getYear());
                        }
                        if (user.getProfileImage() != null && !user.getProfileImage().isEmpty()) {
                            profileEdit.putString("profile_image:" + loginEmail, user.getProfileImage());
                        }
                        profileEdit.apply();
                        CloudSync.saveSession(host, uid, loginEmail, name);
                        // Refresh the public profile for older accounts.
                        repo.syncPublicProfile(uid, user, new AuthRepository.SimpleCallback() {
                            @Override public void onOk() {
                                if (host.isDestroyed() || host.isFinishing()) return;
                                completeAuth(loginEmail, name);
                            }
                            @Override public void onBad(String message) {
                                if (host.isDestroyed() || host.isFinishing()) return;
                                Toast.makeText(host,
                                        "You're in, but your public profile didn't sync: " + message,
                                        Toast.LENGTH_LONG).show();
                                completeAuth(loginEmail, name);
                            }
                        });
                    }
                    @Override public void onBad(String message) {
                        if (host.isDestroyed() || host.isFinishing()) return;
                        String name = com.campustrade.auth.data.SecurePrefs.get(host, host.PREFS + "_profiles")
                                .getString("full_name:" + loginEmail, loginEmail);
                        CloudSync.saveSession(host, uid, loginEmail, name);
                        completeAuth(loginEmail, name);
                    }
                });
            }
            @Override public void onVerify(String email, String message) {
                if (host.isDestroyed() || host.isFinishing()) return;
                host.authSubmitting = false;
                host.submitButton.setText(host.signupMode ? "Create account" : "Log in");
                validateAuth();
                showVerificationDialog(email, message);
            }
            @Override public void onBad(String message) {
                if (host.isDestroyed() || host.isFinishing()) return;
                host.authSubmitting = false;
                host.submitButton.setText(host.signupMode ? "Create account" : "Log in");
                validateAuth();
                host.ui.showSimpleMessage("Account", message);
            }
        };
        if (creating) {
            repo.register(signupName.isEmpty() ? loginEmail : signupName, loginEmail, student, passwordText, host.selectedCourse, host.selectedYear, cb);
        } else {
            host.selectedCourse = "";
            host.selectedYear = "";
            repo.login(loginEmail, passwordText, cb);
        }
    }

    // Save the session and open the right screen.
    public void completeAuth(String email, String fullName) {
        boolean strictAdmin = Constants.STRICT_ADMIN_EMAIL.equalsIgnoreCase(email);
        host.adminAccess = strictAdmin;
        host.adminOnlySession = strictAdmin;
        if (strictAdmin) host.adminAccount = true;
        host.currentStudentEmail = email;
        host.currentStudentName = fullName.trim();
        // Keep a local record that this account accepted the current terms
        // before entering the app. The checkbox is required on every login.
        com.campustrade.auth.data.SecurePrefs.get(host, host.PREFS + "_terms").edit()
                .putLong("accepted_at:" + email, System.currentTimeMillis())
                .putString("version:" + email, "1.5.2")
                .apply();
        // Names are saved per email, so they survive logout.
        android.content.SharedPreferences.Editor profileEdit =
                com.campustrade.auth.data.SecurePrefs.get(host, host.PREFS + "_profiles").edit()
                .putString("full_name:" + email, host.currentStudentName)
                .putLong("last_active:" + email, System.currentTimeMillis());
        if (!host.selectedCourse.isEmpty()) {
            profileEdit.putString("course:" + email, host.selectedCourse);
        }
        if (!host.selectedYear.isEmpty()) {
            profileEdit.putString("year:" + email, host.selectedYear);
        }
        profileEdit.apply();
        com.campustrade.auth.data.SecurePrefs.get(host, host.PREFS).edit()
                .putString("student_email", email)
                .putString("student_name", host.currentStudentName)
                .putBoolean("signed_in", true).apply();
        if (strictAdmin) CloudSync.registerAdminAlias(host, host.currentUid);
        // Apply the saved theme before the first screen appears.
        host.applyTheme();
        host.applyWindowTheme();
        host.showAppScreen(host.getIntent().getBooleanExtra("open_notifications", false) ? "Notifications"
                : host.getIntent().getBooleanExtra("open_inbox", false) ? "Inbox" : "Market");
        host.getIntent().removeExtra("open_notifications");
        host.getIntent().removeExtra("open_inbox");
        host.startChatListeners();
        host.startPresenceTracking();
        if (!strictAdmin) {
            host.market.syncFavourites();
            host.safety.sync(host.currentStudentEmail, host.currentUid);
            host.safety.syncContentSanctions(host.currentStudentEmail, host.currentUid);
            Tutorial.showOnboarding(host);
        }
        int unread = host.inbox.unreadMessages();
        if (unread > 0) host.notification.showCampusNotification("New messages", "You have " + unread + " unread message(s).", "messages");
    }

    // Show field help before submit. Keep the main button red
    // even when the form is incomplete.
    void updateInlineHints(TextView emailHint, TextView confirmHint) {
        String email = host.ui.text(host.emailInput).trim();
        if (email.isEmpty()) {
            emailHint.setVisibility(View.GONE);
        } else {
            boolean ok = isStudentEmail(email);
            emailHint.setVisibility(View.VISIBLE);
            emailHint.setText(ok ? "Campus email confirmed." : "Use your name@vossie.net campus email.");
            emailHint.setTextColor(ok ? host.readableInk(host.green)
                    : host.nightActive ? Color.rgb(255, 125, 146) : Color.rgb(190, 18, 60));
        }
        String password = host.ui.text(host.passwordInput);
        String confirm = host.ui.text(host.confirmPasswordInput);
        if (!host.signupMode || confirm.isEmpty()) {
            confirmHint.setVisibility(View.GONE);
        } else {
            boolean match = password.equals(confirm);
            confirmHint.setVisibility(View.VISIBLE);
            confirmHint.setText(match ? "Passwords match." : "Passwords don't match yet.");
            confirmHint.setTextColor(match ? host.readableInk(host.green)
                    : host.nightActive ? Color.rgb(255, 125, 146) : Color.rgb(190, 18, 60));
        }
    }

    // Quick form check for the submit button.
    public boolean validateAuth() {
        boolean valid = isStudentEmail(host.ui.text(host.emailInput))
                && !host.ui.text(host.passwordInput).isEmpty()
                && host.termsCheck.isChecked();
        if (host.signupMode) {
            String password = host.ui.text(host.passwordInput);
            valid = valid
                    && isValidStudentNumber(host.ui.text(host.studentNumberInput))
                    && host.ui.text(host.fullNameInput).trim().length() >= 2 && !isValidStudentNumber(host.ui.text(host.fullNameInput))
                    && host.ui.isStrongPassword(password)
                    && password.equals(host.ui.text(host.confirmPasswordInput));
        }
        // Keep the button red. Show errors inline
        // instead of disabling it.
        host.submitButton.setEnabled(!host.authSubmitting);
        host.submitButton.setAlpha(1f);
        return valid;
    }

    // Same check, but show the error on the field.
    public boolean validateAuthWithErrors() {
        clearAuthErrors();
        boolean valid = true;
        if (!isStudentEmail(host.ui.text(host.emailInput))) {
            host.emailInput.setError("Use your @vossie.net email");
            valid = false;
        }
        if (host.ui.text(host.passwordInput).isEmpty()) {
            host.passwordInput.setError("Password is required");
            valid = false;
        }
        if (host.signupMode) {
            if (!isValidStudentNumber(host.ui.text(host.studentNumberInput))) {
                host.studentNumberInput.setError("Use your Eduvos student number");
                valid = false;
            }
            if (host.ui.text(host.fullNameInput).trim().length() < 2) {
                host.fullNameInput.setError("Enter your full name");
                valid = false;
            }
            if (!host.ui.isStrongPassword(host.ui.text(host.passwordInput))) {
                host.passwordInput.setError(host.ui.passwordComplexityMessage(host.ui.text(host.passwordInput)));
                valid = false;
            }
            if (!host.ui.text(host.passwordInput).equals(host.ui.text(host.confirmPasswordInput))) {
                host.confirmPasswordInput.setError("Passwords don't match");
                valid = false;
            }
        }
        if (!host.termsCheck.isChecked()) {
            Toast.makeText(host, "Tick the Terms & Conditions box first.", Toast.LENGTH_SHORT).show();
            valid = false;
        }
        return valid;
    }

    // Only accept a real @vossie.net campus email.
    boolean isStudentEmail(String email) {
        String value = host.normalizeEmail(email);
        return Patterns.EMAIL_ADDRESS.matcher(value).matches()
                && value.endsWith("@" + host.STUDENT_EMAIL_DOMAIN);
    }

    // Accept both student-number formats.
    boolean isValidStudentNumber(String studentNumber) {
        String value = studentNumber.trim().toLowerCase();
        boolean connectStyleId = value.matches("con-[0-9]+-[a-z0-9]+");
        boolean eduvosStudentId = value.matches("eduv[0-9]{6,12}");
        return connectStyleId || eduvosStudentId;
    }

    // Clear old errors when the student changes mode or types.
    public void clearAuthErrors() {
        host.fullNameInput.setError(null);
        host.emailInput.setError(null);
        host.studentNumberInput.setError(null);
        host.passwordInput.setError(null);
        host.confirmPasswordInput.setError(null);
    }
}