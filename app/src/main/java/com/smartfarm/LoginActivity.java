package com.smartfarm;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;

import java.util.Objects;


public class LoginActivity extends AppCompatActivity {

    MaterialButton btnLogin;
    EditText etUsername, etPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_activity);
        Objects.requireNonNull(getSupportActionBar()).hide();

        btnLogin = findViewById(R.id.btnLogin);
        btnLogin.setOnClickListener(this::loginHandler);

    }
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if ((keyCode == KeyEvent.KEYCODE_BACK))
        {
            return false;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void loginHandler(View view) {
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        String username = etUsername.getText().toString();
        String password = etPassword.getText().toString();

        if (username.equals("") || password.equals("")){
            Toast.makeText(this.getApplicationContext(), "Vui lòng điền đầy đủ thông tin!"
                    , Toast.LENGTH_SHORT).show();
            return;
        }

        if (username.equals("admin") && password.equals("123456")){
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            this.finish();
        }else {
            Toast.makeText(this.getApplicationContext(), "Tài khoản hoặc mật khẩu không đúng!"
                    , Toast.LENGTH_SHORT).show();
            return;
        }

    }

}