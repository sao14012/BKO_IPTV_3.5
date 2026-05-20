package com.example.bko_iptv_android_tv_35;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ListView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.drm.LocalMediaDrmCallback;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private StyledPlayerView playerView;
    private ExoPlayer player;
    private ListView listaCanales;
    private final String M3U_URL = "https://raw.githubusercontent.com/sao14012/logos/refs/heads/main/pruebaeltrece.m3u";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        playerView = findViewById(R.id.player_view);
        listaCanales = findViewById(R.id.lista_canales);

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                // Si falla, te va a decir la CAUSA exacta (ej: DRM_ERROR, CODEC_ERROR)
                Throwable cause = error.getCause();
                String msg = cause != null ? cause.getMessage() : error.getErrorCodeName();
                Toast.makeText(MainActivity.this, "Error: " + msg, Toast.LENGTH_LONG).show();
            }
        });

        new Thread(this::descargarYProcesarLista).start();
    }

    private void descargarYProcesarLista() {
        try {
            URL url = new URL(M3U_URL);
            BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
            String line, videoUrl = "", licenseKey = "";

            while ((line = reader.readLine()) != null) {
                if (line.contains("license_key")) {
                    licenseKey = line.split("=")[1].trim();
                } else if (line.startsWith("http")) {
                    videoUrl = line.trim();
                }
            }
            reader.close();

            final String finalUrl = videoUrl;
            final String finalKey = licenseKey;

            new Handler(Looper.getMainLooper()).post(() -> configurarYReproducir(finalUrl, finalKey));

        } catch (Exception e) {
            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(this, "Error de red: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }

    private void configurarYReproducir(String url, String key) {
        if (url.isEmpty() || !key.contains(":")) return;

        // Configuración de Red idéntica a navegador
        DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory();
        dataSourceFactory.setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36");

        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", "https://player.sensa.com.ar/");
        headers.put("Origin", "https://player.sensa.com.ar");
        dataSourceFactory.setDefaultRequestProperties(headers);

        // Formateamos el JSON de Clearkey de manera limpia
        String[] parts = key.split(":");
        String licenseJson = "{\"keys\":[{\"kty\":\"oct\",\"k\":\"" + convertHexToBase64Url(parts[1]) + "\",\"kid\":\"" + convertHexToBase64Url(parts[0]) + "\"}]}";
        byte[] licenseBytes = licenseJson.getBytes(StandardCharsets.UTF_8);

        // --- EL CAMBIO CLAVE ---
        // En lugar de pasar una URI de texto, usamos un Callback local que inyecta los bytes directamente en el motor DRM
        LocalMediaDrmCallback localDrmCallback = new LocalMediaDrmCallback(licenseBytes);

        MediaItem mediaItem = new MediaItem.Builder()
                .setUri(Uri.parse(url))
                .setDrmConfiguration(new MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID)
                        .setMultiSession(true)
                        .build())
                .build();

        // Creamos la fuente usando el inyector local de llaves
        MediaSource mediaSource = new DashMediaSource.Factory(dataSourceFactory)
                .setDrmSessionManagerProvider(unusedMediaItem ->
                        new com.google.android.exoplayer2.drm.DefaultDrmSessionManager.Builder()
                                .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, com.google.android.exoplayer2.drm.FrameworkMediaDrm.DEFAULT_PROVIDER)
                                .build(localDrmCallback))
                .createMediaSource(mediaItem);

        player.setMediaSource(mediaSource);
        player.prepare();
        player.play();
    }

    private String convertHexToBase64Url(String hex) {
        byte[] data = new byte[hex.length() / 2];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP | android.util.Base64.URL_SAFE | android.util.Base64.NO_PADDING);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) { player.release(); player = null; }
    }
}