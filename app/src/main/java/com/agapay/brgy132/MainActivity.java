package com.agapay.brgy132;

import android.content.Intent;
import android.graphics.Paint;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Underline "Barangay 132"
        TextView tvBrgyUnderline = findViewById(R.id.tvBrgyUnderline);
        tvBrgyUnderline.setPaintFlags(tvBrgyUnderline.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);

        // Hotline button
        Button btnHotline = findViewById(R.id.btnHotline);
        btnHotline.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Barangay 132 Emergency Hotlines")
                .setMessage("• Brgy. 132 Desk: (02) 8361-XXXX\n• Caloocan DRRMO: (02) 8288-8811")
                .setPositiveButton("Close", null)
                .show());

        // Get Started & Log In button
        Button btnGetStartedLogin = findViewById(R.id.btnGetStartedLogin);
        btnGetStartedLogin.setOnClickListener(v -> {
            // We will create LoginActivity in the next step!
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            // startActivity(new Intent(MainActivity.this, LoginActivity.class));
        });

        // Create New Account button
        Button btnCreateAccount = findViewById(R.id.btnCreateAccount);
        btnCreateAccount.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, SignUpActivity.class));
        });
    }
}