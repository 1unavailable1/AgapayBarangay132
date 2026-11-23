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

public class LoginActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        CloudSyncManager syncManager = new CloudSyncManager(this);
        syncManager.startLiveSync(null);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainLogin), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        dbHelper = new DatabaseHelper(this);

        TextView btnBack = findViewById(R.id.btnBack);
        EditText etEmail = findViewById(R.id.etEmail);
        EditText etPassword = findViewById(R.id.etPassword);
        Button btnLoginRoute = findViewById(R.id.btnLoginRoute);
        TextView tvSignUpLink = findViewById(R.id.tvSignUpLink);

        attachPasswordToggle(etPassword);

        btnBack.setOnClickListener(v -> finish());

        btnLoginRoute.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter your email and password.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!DatabaseHelper.isAuthorizedOfficialEmail(email) && !DatabaseHelper.isValidGmail(email)) {
                Toast.makeText(this, "Invalid credentials: Please enter a valid @gmail.com address.", Toast.LENGTH_SHORT).show();
                return;
            }

            String role = dbHelper.authenticateUser(email, password);
            if (role == null) {
                Toast.makeText(this, "Invalid credentials: Incorrect email or password.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (role.equals("OFFICIAL")) {
                Intent intent = new Intent(LoginActivity.this, OfficialDashboardActivity.class);
                intent.putExtra("USER_EMAIL", email);
                startActivity(intent);
                finish();
            } else {
                Intent intent = new Intent(LoginActivity.this, ResidentDashboardActivity.class);
                intent.putExtra("USER_EMAIL", email);
                startActivity(intent);
                finish();
            }
        });

        tvSignUpLink.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, SignUpActivity.class));
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