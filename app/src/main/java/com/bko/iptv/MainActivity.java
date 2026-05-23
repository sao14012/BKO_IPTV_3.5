package com.bko.iptv;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.MediaMetadata;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.drm.LocalMediaDrmCallback;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.util.MimeTypes;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private StyledPlayerView playerView;
    private StyledPlayerView playerViewMini;
    private View contenedorMiniPlayer;
    private ListView listViewCanales;
    private ListView listViewGrupos;
    private android.widget.Button btnVolverGrupos;
    private ExoPlayer player;
    private ExoPlayer playerMini;
    private View imagenSplash;
    private LinearLayout contenedorMenus;
    private TextView textNombreListaCabecera;
    private EditText inputBuscadorTiempoReal;
    private View contenedorConfiguracion;
    private ListView listViewConfiguracion;
    private String nombreListaActualEnUso = "BKO IPTV";
    private String claveAdultos = "0000";

    private static class CanalEstructura {
        String urlStream;
        String licenseKey;
        String nombreCanal;
        String nombreOrdenado;
        String grupoCanal;
        String urlLogo;
        String tipoMime;
    }

    private List<CanalEstructura> listaGlobalCanales = new ArrayList<>();
    private List<CanalEstructura> listaFiltradaCanales = new ArrayList<>();

    private List<String> listaDeGruposVisibles = new ArrayList<>();
    private String grupoSeleccionadoActual = "[ TODOS LOS CANALES ]";

    private static final String PREFS_NAME = "BkoPrefsPro";
    private static final String KEY_LISTAS_JSON = "listas_iptv_pro_json";
    private static final String KEY_ULTIMA_URL = "ultima_url_sintonizada";
    private static final String KEY_FAVORITOS_SET = "favoritos_canales_urls";
    private static final String KEY_GRUPO_PREDETERMINADO = "grupo_predeterminado_iptv";
    private static final String KEY_ULTIMO_CANAL_SINTONIZADO = "ultimo_canal_sintonizado_url";
    private static final String KEY_CLAVE_ADULTOS = "clave_parental_adultos";
    private static final String KEY_MOSTRAR_ADULTOS = "mostrar_contenido_adulto";
    private boolean mostrarContenidoAdulto = false;

    private List<String> nombresDeListasGuardadas = new ArrayList<>();
    private List<String> urlsDeListasGuardadas = new ArrayList<>();
    private Set<String> setDeCanalesFavoritos = new HashSet<>();

    private final Handler reintentoHandler = new Handler(Looper.getMainLooper());
    private String urlListaActualEnUso = "";
    private CanalEstructura canalActualReproduciendo;
    private CanalEstructura canalMiniReproduciendo;
    private GestureDetector gestureDetector;
    private GestureDetector miniPlayerGestureDetector;
    private boolean menuParaPantallaChica = false;
    private int contadorErroresReproduccion = 0;

    private long tiempoPresionadoOk = 0;
    private boolean yaSeEjecutoLargoOk = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        playerView = findViewById(R.id.player_view_firme);
        playerViewMini = findViewById(R.id.player_view_mini);
        contenedorMiniPlayer = findViewById(R.id.contenedor_mini_player);
        listViewCanales = findViewById(R.id.list_view_canales);
        listViewGrupos = findViewById(R.id.list_view_grupos);
        imagenSplash = findViewById(R.id.imagen_splash);

        contenedorMenus = findViewById(R.id.contenedor_menus);
        textNombreListaCabecera = findViewById(R.id.text_nombre_lista_cabecera);
        inputBuscadorTiempoReal = findViewById(R.id.input_buscador_canales);
        contenedorConfiguracion = findViewById(R.id.contenedor_configuracion);
        listViewConfiguracion = findViewById(R.id.list_view_configuracion);
        cargarOpcionesConfiguracion();

        // btnVolverGrupos ya no es necesario en el diseño de dos columnas

        if (inputBuscadorTiempoReal != null) {
            inputBuscadorTiempoReal.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEARCH || 
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    
                    // 1. Cerrar teclado inmediatamente
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    }
                    
                    // 2. Forzar que el foco salga del buscador
                    v.clearFocus();
                    
                    // 3. Obligar al foco a ir a la lista de CANALES (la de la derecha)
                    if (listViewCanales != null) {
                        listViewCanales.requestFocus();
                        if (!listaFiltradaCanales.isEmpty()) {
                            listViewCanales.setSelection(0);
                        }
                    }
                    return true;
                }
                return false;
            });
        }
        playerView.setKeepScreenOn(true);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        setDeCanalesFavoritos = new HashSet<>(prefs.getStringSet(KEY_FAVORITOS_SET, new HashSet<>()));
        claveAdultos = prefs.getString(KEY_CLAVE_ADULTOS, "0000");
        mostrarContenidoAdulto = prefs.getBoolean(KEY_MOSTRAR_ADULTOS, false);

        // Efecto de Zoom suave para el Splash
        if (imagenSplash != null) {
            imagenSplash.setScaleX(1.0f);
            imagenSplash.setScaleY(1.0f);
            imagenSplash.animate()
                    .scaleX(1.15f)
                    .scaleY(1.15f)
                    .setDuration(6000)
                    .start();
        }

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (imagenSplash != null) {
                imagenSplash.animate()
                        .alpha(0f)
                        .setDuration(800)
                        .withEndAction(() -> imagenSplash.setVisibility(View.GONE))
                        .start();
            }
        }, 3000);

        try {
            DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                    .setBufferDurationsMs(15000, 50000, 2500, 5000).build();

            player = new ExoPlayer.Builder(this).setLoadControl(loadControl).build();
            playerView.setPlayer(player);
            playerView.setUseController(false);
            player.setRepeatMode(Player.REPEAT_MODE_OFF);

            playerMini = new ExoPlayer.Builder(this).setLoadControl(loadControl).build();
            playerViewMini.setPlayer(playerMini);
            playerViewMini.setUseController(false);
            playerMini.setVolume(0f); // Pantalla chica sin sonido
            playerMini.setRepeatMode(Player.REPEAT_MODE_OFF);

            playerMini.addListener(new Player.Listener() {
                @Override
                public void onPlayerError(PlaybackException error) {
                    if (playerMini != null) {
                        playerMini.prepare();
                        playerMini.play();
                    }
                }
            });

            player.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (playbackState == Player.STATE_READY) {
                        contadorErroresReproduccion = 0;
                    }
                }

                @Override
                public void onPlayerError(PlaybackException error) {
                    contadorErroresReproduccion++;
                    
                    if (contadorErroresReproduccion >= 3) {
                        reintentoHandler.removeCallbacksAndMessages(null);
                        mostrarDialogoCanalCaido();
                        contadorErroresReproduccion = 0;
                        return;
                    }

                    Throwable cause = error.getCause();
                    String msg = cause != null ? cause.getMessage() : error.getErrorCodeName();
                    Toast.makeText(MainActivity.this, "Fallo de señal (" + msg + "). Reintento " + contadorErroresReproduccion + "/3", Toast.LENGTH_SHORT).show();

                    if (player != null) {
                        reintentoHandler.removeCallbacksAndMessages(null);
                        reintentoHandler.postDelayed(() -> {
                            if (player != null) {
                                player.prepare();
                                player.play();
                            }
                        }, 4000);
                    }
                }

                @Override
                public void onMediaItemTransition(MediaItem mediaItem, int reason) {
                    if (mediaItem != null && mediaItem.mediaMetadata.title != null) {
                        Toast.makeText(MainActivity.this, "Cargando: " + mediaItem.mediaMetadata.title, Toast.LENGTH_SHORT).show();
                    }
                    reintentoHandler.removeCallbacksAndMessages(null);
                }
            });

            playerView.setKeepScreenOn(true);

            gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDown(MotionEvent e) {
                    return true;
                }

                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                    if (Math.abs(velocityY) > Math.abs(velocityX)) {
                        if (velocityY < -500) { // Swipe Up
                            cambiarCanalSiguiente(player);
                            return true;
                        } else if (velocityY > 500) { // Swipe Down
                            cambiarCanalAnterior(player);
                            return true;
                        }
                    }
                    return false;
                }

                @Override
                public boolean onSingleTapConfirmed(MotionEvent e) {
                    menuParaPantallaChica = false;
                    alternarMenuCanales();
                    return true;
                }

                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    if (canalActualReproduciendo != null) {
                        // 1. Mover canal actual a la pantalla chica (silenciado)
                        reproducirCanalEstable(canalActualReproduciendo, playerMini);
                        
                        // 2. Abrir el menú para elegir el nuevo canal (para la grande)
                        menuParaPantallaChica = false;
                        alternarMenuCanales();
                        
                        Toast.makeText(MainActivity.this, "📺 Multiview: Seleccione nuevo canal", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }

                @Override
                public void onLongPress(MotionEvent e) {
                    alternarMenuConfiguracion();
                }
            });

            miniPlayerGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDown(MotionEvent e) {
                    return true;
                }

                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    cerrarMiniPlayer();
                    return true;
                }

                @Override
                public boolean onSingleTapConfirmed(MotionEvent e) {
                    menuParaPantallaChica = true;
                    alternarMenuCanales();
                    Toast.makeText(MainActivity.this, "📺 Cambiando canal de pantalla chica", Toast.LENGTH_SHORT).show();
                    return true;
                }

                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                    if (Math.abs(velocityY) > Math.abs(velocityX)) {
                        if (velocityY < -500) { // Swipe Up
                            cambiarCanalSiguiente(playerMini);
                            return true;
                        } else if (velocityY > 500) { // Swipe Down
                            cambiarCanalAnterior(playerMini);
                            return true;
                        }
                    }
                    return false;
                }
            });

            if (contenedorMiniPlayer != null) {
                contenedorMiniPlayer.setOnTouchListener((v, event) -> {
                    miniPlayerGestureDetector.onTouchEvent(event);
                    return true;
                });
            }

            playerView.setOnTouchListener((v, event) -> {
                gestureDetector.onTouchEvent(event);
                return true;
            });

            listViewCanales.setOnItemClickListener((parent, view, position, id) -> {
                CanalEstructura seleccionado = listaFiltradaCanales.get(position);
                if (menuParaPantallaChica) {
                    reproducirCanalEstable(seleccionado, playerMini);
                } else {
                    reproducirCanalEstable(seleccionado, player);
                }
                limpiarBuscadorOcultarMenus();
            });

            listViewCanales.setOnItemLongClickListener((parent, view1, position, id) -> {
                CanalEstructura canalSeleccionado = listaFiltradaCanales.get(position);

                if (canalSeleccionado != null) {
                    String mensajeAccion = setDeCanalesFavoritos.contains(generarKeyCanal(canalSeleccionado))
                            ? "❌ Quitar de Favoritos"
                            : "⭐ Añadir a Favoritos";

                    new AlertDialog.Builder(this)
                            .setTitle(canalSeleccionado.nombreCanal)
                            .setItems(new String[]{mensajeAccion}, (dialog, which) -> {
                                if (which == 0) {
                                    alternarFavoritoCanal(canalSeleccionado);
                                }
                            })
                            .setNegativeButton("Cancelar", null)
                            .show();
                }
                return true;
            });

        listViewGrupos.setOnItemClickListener((parent, view, position, id) -> {
            if (inputBuscadorTiempoReal != null) inputBuscadorTiempoReal.setText("");
            grupoSeleccionadoActual = listaDeGruposVisibles.get(position);
            aplicarFiltroDeGrupo(grupoSeleccionadoActual);
            
            // Ahora simplemente pasamos el foco a la lista de canales
            listViewCanales.requestFocus();
        });

            listViewGrupos.setOnItemLongClickListener((parent, view1, position, id) -> {
                String grupoSeleccionado = listaDeGruposVisibles.get(position);
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putString(KEY_GRUPO_PREDETERMINADO, grupoSeleccionado)
                        .apply();
                Toast.makeText(MainActivity.this, "⭐ Grupo predeterminado: " + grupoSeleccionado, Toast.LENGTH_SHORT).show();
                return true;
            });

            if (inputBuscadorTiempoReal != null) {
                inputBuscadorTiempoReal.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        ejecutarFiltradoEnTiempoReal(s.toString());
                    }
                    @Override
                    public void afterTextChanged(Editable s) {}
                });
            }

            cargarListasDesdeMemoria();

            if (urlsDeListasGuardadas.isEmpty()) {
                solicitarNuevaLista(true);
            } else {
                String urlUltima = prefs.getString(KEY_ULTIMA_URL, "");
                if (!urlUltima.isEmpty() && urlsDeListasGuardadas.contains(urlUltima)) {
                    urlListaActualEnUso = urlUltima;
                    int index = urlsDeListasGuardadas.indexOf(urlUltima);
                    nombreListaActualEnUso = nombresDeListasGuardadas.get(index);
                } else {
                    urlListaActualEnUso = urlsDeListasGuardadas.get(0);
                    nombreListaActualEnUso = nombresDeListasGuardadas.get(0);
                }
                cargarListaDesdeUrl(urlListaActualEnUso);
            }

        } catch (Exception e) {
            Toast.makeText(this, "Error al iniciar el reproductor", Toast.LENGTH_LONG).show();
        }
    }

    private void mostrarDialogoCanalCaido() {
        new AlertDialog.Builder(this)
                .setTitle("⚠️ CANAL SIN SEÑAL")
                .setMessage("No se ha podido conectar con el canal seleccionado.\n\n¿Deseas abrir la lista para elegir otro canal?")
                .setCancelable(false)
                .setPositiveButton("VER CANALES", (dialog, which) -> {
                    alternarMenuCanales();
                })
                .setNegativeButton("REINTENTAR", (dialog, which) -> {
                    if (player != null) {
                        player.prepare();
                        player.play();
                    }
                })
                .show();
    }

    private boolean esGrupoAdulto(String grupo) {
        if (grupo == null) return false;
        String g = grupo.toUpperCase();
        return g.contains("XXX") || g.contains("ADULTO") || g.contains("ADULT") || 
               g.contains("+18") || g.contains("18+") || g.contains("PORNO");
    }

    private boolean esCanalAdulto(CanalEstructura canal) {
        if (canal == null) return false;
        return esGrupoAdulto(canal.grupoCanal);
    }

    private void reproducirCanalEstable(final CanalEstructura canal) {
        reproducirCanalEstable(canal, player);
    }

    private void reproducirCanalEstable(final CanalEstructura canal, final ExoPlayer targetPlayer) {
        if (targetPlayer == null || canal == null) return;

        if (esCanalAdulto(canal)) {
            final EditText input = new EditText(this);
            input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            input.setHint("Contenido Protegido");

            new AlertDialog.Builder(this)
                    .setTitle("🔞 Control Parental")
                    .setMessage("Ingrese la clave para ver este canal:")
                    .setView(input)
                    .setCancelable(false)
                    .setPositiveButton("Desbloquear", (dialog, which) -> {
                        String pass = input.getText().toString();
                        if (pass.equals(claveAdultos)) {
                            continuarReproduccion(canal, targetPlayer);
                        } else {
                            Toast.makeText(this, "❌ Clave incorrecta", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Cerrar", null)
                    .show();
        } else {
            continuarReproduccion(canal, targetPlayer);
        }
    }

    private void continuarReproduccion(CanalEstructura canal, ExoPlayer targetPlayer) {
        if (targetPlayer == player) {
            this.canalActualReproduciendo = canal;
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_ULTIMO_CANAL_SINTONIZADO, canal.urlStream)
                    .apply();
        } else {
            this.canalMiniReproduciendo = canal;
            if (contenedorMiniPlayer != null) {
                contenedorMiniPlayer.setVisibility(View.VISIBLE);
            }
        }

        DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory();
        dataSourceFactory.setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36");

        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", "https://player.sensa.com.ar/");
        headers.put("Origin", "https://player.sensa.com.ar");
        dataSourceFactory.setDefaultRequestProperties(headers);

        MediaItem.Builder mediaItemBuilder = new MediaItem.Builder()
                .setUri(Uri.parse(canal.urlStream))
                .setMediaId(canal.urlStream)
                .setMediaMetadata(new MediaMetadata.Builder().setTitle(canal.nombreCanal).build());

        MediaSource mediaSource;

        if (canal.licenseKey != null && canal.licenseKey.contains(":")) {
            String[] parts = canal.licenseKey.split(":");
            String licenseJson = "{\"keys\":[{\"kty\":\"oct\",\"k\":\"" + convertHexToBase64Url(parts[1]) + "\",\"kid\":\"" + convertHexToBase64Url(parts[0]) + "\"}]}";
            byte[] licenseBytes = licenseJson.getBytes(StandardCharsets.UTF_8);

            LocalMediaDrmCallback localDrmCallback = new LocalMediaDrmCallback(licenseBytes);

            mediaItemBuilder.setDrmConfiguration(new MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID)
                    .setMultiSession(true)
                    .build());

            mediaSource = new DashMediaSource.Factory(dataSourceFactory)
                    .setDrmSessionManagerProvider(unusedItem ->
                            new com.google.android.exoplayer2.drm.DefaultDrmSessionManager.Builder()
                                    .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, com.google.android.exoplayer2.drm.FrameworkMediaDrm.DEFAULT_PROVIDER)
                                    .build(localDrmCallback))
                    .createMediaSource(mediaItemBuilder.build());
        } else {
            mediaItemBuilder.setMimeType(canal.tipoMime);
            if (canal.tipoMime != null && canal.tipoMime.equals(MimeTypes.APPLICATION_M3U8)) {
                mediaSource = new com.google.android.exoplayer2.source.hls.HlsMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(mediaItemBuilder.build());
            } else {
                mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(mediaItemBuilder.build());
            }
        }

        targetPlayer.setMediaSource(mediaSource);
        targetPlayer.prepare();
        targetPlayer.play();
    }

    private String convertHexToBase64Url(String hex) {
        byte[] data = new byte[hex.length() / 2];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP | android.util.Base64.URL_SAFE | android.util.Base64.NO_PADDING);
    }

    private void cerrarMiniPlayer() {
        if (playerMini != null) {
            playerMini.stop();
            canalMiniReproduciendo = null;
        }
        if (contenedorMiniPlayer != null) {
            contenedorMiniPlayer.setVisibility(View.GONE);
        }
        Toast.makeText(this, "✖ Mini Pantalla Cerrada", Toast.LENGTH_SHORT).show();
    }

    private void cambiarCanalSiguiente(ExoPlayer targetPlayer) {
        if (listaFiltradaCanales.isEmpty()) return;
        CanalEstructura refCanal = (targetPlayer == player) ? canalActualReproduciendo : canalMiniReproduciendo;
        if (refCanal == null) return;

        int index = -1;
        for (int i = 0; i < listaFiltradaCanales.size(); i++) {
            if (listaFiltradaCanales.get(i).urlStream.equals(refCanal.urlStream)) {
                index = i;
                break;
            }
        }
        if (index != -1) {
            int nextIndex = (index + 1) % listaFiltradaCanales.size();
            CanalEstructura next = listaFiltradaCanales.get(nextIndex);
            String prefijo = (targetPlayer == player) ? "▲ " : "▲ Mini: ";
            Toast.makeText(this, prefijo + next.nombreCanal, Toast.LENGTH_SHORT).show();
            reproducirCanalEstable(next, targetPlayer);
        }
    }

    private void cambiarCanalAnterior(ExoPlayer targetPlayer) {
        if (listaFiltradaCanales.isEmpty()) return;
        CanalEstructura refCanal = (targetPlayer == player) ? canalActualReproduciendo : canalMiniReproduciendo;
        if (refCanal == null) return;

        int index = -1;
        for (int i = 0; i < listaFiltradaCanales.size(); i++) {
            if (listaFiltradaCanales.get(i).urlStream.equals(refCanal.urlStream)) {
                index = i;
                break;
            }
        }
        if (index != -1) {
            int prevIndex = (index - 1 + listaFiltradaCanales.size()) % listaFiltradaCanales.size();
            CanalEstructura prev = listaFiltradaCanales.get(prevIndex);
            String prefijo = (targetPlayer == player) ? "▼ " : "▼ Mini: ";
            Toast.makeText(this, prefijo + prev.nombreCanal, Toast.LENGTH_SHORT).show();
            reproducirCanalEstable(prev, targetPlayer);
        }
    }

    private void cargarListasDesdeMemoria() {
        nombresDeListasGuardadas.clear();
        urlsDeListasGuardadas.clear();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String jsonListas = prefs.getString(KEY_LISTAS_JSON, null);
        if (jsonListas != null) {
            try {
                JSONArray jsonArray = new JSONArray(jsonListas);
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject obj = jsonArray.getJSONObject(i);
                    nombresDeListasGuardadas.add(obj.getString("nombre"));
                    urlsDeListasGuardadas.add(obj.getString("url"));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void mostrarPanelAdministradorListas() {
        cargarListasDesdeMemoria();

        if (urlsDeListasGuardadas.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("📂 Administrador de Listas")
                    .setMessage("No tienes listas guardadas actualmente.")
                    .setPositiveButton("Agregar Lista", (dialog, which) -> solicitarNuevaLista(false))
                    .setNegativeButton("Volver", null)
                    .show();
            return;
        }

        String[] nombresMostrar = nombresDeListasGuardadas.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle("📂 Seleccione una Lista")
                .setItems(nombresMostrar, (dialog, indexSeleccionado) -> {
                    String nombreSeleccionado = nombresDeListasGuardadas.get(indexSeleccionado);
                    String urlSeleccionada = urlsDeListasGuardadas.get(indexSeleccionado);

                    String[] opcionesAccion = {"✅ Activar y reproducir", "✏️ Editar", "❌ Eliminar"};
                    new AlertDialog.Builder(this)
                            .setTitle("Opciones")
                            .setItems(opcionesAccion, (subDialog, opcionIndex) -> {
                                if (opcionIndex == 0) {
                                    urlListaActualEnUso = urlSeleccionada;
                                    nombreListaActualEnUso = nombreSeleccionado;
                                    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                            .edit().putString(KEY_ULTIMA_URL, urlSeleccionada).apply();
                                    limpiarBuscadorOcultarMenus();
                                    cargarListaDesdeUrl(urlSeleccionada);
                                    Toast.makeText(this, "Cargando: " + nombreSeleccionado, Toast.LENGTH_SHORT).show();

                                } else if (opcionIndex == 1) {
                                    formularioEditarLista(indexSeleccionado);

                                } else if (opcionIndex == 2) {
                                    ejecutarPrimerAvisoBorrar(indexSeleccionado);
                                }
                            })
                            .setNegativeButton("Atrás", (subDialog, w) -> mostrarPanelAdministradorListas())
                            .show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void formularioEditarLista(int posicionLista) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Editar Lista");
        builder.setCancelable(false);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        final EditText inputNombre = new EditText(this);
        inputNombre.setText(nombresDeListasGuardadas.get(posicionLista));
        layout.addView(inputNombre);

        final EditText inputUrl = new EditText(this);
        inputUrl.setText(urlsDeListasGuardadas.get(posicionLista));
        layout.addView(inputUrl);

        builder.setView(layout);

        builder.setPositiveButton("Actualizar", (dialog, which) -> {
            String nuevoNombre = inputNombre.getText().toString().trim();
            String nuevaUrl = inputUrl.getText().toString().trim();

            if (!nuevoNombre.isEmpty() && !nuevaUrl.isEmpty()) {
                actualizarListaEnMemoria(posicionLista, nuevoNombre, nuevaUrl);
                cargarListasDesdeMemoria();

                if (urlListaActualEnUso.equals(urlsDeListasGuardadas.get(posicionLista)) || urlListaActualEnUso.isEmpty()) {
                    urlListaActualEnUso = nuevaUrl;
                    nombreListaActualEnUso = nuevoNombre;
                    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_ULTIMA_URL, nuevaUrl).apply();
                    cargarListaDesdeUrl(nuevaUrl);
                } else {
                    mostrarPanelAdministradorListas();
                }
            } else {
                formularioEditarLista(posicionLista);
            }
        });
        builder.setNegativeButton("Cancelar", (dialog, which) -> mostrarPanelAdministradorListas());
        builder.show();
    }

    private void ejecutarPrimerAvisoBorrar(int posicionLista) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("⚠️ ¡ATENCIÓN!");
        builder.setMessage("¿Estás seguro de borrar la lista?");
        builder.setPositiveButton("SÍ, BORRAR", (dialog, which) -> ejecutarSegundoAvisoConfirmacion(posicionLista));
        builder.setNegativeButton("NO, CANCELAR", (dialog, which) -> mostrarPanelAdministradorListas());
        builder.show();
    }

    private void ejecutarSegundoAvisoConfirmacion(int posicionLista) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🛑 SEGUNDA ADVERTENCIA");
        builder.setMessage("Si borras la lista, perderás todos tus canales y favoritos de esta fuente.\n¿ESTÁS COMPLETAMENTE SEGURO?");
        builder.setPositiveButton("SÍ, CONTINUAR", (dialog, which) -> ejecutarTercerAvisoConfirmacion(posicionLista));
        builder.setNegativeButton("NO, CANCELAR", (dialog, which) -> mostrarPanelAdministradorListas());
        builder.show();
    }

    private void ejecutarTercerAvisoConfirmacion(int posicionLista) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("❗ CONFIRMACIÓN FINAL (IRREVERSIBLE)");
        builder.setMessage("Esta es la última oportunidad. ¿Realmente quieres ELIMINAR la lista?");
        builder.setPositiveButton("ELIMINAR DEFINITIVAMENTE", (dialog, which) -> {
            borrarListaDeMemoria(posicionLista);
            cargarListasDesdeMemoria();
            if (urlsDeListasGuardadas.isEmpty()) {
                urlListaActualEnUso = "";
                nombreListaActualEnUso = "Sin Lista";
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().remove(KEY_ULTIMA_URL).apply();
                if (player != null) player.stop();
                solicitarNuevaLista(true);
            } else {
                mostrarPanelAdministradorListas();
            }
        });
        builder.setNegativeButton("ME ARREPENTÍ", (dialog, which) -> mostrarPanelAdministradorListas());
        builder.show();
    }

    private void solicitarNuevaLista(boolean esObligatoria) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(esObligatoria ? "Configurar Lista Principal" : "Agregar Lista M3U");
        builder.setCancelable(false);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        final EditText inputNombre = new EditText(this);
        inputNombre.setHint("Nombre");
        inputNombre.setFocusable(true);
        inputNombre.setFocusableInTouchMode(true);
        inputNombre.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_NEXT);
        inputNombre.setSingleLine(true);
        layout.addView(inputNombre);

        final EditText inputUrl = new EditText(this);
        inputUrl.setHint("URL m3u");
        inputUrl.setFocusable(true);
        inputUrl.setFocusableInTouchMode(true);
        inputUrl.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        inputUrl.setSingleLine(true);
        layout.addView(inputUrl);

        builder.setView(layout);

        builder.setPositiveButton("Guardar", (dialog, which) -> {
            String nombre = inputNombre.getText().toString().trim();
            String urlStr = inputUrl.getText().toString().trim();

            if (!nombre.isEmpty() && !urlStr.isEmpty()) {
                // Si el usuario solo pone un nombre sin http, asumimos que es su código de cliente
                if (!urlStr.startsWith("http")) {
                    urlStr = "https://tu-servidor-o-web.com/listas/" + urlStr + ".m3u"; 
                }
                
                if (urlStr.contains("drive.google.com")) {
                    urlStr = convertirEnlaceGoogleDriveADirecto(urlStr);
                }
                guardarNuevaListaEnMemoria(nombre, urlStr);
                cargarListasDesdeMemoria();

                urlListaActualEnUso = urlStr;
                nombreListaActualEnUso = nombre;
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_ULTIMA_URL, urlStr).apply();
                cargarListaDesdeUrl(urlStr);
            } else {
                solicitarNuevaLista(esObligatoria);
            }
        });
        if (!esObligatoria) {
            builder.setNegativeButton("Volver", (dialog, which) -> mostrarPanelAdministradorListas());
        }
        builder.show();
    }

    private String convertirEnlaceGoogleDriveADirecto(String urlDrive) {
        try {
            String idArchivo = "";
            if (urlDrive.contains("/file/d/")) {
                int indexStart = urlDrive.indexOf("/file/d/") + 8;
                int indexEnd = urlDrive.indexOf("/", indexStart);
                if (indexEnd == -1) indexEnd = urlDrive.indexOf("?", indexStart);
                idArchivo = (indexEnd == -1) ? urlDrive.substring(indexStart) : urlDrive.substring(indexStart, indexEnd);
            } else if (urlDrive.contains("id=")) {
                int indexStart = urlDrive.indexOf("id=") + 3;
                int indexEnd = urlDrive.indexOf("&", indexStart);
                idArchivo = (indexEnd == -1) ? urlDrive.substring(indexStart) : urlDrive.substring(indexStart, indexEnd);
            }
            if (!idArchivo.isEmpty()) {
                return "https://docs.google.com/uc?export=download&id=" + idArchivo;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return urlDrive;
    }

    private void guardarNuevaListaEnMemoria(String nombre, String urlStr) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String jsonListas = prefs.getString(KEY_LISTAS_JSON, "[]");
        try {
            JSONArray jsonArray = new JSONArray(jsonListas);
            JSONObject nuevoObjeto = new JSONObject();
            nuevoObjeto.put("nombre", nombre);
            nuevoObjeto.put("url", urlStr);
            jsonArray.put(nuevoObjeto);
            prefs.edit().putString(KEY_LISTAS_JSON, jsonArray.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void actualizarListaEnMemoria(int posicion, String nuevoNombre, String nuevaUrl) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String jsonListas = prefs.getString(KEY_LISTAS_JSON, "[]");
        try {
            JSONArray jsonArray = new JSONArray(jsonListas);
            JSONObject objetoEditado = jsonArray.getJSONObject(posicion);
            objetoEditado.put("nombre", nuevoNombre);
            if (nuevaUrl.contains("drive.google.com")) {
                nuevaUrl = convertirEnlaceGoogleDriveADirecto(nuevaUrl);
            }
            objetoEditado.put("url", nuevaUrl);
            prefs.edit().putString(KEY_LISTAS_JSON, jsonArray.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void borrarListaDeMemoria(int posicion) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String jsonListas = prefs.getString(KEY_LISTAS_JSON, "[]");
        try {
            JSONArray jsonArray = new JSONArray(jsonListas);
            jsonArray.remove(posicion);
            prefs.edit().putString(KEY_LISTAS_JSON, jsonArray.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void alternarMenuCanales() {
        if (contenedorMenus != null) {
            if (contenedorMenus.getVisibility() == View.VISIBLE) {
                limpiarBuscadorOcultarMenus();
            } else {
                if (textNombreListaCabecera != null) {
                    textNombreListaCabecera.setText("LISTA: " + nombreListaActualEnUso.toUpperCase());
                }
                contenedorMenus.setVisibility(View.VISIBLE);
                listViewGrupos.setVisibility(View.VISIBLE);
                listViewCanales.setVisibility(View.VISIBLE);

                // Intentar encontrar el canal que se está reproduciendo actualmente
                String urlActual = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getString(KEY_ULTIMO_CANAL_SINTONIZADO, "");
                
                int indice = -1;
                for (int i = 0; i < listaFiltradaCanales.size(); i++) {
                    if (listaFiltradaCanales.get(i).urlStream.equals(urlActual)) {
                        indice = i;
                        break;
                    }
                }

                if (indice != -1) {
                    listViewCanales.setSelection(indice);
                    listViewCanales.requestFocus();
                } else {
                    // Si no está en la lista actual, enfocamos los grupos
                    listViewGrupos.requestFocus();
                }
            }
        }
    }

    private void limpiarBuscadorOcultarMenus() {
        if (inputBuscadorTiempoReal != null) inputBuscadorTiempoReal.setText("");
        if (contenedorMenus != null) contenedorMenus.setVisibility(View.GONE);
        if (contenedorConfiguracion != null) contenedorConfiguracion.setVisibility(View.GONE);
    }

    private String normalizarTexto(String texto) {
        if (texto == null) return "";
        String normalizado = Normalizer.normalize(texto, Normalizer.Form.NFD);
        return normalizado.replaceAll("\\p{InCombiningDiacriticalMarks}+", "").toLowerCase();
    }

    private void alternarMenuConfiguracion() {
        if (contenedorConfiguracion == null) return;

        if (contenedorMenus != null && contenedorMenus.getVisibility() == View.VISIBLE) {
            limpiarBuscadorOcultarMenus();
        }

        if (contenedorConfiguracion.getVisibility() == View.VISIBLE) {
            contenedorConfiguracion.setVisibility(View.GONE);
        } else {
            contenedorConfiguracion.setVisibility(View.VISIBLE);
            if (listViewConfiguracion != null) {
                listViewConfiguracion.requestFocus();
            }
        }
    }

    private void cargarOpcionesConfiguracion() {
        if (listViewConfiguracion == null) return;

        String[] titulos = {
                "📂 Gestión de Listas IPTV",
                "🔄 Recargar Lista Actual",
                "🔞 Control Parental (Adultos)",
                "🎮 Guía de Controles",
                "ℹ️ Acerca de"
        };

        String[] descripciones = {
                "Añade, edita o elimina tus listas M3U.",
                "Actualiza los canales de la lista en uso.",
                "Configura la clave para contenido sensible.",
                "Aprende cómo navegar y usar la app.",
                "Información de la versión 1.3"
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_2, android.R.id.text1, titulos) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView t1 = view.findViewById(android.R.id.text1);
                TextView t2 = view.findViewById(android.R.id.text2);

                t1.setTextColor(android.graphics.Color.WHITE);
                t1.setTextSize(16);
                t1.setText(titulos[position]);

                t2.setTextColor(android.graphics.Color.parseColor("#AAAAAA"));
                t2.setTextSize(12);
                t2.setText(descripciones[position]);

                view.setPadding(30, 25, 30, 25);
                return view;
            }
        };

        listViewConfiguracion.setAdapter(adapter);
        listViewConfiguracion.setSelector(getResources().getDrawable(R.drawable.selector_menu_televisor));

        listViewConfiguracion.setOnItemClickListener((parent, view, position, id) -> {
            switch (position) {
                case 0:
                    mostrarSubmenuListas();
                    break;
                case 1:
                    if (!urlListaActualEnUso.isEmpty()) {
                        cargarListaDesdeUrl(urlListaActualEnUso);
                        Toast.makeText(MainActivity.this, "Actualizando canales...", Toast.LENGTH_SHORT).show();
                        alternarMenuConfiguracion();
                    }
                    break;
                case 2:
                    verificarClaveAdultos();
                    break;
                case 3:
                    mostrarGuiaControles();
                    break;
                case 4:
                    String androidId = android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("BKO IPTV 3.5")
                            .setMessage("Versión 3.5\n\nID DE EQUIPO: " + androidId.toUpperCase() + "\n\nDesarrollado por Zepol Desings (Arg)\n\n© 2026 Todos los derechos reservados")
                            .setPositiveButton("COPIAR ID", (dialog, which) -> {
                                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                android.content.ClipData clip = android.content.ClipData.newPlainText("ID Equipo", androidId.toUpperCase());
                                clipboard.setPrimaryClip(clip);
                                Toast.makeText(MainActivity.this, "ID copiado al portapapeles", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("OK", null)
                            .show();
                    break;
            }
        });
    }

    private void mostrarGuiaControles() {
        StringBuilder guia = new StringBuilder();
        guia.append("📱 MÓVIL (PANTALLA):\n");
        guia.append("👆 TOQUE SIMPLE: Menú de canales (Grande).\n");
        guia.append("✌️ DOBLE TOQUE: Activar Multiview (Pasa actual a mini).\n");
        guia.append("🤏 TOQUE EN MINI: Cambiar canal de pantalla chica.\n");
        guia.append("❌ DOBLE TOQUE EN MINI: Cerrar pantalla chica.\n");
        guia.append("↕️ SWIPE ARRIBA/ABAJO: Cambiar canal (Grande o Mini).\n");
        guia.append("☝️ TOQUE LARGO: Abre este menú de Ajustes.\n\n");
        
        guia.append("📺 TV / CONTROL REMOTO:\n");
        guia.append("🔵 OK / CENTER: Abre el menú de canales.\n");
        guia.append("⏳ MANTENER OK: Abre este menú de Ajustes.\n");
        guia.append("↕️ ARRIBA / ABAJO: Cambiar de canal.\n");
        guia.append("⬅️ FLECHA IZQUIERDA: Vuelve a categorías.\n\n");
        
        guia.append("⭐ ADICIONALES:\n");
        guia.append("✨ OK LARGO en Canal: Favoritos.\n");
        guia.append("↩️ BACK: Cierra menús o sale.");

        new AlertDialog.Builder(this)
                .setTitle("🎮 GUÍA DE CONTROLES")
                .setMessage(guia.toString())
                .setPositiveButton("ENTENDIDO", null)
                .show();
    }

    private void ejecutarFiltradoEnTiempoReal(String texto) {
        String consultaOriginal = texto.trim();
        String consultaNormalizada = normalizarTexto(consultaOriginal);
        listaFiltradaCanales.clear();

        if (consultaOriginal.isEmpty()) {
            if (textNombreListaCabecera != null) {
                if (grupoSeleccionadoActual.equals("⭐ [ FAVORITOS ]")) {
                    textNombreListaCabecera.setText("SECCIÓN: FAVORITOS");
                } else {
                    textNombreListaCabecera.setText("CATEGORÍA: " + grupoSeleccionadoActual.toUpperCase());
                }
            }
            Set<String> urlsAgregadas = new HashSet<>();
            for (CanalEstructura canal : listaGlobalCanales) {
                if (!mostrarContenidoAdulto && esCanalAdulto(canal)) continue;

                if (grupoSeleccionadoActual.equals("[ TODOS LOS CANALES ]")) {
                    listaFiltradaCanales.add(canal);
                } else if (grupoSeleccionadoActual.equals("⭐ [ FAVORITOS ]")) {
                    String uniqueId = generarKeyCanal(canal);
                    if (setDeCanalesFavoritos.contains(uniqueId) && !urlsAgregadas.contains(uniqueId)) {
                        listaFiltradaCanales.add(canal);
                        urlsAgregadas.add(uniqueId);
                    }
                } else if (canal.grupoCanal.equalsIgnoreCase(grupoSeleccionadoActual)) {
                    listaFiltradaCanales.add(canal);
                }
            }
        } else {
            if (textNombreListaCabecera != null) {
                String sufijo = grupoSeleccionadoActual.equals("[ TODOS LOS CANALES ]") ? "" : " EN " + grupoSeleccionadoActual.toUpperCase();
                textNombreListaCabecera.setText("BUSCANDO" + sufijo + ": " + consultaOriginal.toUpperCase());
            }
            
            Set<String> urlsAgregadas = new HashSet<>();
            for (CanalEstructura canal : listaGlobalCanales) {
                if (!mostrarContenidoAdulto && esCanalAdulto(canal)) continue;

                String nombreNormalizado = normalizarTexto(canal.nombreCanal);
                if (nombreNormalizado.contains(consultaNormalizada)) {
                    if (grupoSeleccionadoActual.equals("[ TODOS LOS CANALES ]")) {
                        listaFiltradaCanales.add(canal);
                    } else if (grupoSeleccionadoActual.equals("⭐ [ FAVORITOS ]")) {
                        String uniqueId = generarKeyCanal(canal);
                        if (setDeCanalesFavoritos.contains(uniqueId) && !urlsAgregadas.contains(uniqueId)) {
                            listaFiltradaCanales.add(canal);
                            urlsAgregadas.add(uniqueId);
                        }
                    } else if (canal.grupoCanal.equalsIgnoreCase(grupoSeleccionadoActual)) {
                        listaFiltradaCanales.add(canal);
                    }
                }
            }
        }
        aplicarFiltroDirectoBuscador();
    }

    private String generarKeyCanal(CanalEstructura canal) {
        if (canal == null) return "";
        String urlLimpia = canal.urlStream != null ? canal.urlStream : "";
        if (urlLimpia.contains("?")) {
            urlLimpia = urlLimpia.substring(0, urlLimpia.indexOf("?"));
        }
        // Combinamos nombre y url limpia para que sea un ID único y estable
        return canal.nombreCanal + "|" + urlLimpia;
    }

    private void alternarFavoritoCanal(CanalEstructura canal) {
        if (canal == null) return;

        int posicionActual = listViewCanales.getSelectedItemPosition();
        String uniqueId = generarKeyCanal(canal);
        String tituloCanal = canal.nombreCanal;

        if (setDeCanalesFavoritos.contains(uniqueId)) {
            setDeCanalesFavoritos.remove(uniqueId);
            Toast.makeText(this, "❌ Quitado de Favoritos: " + tituloCanal, Toast.LENGTH_SHORT).show();
        } else {
            setDeCanalesFavoritos.add(uniqueId);
            Toast.makeText(this, "⭐ Agregado a Favoritos: " + tituloCanal, Toast.LENGTH_SHORT).show();
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putStringSet(KEY_FAVORITOS_SET, setDeCanalesFavoritos).apply();

        aplicarFiltroDeGrupo(grupoSeleccionadoActual);
        
        // Restaurar la posición después del refresco
        listViewCanales.post(() -> {
            listViewCanales.setSelection(posicionActual);
            listViewCanales.requestFocus();
        });
    }

    private void aplicarFiltroDirectoBuscador() {
        ArrayAdapter<CanalEstructura> adapter = new ArrayAdapter<CanalEstructura>(
                this, android.R.layout.simple_list_item_1, listaFiltradaCanales) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                LinearLayout filaLayout;
                if (convertView == null) {
                    filaLayout = new LinearLayout(getContext());
                    filaLayout.setOrientation(LinearLayout.HORIZONTAL);
                    filaLayout.setPadding(35, 25, 35, 25);
                    filaLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);

                    ImageView imgLogo = new ImageView(getContext());
                    imgLogo.setId(View.generateViewId());
                    LinearLayout.LayoutParams lpImg = new LinearLayout.LayoutParams(80, 80);
                    lpImg.rightMargin = 25;
                    imgLogo.setLayoutParams(lpImg);
                    imgLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    filaLayout.addView(imgLogo);

                    LinearLayout textContainer = new LinearLayout(getContext());
                    textContainer.setOrientation(LinearLayout.VERTICAL);
                    textContainer.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

                    TextView textNombre = new TextView(getContext());
                    textNombre.setId(View.generateViewId());
                    textNombre.setTextColor(android.graphics.Color.WHITE);
                    textNombre.setTextSize(16);
                    textNombre.setSingleLine(true);
                    textNombre.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    textContainer.addView(textNombre);

                    TextView textGrupo = new TextView(getContext());
                    textGrupo.setId(View.generateViewId());
                    textGrupo.setTextColor(android.graphics.Color.parseColor("#FFD700")); // Dorado/Amarillo
                    textGrupo.setTextSize(11);
                    textGrupo.setSingleLine(true);
                    textGrupo.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    textContainer.addView(textGrupo);

                    filaLayout.addView(textContainer);
                } else {
                    filaLayout = (LinearLayout) convertView;
                }

                ImageView logoView = (ImageView) filaLayout.getChildAt(0);
                LinearLayout textContainer = (LinearLayout) filaLayout.getChildAt(1);
                TextView nameView = (TextView) textContainer.getChildAt(0);
                TextView groupView = (TextView) textContainer.getChildAt(1);

                CanalEstructura actual = getItem(position);
                nameView.setText(actual.nombreCanal);
                groupView.setText("» " + actual.grupoCanal);

                if (setDeCanalesFavoritos.contains(generarKeyCanal(actual))) {
                    nameView.setText("⭐ " + actual.nombreCanal);
                }

                if (actual.urlLogo != null && !actual.urlLogo.isEmpty()) {
                    logoView.setVisibility(View.VISIBLE);
                    Glide.with(getContext())
                            .load(actual.urlLogo)
                            .placeholder(android.R.drawable.ic_menu_slideshow)
                            .error(android.R.drawable.ic_menu_slideshow)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .into(logoView);
                } else {
                    logoView.setImageResource(android.R.drawable.ic_menu_slideshow);
                }
                return filaLayout;
            }
        };
        listViewCanales.setAdapter(adapter);
        listViewCanales.setSelector(getResources().getDrawable(R.drawable.selector_menu_televisor));
        // Botón volver eliminado
    }

    private void aplicarFiltroDeGrupo(String group) {
        listaFiltradaCanales.clear();
        Set<String> urlsAgregadas = new HashSet<>();

        for (CanalEstructura canal : listaGlobalCanales) {
            if (!mostrarContenidoAdulto && esCanalAdulto(canal)) continue;

            if (group.equals("[ TODOS LOS CANALES ]")) {
                listaFiltradaCanales.add(canal);
            } else if (group.equals("⭐ [ FAVORITOS ]")) {
                String uniqueId = generarKeyCanal(canal);
                if (setDeCanalesFavoritos.contains(uniqueId) && !urlsAgregadas.contains(uniqueId)) {
                    listaFiltradaCanales.add(canal);
                    urlsAgregadas.add(uniqueId);
                }
            } else if (canal.grupoCanal.equalsIgnoreCase(group)) {
                listaFiltradaCanales.add(canal);
            }
        }

        ArrayAdapter<CanalEstructura> adapter = new ArrayAdapter<CanalEstructura>(
                this, android.R.layout.simple_list_item_1, listaFiltradaCanales) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                LinearLayout filaLayout;
                if (convertView == null) {
                    filaLayout = new LinearLayout(getContext());
                    filaLayout.setOrientation(LinearLayout.HORIZONTAL);
                    filaLayout.setPadding(35, 25, 35, 25);
                    filaLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);

                    ImageView imgLogo = new ImageView(getContext());
                    imgLogo.setId(View.generateViewId());
                    LinearLayout.LayoutParams lpImg = new LinearLayout.LayoutParams(80, 80);
                    lpImg.rightMargin = 25;
                    imgLogo.setLayoutParams(lpImg);
                    imgLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    filaLayout.addView(imgLogo);

                    LinearLayout textContainer = new LinearLayout(getContext());
                    textContainer.setOrientation(LinearLayout.VERTICAL);
                    textContainer.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

                    TextView textNombre = new TextView(getContext());
                    textNombre.setId(View.generateViewId());
                    textNombre.setTextColor(android.graphics.Color.WHITE);
                    textNombre.setTextSize(16);
                    textNombre.setSingleLine(true);
                    textNombre.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    textContainer.addView(textNombre);

                    TextView textGrupo = new TextView(getContext());
                    textGrupo.setId(View.generateViewId());
                    textGrupo.setTextColor(android.graphics.Color.parseColor("#FFD700")); // Dorado/Amarillo
                    textGrupo.setTextSize(11);
                    textGrupo.setSingleLine(true);
                    textGrupo.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    textContainer.addView(textGrupo);

                    filaLayout.addView(textContainer);
                } else {
                    filaLayout = (LinearLayout) convertView;
                }

                ImageView logoView = (ImageView) filaLayout.getChildAt(0);
                LinearLayout textContainer = (LinearLayout) filaLayout.getChildAt(1);
                TextView nameView = (TextView) textContainer.getChildAt(0);
                TextView groupView = (TextView) textContainer.getChildAt(1);

                CanalEstructura actual = getItem(position);
                nameView.setText(actual.nombreCanal);
                groupView.setText("» " + actual.grupoCanal);

                if (setDeCanalesFavoritos.contains(generarKeyCanal(actual))) {
                    nameView.setText("⭐ " + actual.nombreCanal);
                }

                if (actual.urlLogo != null && !actual.urlLogo.isEmpty()) {
                    logoView.setVisibility(View.VISIBLE);
                    Glide.with(getContext())
                            .load(actual.urlLogo)
                            .placeholder(android.R.drawable.ic_menu_slideshow)
                            .error(android.R.drawable.ic_menu_slideshow)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .into(logoView);
                } else {
                    logoView.setImageResource(android.R.drawable.ic_menu_slideshow);
                }

                return filaLayout;
            }
        };

        listViewCanales.setAdapter(adapter);
        listViewCanales.setSelector(getResources().getDrawable(R.drawable.selector_menu_televisor));
        // Botón volver eliminado
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean menusVisibles = (contenedorMenus != null && contenedorMenus.getVisibility() == View.VISIBLE);

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (menusVisibles) {
                limpiarBuscadorOcultarMenus();
                return true;
            } else {
                if (contenedorConfiguracion != null && contenedorConfiguracion.getVisibility() == View.VISIBLE) {
                    alternarMenuConfiguracion();
                    return true;
                }

                android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                        .setTitle("Salir de la aplicación")
                        .setMessage("¿Seguro que deseas salir de BKO IPTV 3.5?")
                        .setPositiveButton("Sí", (dialogInterface, which) -> {
                            finishAffinity();
                        })
                        .setNegativeButton("No", (dialogInterface, which) -> {
                            dialogInterface.dismiss();
                        })
                        .create();

                dialog.setOnShowListener(dialogInterface -> {
                    dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).requestFocus();
                });

                dialog.show();
                return true;
            }
        }

        if (!menusVisibles) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                cambiarCanalAnterior(player);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                cambiarCanalSiguiente(player);
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (event.getRepeatCount() == 0) {
                        tiempoPresionadoOk = System.currentTimeMillis();
                        yaSeEjecutoLargoOk = false;
                    } else {
                        if (System.currentTimeMillis() - tiempoPresionadoOk > 1000) {
                            if (!yaSeEjecutoLargoOk) {
                                yaSeEjecutoLargoOk = true;
                                android.widget.Toast.makeText(this, "Abriendo configuración...", android.widget.Toast.LENGTH_SHORT).show();

                                if (contenedorMenus != null) {
                                    contenedorMenus.setVisibility(View.GONE);
                                }

                                if (contenedorConfiguracion != null) {
                                    contenedorConfiguracion.setVisibility(View.VISIBLE);
                                    cargarOpcionesConfiguracion();
                                    if (listViewConfiguracion != null) {
                                        listViewConfiguracion.requestFocus();
                                    }
                                }
                            }
                        }
                    }
                }
                return true;
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            boolean menusVisibles = (contenedorMenus != null && contenedorMenus.getVisibility() == View.VISIBLE);
            boolean configVisible = (contenedorConfiguracion != null && contenedorConfiguracion.getVisibility() == View.VISIBLE);

            if (!yaSeEjecutoLargoOk && !menusVisibles && !configVisible) {
                // Solo abrir el menú si no hay nada visible (clic simple en TV sobre el video)
                menuParaPantallaChica = false;
                alternarMenuCanales();
            }
            yaSeEjecutoLargoOk = false;
            tiempoPresionadoOk = 0;
            return (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER);
        }
        return super.onKeyUp(keyCode, event);
    }

    private void cargarListaDesdeUrl(String urlM3u) {
        // Limpiar las listas actuales inmediatamente para dar feedback visual
        runOnUiThread(() -> {
            listaGlobalCanales.clear();
            listaFiltradaCanales.clear();
            if (listViewCanales != null && listViewCanales.getAdapter() != null) {
                ((ArrayAdapter<?>) listViewCanales.getAdapter()).notifyDataSetChanged();
            }
        });

        new Thread(() -> {
            try {
                // Añadir un parámetro de tiempo para saltar cualquier caché de servidor/proxy
                String urlConCacheBuster = urlM3u + (urlM3u.contains("?") ? "&" : "?") + "t=" + System.currentTimeMillis();
                URL url = new URL(urlConCacheBuster);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setUseCaches(false);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                connection.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate");
                connection.setRequestProperty("Pragma", "no-cache");
                connection.setRequestProperty("Expires", "0");

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String linea;

                String nombreCanal = "Canal Libre";
                String grupoCanal = "Otros";
                String urlLogo = "";
                String ultimaLicenciaDRM = "";

                listaGlobalCanales.clear();
                Set<String> setDeGruposUnicos = new HashSet<>();

                while ((linea = reader.readLine()) != null) {
                    linea = linea.trim();
                    if (linea.isEmpty()) continue;

                    if (linea.contains("license_key")) {
                        ultimaLicenciaDRM = linea.split("=")[1].trim();
                    }

                    if (linea.startsWith("#EXTINF:")) {
                        if (linea.contains("tvg-logo=\"")) {
                            int inicioLogo = linea.indexOf("tvg-logo=\"") + 10;
                            int finLogo = linea.indexOf("\"", inicioLogo);
                            if (finLogo != -1)
                                urlLogo = linea.substring(inicioLogo, finLogo).trim();
                        }

                        if (linea.contains("group-title=\"")) {
                            int inicio = linea.indexOf("group-title=\"") + 13;
                            int fin = linea.indexOf("\"", inicio);
                            if (fin != -1) grupoCanal = linea.substring(inicio, fin).trim();
                        } else {
                            grupoCanal = "Otros";
                        }

                        int comaIndex = linea.lastIndexOf(",");
                        if (comaIndex != -1) {
                            nombreCanal = linea.substring(comaIndex + 1).trim();
                        } else {
                            nombreCanal = "Canal Sin Nombre";
                        }
                    } else if (linea.startsWith("http://") || linea.startsWith("https://")) {

                        String tipoMime = null;
                        if (linea.contains(".mpd") || linea.contains(".mpd?")) {
                            tipoMime = MimeTypes.APPLICATION_MPD;
                        } else if (linea.contains(".m3u8") || linea.contains(".m3u8?")) {
                            tipoMime = MimeTypes.APPLICATION_M3U8;
                        }

                        CanalEstructura nuevoCanal = new CanalEstructura();
                        nuevoCanal.urlStream = linea;
                        nuevoCanal.licenseKey = ultimaLicenciaDRM;
                        nuevoCanal.nombreCanal = nombreCanal;
                        nuevoCanal.grupoCanal = grupoCanal;
                        nuevoCanal.urlLogo = urlLogo;
                        nuevoCanal.tipoMime = tipoMime;

                        String limpio = nombreCanal.toLowerCase()
                                .replaceAll("^[^a-zA-Z0-9áéíóúñ]+", "")
                                .trim();
                        nuevoCanal.nombreOrdenado = limpio.isEmpty() ? nombreCanal.toLowerCase() : limpio;

                        listaGlobalCanales.add(nuevoCanal);
                        if (!grupoCanal.isEmpty()) setDeGruposUnicos.add(grupoCanal);

                        nombreCanal = "Canal Libre";
                        grupoCanal = "Otros";
                        urlLogo = "";
                        ultimaLicenciaDRM = "";
                    }
                }
                reader.close();
                connection.disconnect();

                if (!listaGlobalCanales.isEmpty()) {
                    Collections.sort(listaGlobalCanales, new Comparator<CanalEstructura>() {
                        @Override
                        public int compare(CanalEstructura c1, CanalEstructura c2) {
                            return c1.nombreOrdenado.compareTo(c2.nombreOrdenado);
                        }
                    });

                    List<String> gruposOrdenados = new ArrayList<>(setDeGruposUnicos);
                    Collections.sort(gruposOrdenados, String.CASE_INSENSITIVE_ORDER);

                    listaDeGruposVisibles.clear();
                    listaDeGruposVisibles.add("[ TODOS LOS CANALES ]");
                    listaDeGruposVisibles.add("⭐ [ FAVORITOS ]");
                    
                    for (String g : gruposOrdenados) {
                        if (mostrarContenidoAdulto || !esGrupoAdulto(g)) {
                            listaDeGruposVisibles.add(g);
                        }
                    }

                    runOnUiThread(() -> {
                        SharedPreferences prefs1 = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

                        String grupoPredet = prefs1.getString(KEY_GRUPO_PREDETERMINADO, "[ TODOS LOS CANALES ]");
                        if (listaDeGruposVisibles.contains(grupoPredet)) {
                            grupoSeleccionadoActual = grupoPredet;
                        } else {
                            grupoSeleccionadoActual = "[ TODOS LOS CANALES ]";
                        }
                        aplicarFiltroDeGrupo(grupoSeleccionadoActual);

                        String ultimoCanalUrl = prefs1.getString(KEY_ULTIMO_CANAL_SINTONIZADO, "");
                        CanalEstructura canalAIniciar = null;

                        if (!ultimoCanalUrl.isEmpty()) {
                            for (CanalEstructura c : listaGlobalCanales) {
                                if (c.urlStream.equals(ultimoCanalUrl)) {
                                    canalAIniciar = c;
                                    break;
                                }
                            }
                        }

                        if (canalAIniciar == null && !listaGlobalCanales.isEmpty()) {
                            canalAIniciar = listaGlobalCanales.get(0);
                        }

                        if (canalAIniciar != null) {
                            reproducirCanalEstable(canalAIniciar);
                        }

                        ArrayAdapter<String> adapterGrupos = new ArrayAdapter<String>(
                                MainActivity.this, android.R.layout.simple_list_item_1, listaDeGruposVisibles) {
                            @Override
                            public View getView(int position, View convertView, ViewGroup parent) {
                                View view = super.getView(position, convertView, parent);
                                TextView text = view.findViewById(android.R.id.text1);
                                String itemText = getItem(position);
                                if (itemText.contains("FAVORITOS")) {
                                    text.setTextColor(android.graphics.Color.CYAN);
                                } else {
                                    text.setTextColor(android.graphics.Color.YELLOW);
                                }
                                text.setPadding(30, 40, 30, 40);
                                return view;
                            }
                        };
                        listViewGrupos.setAdapter(adapterGrupos);
                        listViewGrupos.setSelector(getResources().getDrawable(R.drawable.selector_menu_televisor));
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Error de red al procesar la lista.", Toast.LENGTH_LONG).show();
                    mostrarPanelAdministradorListas();
                });
            }
        }).start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) player.pause();
        if (playerMini != null) playerMini.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        reintentoHandler.removeCallbacksAndMessages(null);
        
        // Limpiar caché de logos automáticamente al cerrar
        new Thread(() -> {
            try {
                Glide.get(getApplicationContext()).clearDiskCache();
            } catch (Exception ignored) {}
        }).start();
        Glide.get(getApplicationContext()).clearMemory();

        if (player != null) {
            player.release();
            player = null;
        }
        if (playerMini != null) {
            playerMini.release();
            playerMini = null;
        }
    }

    private void mostrarSubmenuListas() {
        String[] opcionesPrincipales = {
                "➕ Agregar Nueva Lista",
                "✅ Seleccionar y Activar Lista",
                "✏️ Editar Lista",
                "❌ Eliminar Lista"
        };

        new android.app.AlertDialog.Builder(this)
                .setTitle("📂 Gestión de Listas IPTV")
                .setItems(opcionesPrincipales, (dialog, which) -> {
                    cargarListasDesdeMemoria();

                    if (which == 0) {
                        solicitarNuevaLista(false);

                    } else if (which == 1) {
                        if (urlsDeListasGuardadas.isEmpty()) {
                            android.widget.Toast.makeText(this, "No tienes listas guardadas para activar", android.widget.Toast.LENGTH_SHORT).show();
                            return;
                        }
                        String[] nombres = nombresDeListasGuardadas.toArray(new String[0]);
                        new android.app.AlertDialog.Builder(this)
                                .setTitle("✅ Seleccione Lista para Activar")
                                .setItems(nombres, (d, index) -> {
                                    String nombreSel = nombresDeListasGuardadas.get(index);
                                    String urlSel = urlsDeListasGuardadas.get(index);
                                    urlListaActualEnUso = urlSel;
                                    nombreListaActualEnUso = nombreSel;
                                    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                            .edit().putString(KEY_ULTIMA_URL, urlSel).apply();
                                    limpiarBuscadorOcultarMenus();
                                    cargarListaDesdeUrl(urlSel);
                                    android.widget.Toast.makeText(this, "Cargando: " + nombreSel, android.widget.Toast.LENGTH_SHORT).show();
                                })
                                .setNegativeButton("Atrás", (d, w) -> mostrarSubmenuListas())
                                .show();

                    } else if (which == 2) {
                        if (urlsDeListasGuardadas.isEmpty()) {
                            android.widget.Toast.makeText(this, "No tienes listas guardadas para editar", android.widget.Toast.LENGTH_SHORT).show();
                            return;
                        }
                        String[] nombres = nombresDeListasGuardadas.toArray(new String[0]);
                        new android.app.AlertDialog.Builder(this)
                                .setTitle("✏️ Seleccione Lista para Editar")
                                .setItems(nombres, (d, index) -> formularioEditarLista(index))
                                .setNegativeButton("Atrás", (d, w) -> mostrarSubmenuListas())
                                .show();

                    } else if (which == 3) {
                        if (urlsDeListasGuardadas.isEmpty()) {
                            android.widget.Toast.makeText(this, "No tienes listas guardadas para eliminar", android.widget.Toast.LENGTH_SHORT).show();
                            return;
                        }
                        String[] nombres = nombresDeListasGuardadas.toArray(new String[0]);
                        new android.app.AlertDialog.Builder(this)
                                .setTitle("❌ Seleccione Lista para Eliminar")
                                .setItems(nombres, (d, index) -> ejecutarPrimerAvisoBorrar(index))
                                .setNegativeButton("Atrás", (d, w) -> mostrarSubmenuListas())
                                .show();
                    }
                })
                .setNegativeButton("Volver", null)
                .show();
    }

    private void verificarClaveAdultos() {
        final SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        claveAdultos = prefs.getString(KEY_CLAVE_ADULTOS, "0000");

        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint("Ingrese clave actual");

        new android.app.AlertDialog.Builder(this)
                .setTitle("🔞 Control Parental")
                .setMessage("Ingrese la clave actual para configurar:")
                .setView(input)
                .setPositiveButton("Siguiente", (dialog, which) -> {
                    String pass = input.getText().toString();
                    if (pass.equals(claveAdultos)) {
                        String[] opciones = {"Cambiar Clave Parental", mostrarContenidoAdulto ? "Ocultar Grupos Restringidos" : "Mostrar Grupos Restringidos"};
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Opciones de Control")
                                .setItems(opciones, (d, whichOp) -> {
                                    if (whichOp == 0) {
                                        mostrarDialogoNuevaClave();
                                    } else {
                                        mostrarContenidoAdulto = !mostrarContenidoAdulto;
                                        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                                .edit().putBoolean(KEY_MOSTRAR_ADULTOS, mostrarContenidoAdulto).apply();
                                        
                                        // Recargar la interfaz para aplicar cambios
                                        cargarListaDesdeUrl(urlListaActualEnUso);
                                        Toast.makeText(MainActivity.this, mostrarContenidoAdulto ? "🔓 Grupos visibles" : "🔒 Grupos ocultos", Toast.LENGTH_SHORT).show();
                                    }
                                }).show();
                    } else {
                        android.widget.Toast.makeText(this, "❌ Clave incorrecta", android.widget.Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void mostrarDialogoNuevaClave() {
        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint("Nueva clave (mínimo 4 dígitos)");

        new android.app.AlertDialog.Builder(this)
                .setTitle("Nueva Clave")
                .setMessage("Ingrese su nueva clave numérica:")
                .setView(input)
                .setPositiveButton("Siguiente", (dialog, which) -> {
                    String nueva = input.getText().toString();
                    if (nueva.length() >= 4) {
                        mostrarConfirmacionNuevaClave(nueva);
                    } else {
                        android.widget.Toast.makeText(this, "Mínimo 4 dígitos", android.widget.Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void mostrarConfirmacionNuevaClave(final String nuevaClave) {
        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint("Repetir nueva clave");

        new android.app.AlertDialog.Builder(this)
                .setTitle("Confirmar Clave")
                .setMessage("Repita la nueva clave para confirmar:")
                .setView(input)
                .setPositiveButton("Guardar", (dialog, which) -> {
                    String confirmacion = input.getText().toString();
                    if (confirmacion.equals(nuevaClave)) {
                        claveAdultos = nuevaClave;
                        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                .edit().putString(KEY_CLAVE_ADULTOS, nuevaClave).apply();
                        android.widget.Toast.makeText(this, "✅ Clave actualizada correctamente", android.widget.Toast.LENGTH_LONG).show();
                    } else {
                        android.widget.Toast.makeText(this, "❌ Las claves no coinciden", android.widget.Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
}