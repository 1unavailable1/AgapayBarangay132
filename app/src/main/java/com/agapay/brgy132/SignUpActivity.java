package com.agapay.brgy132;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class SignUpActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sign_up);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainSignUp), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        dbHelper = new DatabaseHelper(this);

        TextView btnBackLogin = findViewById(R.id.btnBackLogin);
        TextView tvLoginLink = findViewById(R.id.tvLoginLink);
        EditText etFirstName = findViewById(R.id.etFirstName);
        EditText etLastName = findViewById(R.id.etLastName);
        EditText etAddress = findViewById(R.id.etAddress);
        EditText etPhone = findViewById(R.id.etPhone);
        EditText etEmail = findViewById(R.id.etSignUpEmail);
        EditText etPassword = findViewById(R.id.etSignUpPassword);
        Button btnCompleteRegistration = findViewById(R.id.btnCompleteRegistration);

        attachPasswordToggle(etPassword);

        btnBackLogin.setOnClickListener(v -> {
            startActivity(new Intent(SignUpActivity.this, LoginActivity.class));
            finish();
        });

        tvLoginLink.setOnClickListener(v -> {
            startActivity(new Intent(SignUpActivity.this, LoginActivity.class));
            finish();
        });

        btnCompleteRegistration.setOnClickListener(v -> {
            String firstName = etFirstName.getText().toString().trim();
            String lastName = etLastName.getText().toString().trim();
            String address = etAddress.getText().toString().trim();
            String phone = etPhone.getText().toString().trim();
            String email = etEmail.getText().toString().trim().toLowerCase();
            String password = etPassword.getText().toString().trim();

            if (firstName.isEmpty() || lastName.isEmpty() || address.isEmpty()
                    || phone.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill out all fields.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!DatabaseHelper.isValidPhPhoneNumber(phone)) {
                Toast.makeText(this, "Invalid contact number: Use 09XXXXXXXXX or +63 9XX XXX XXXX.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!DatabaseHelper.isValidGmail(email)) {
                Toast.makeText(this, "Invalid email: Residents must use a valid @gmail.com address.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (password.length() < 6) {
                Toast.makeText(this, "Password must be at least 6 characters.", Toast.LENGTH_SHORT).show();
                return;
            }

            boolean saved = dbHelper.registerResident(firstName, lastName, phone, email, password);
            if (saved) {
                String fullName = firstName + " " + lastName;
                CloudSyncManager syncManager = new CloudSyncManager(this);
                syncManager.syncResidentToCloud(
                        email,
                        fullName,
                        fullName, // familyHead
                        address,  // street
                        phone,    // contact
                        password,
                        "",       // ageGroup
                        "",       // category
                        1,        // totalMembers
                        0,        // seniors
                        0,        // pwd
                        0,        // infants
                        0         // roomId
                );

                Toast.makeText(this, "Resident account registered! Please log in.", Toast.LENGTH_LONG).show();
                startActivity(new Intent(SignUpActivity.this, LoginActivity.class));
                finish();
            } else {
                Toast.makeText(this, "That email is already registered.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void attachPasswordToggle(EditText passwordField) {
        ViewGroup parent = (ViewGroup) passwordField.getParent();
        int index = parent.indexOfChild(passwordField);
        ViewGroup.LayoutParams origParams = passwordField.getLayoutParams();

        parent.removeView(passwordField);

        FrameLayout wrapper = new FrameLayout(this);
        wrapper.setLayoutParams(origParams);

        FrameLayout.LayoutParams editParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        passwordField.setLayoutParams(editParams);
        passwordField.setPadding(
                passwordField.getPaddingLeft(),
                passwordField.getPaddingTop(),
                passwordField.getPaddingRight() + 140,
                passwordField.getPaddingBottom()
        );

        TextView tvToggle = new TextView(this);
        tvToggle.setText("Show");
        tvToggle.setTextColor(Color.parseColor("#138A4C"));
        tvToggle.setTextSize(13f);
        tvToggle.setTypeface(null, Typeface.BOLD);
        tvToggle.setPadding(24, 16, 36, 16);

        FrameLayout.LayoutParams toggleParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.CENTER_VERTICAL
        );
        tvToggle.setLayoutParams(toggleParams);

        final boolean[] isVisible = {false};
        tvToggle.setOnClickListener(v -> {
            isVisible[0] = !isVisible[0];
            Typeface currentTypeface = passwordField.getTypeface();
            if (isVisible[0]) {
                passwordField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                tvToggle.setText("Hide");
            } else {
                passwordField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                tvToggle.setText("Show");
            }
            passwordField.setTypeface(currentTypeface);
            passwordField.setSelection(passwordField.getText().length());
        });

        wrapper.addView(passwordField);
        wrapper.addView(tvToggle);
        parent.addView(wrapper, index);
    }
}