package com.example.tfg_dmae;

import static android.content.Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class UserManual extends AppCompatActivity {

    Context context;
    private WebView manualContent;
    private Window window;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = getApplicationContext();
        setContentView(R.layout.activity_user_manual);

        window = getWindow();
        //Prevent screenshots or screen recording
        //window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        manualContent = findViewById(R.id.userManual);
        manualContent.getSettings().setJavaScriptEnabled(true); //Habilitar JavaScript para HTML
        manualContent.setWebViewClient(new WebViewClient()); //Necesario para abrir en la aplicación

        try {
            manualContent.loadUrl("file:///android_asset/Manual_De_Usuario.html");
        } catch (Exception e) {
            Toast.makeText(context, "Ha ocurrido un error al cargar el manual de usuario", Toast.LENGTH_SHORT).show();
            finish();
        }

    }

    public void backToMain(View view) {
        finish();
    }
}