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
import android.widget.FrameLayout;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.location.Location;
import android.location.LocationManager;

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

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

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
    private View layoutFondoInicio;
    private View layoutBotonesInicio;
    private TextView textEstadoReproduccion;
    private LinearLayout contenedorMenus;
    private TextView textNombreListaCabecera;
    private EditText inputBuscadorTiempoReal;
    private View contenedorConfiguracion;
    private View overlayBrillo;
    private View contenedorAjusteBrillo;
    private TextView textValorBrillo;
    private int nivelBrilloActual = 100; // 0 a 100
    private TextView textRelojDigital;
    private TextView textCiudadClima;
    private TextView textFechaDerecha;
    private TextView textFechaBloque;
    private String temperaturaActual = "";
    private String ciudadActual = "";
    private boolean verReloj = true, verClima = true, verCiudad = true, verFecha = true, verPronostico = false;
    private int alineacionBloqueActual = 0; // 0 a 7
    private int tamanioPronosticoActual = 1; // 0: CHICO, 1: MEDIANO, 2: GRANDE
    private final Handler relojHandler = new Handler(Looper.getMainLooper());
    private ListView listViewConfiguracion;
    private String nombreListaActualEnUso = "BKO IPTV";
    private String claveAdultos = "0000";

    // Variables para Temporizadores
    private long sleepMinutosRestantes = 0;
    private String horaApagadoProgramado = "00:00";
    private boolean modoApagadoProgramadoActivo = false;
    private Set<String> diasApagadoProgramado = new HashSet<>();
    private final Handler temporizadorHandler = new Handler(Looper.getMainLooper());
    private Runnable temporizadorRunnable;

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
    private static final String KEY_CACHE_CANALES = "cache_canales_json_v2";
    private static final String KEY_CACHE_URL_ORIGEN = "cache_url_origen_v2";
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

    private View layoutEstadoRed;
    private ImageView imgIconoRed;
    private TextView textEstadoRed;
    private final Handler reintentoHandler = new Handler(Looper.getMainLooper());
    private String urlListaActualEnUso = "";
    private String androidIdUnico = "";
    private DatabaseReference mDatabase;
    private ValueEventListener accountListener;
    private DatabaseReference currentAccountRef;
    private boolean equipoActivadoRemotamente = false;
    private androidx.appcompat.app.AlertDialog dialogoConfiguracionActual;
    
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
        layoutFondoInicio = findViewById(R.id.layout_fondo_inicio);
        layoutBotonesInicio = findViewById(R.id.layout_botones_inicio);
        textEstadoReproduccion = findViewById(R.id.text_estado_reproduccion);

        findViewById(R.id.btn_inicio_canales).setOnClickListener(v -> alternarMenuCanales());
        findViewById(R.id.btn_inicio_ajustes).setOnClickListener(v -> alternarMenuConfiguracion());
        findViewById(R.id.btn_inicio_salir).setOnClickListener(v -> mostrarDialogoSalir());

        contenedorMenus = findViewById(R.id.contenedor_menus);
        textNombreListaCabecera = findViewById(R.id.text_nombre_lista_cabecera);
        inputBuscadorTiempoReal = findViewById(R.id.input_buscador_canales);
        contenedorConfiguracion = findViewById(R.id.contenedor_configuracion);
        overlayBrillo = findViewById(R.id.overlay_brillo);
        contenedorAjusteBrillo = findViewById(R.id.contenedor_ajuste_brillo);
        textValorBrillo = findViewById(R.id.text_valor_brillo);
        textRelojDigital = findViewById(R.id.text_reloj_digital);
        textCiudadClima = findViewById(R.id.text_ciudad_clima);
        textFechaDerecha = findViewById(R.id.text_fecha_derecha);
        textFechaBloque = findViewById(R.id.text_fecha_bloque);
        
        cargarPreferenciasVisualizacion();
        iniciarActualizacionReloj();
        verificarPermisosUbicacion();
        iniciarMotorTemporizadores();
        
        findViewById(R.id.btn_cerrar_brillo).setOnClickListener(v -> cerrarAjusteBrillo());
        
        findViewById(R.id.btn_brillo_menos).setOnClickListener(v -> {
            if (nivelBrilloActual > 10) nivelBrilloActual -= 5;
            actualizarInterfazBrillo();
        });

        findViewById(R.id.btn_brillo_mas).setOnClickListener(v -> {
            if (nivelBrilloActual < 100) nivelBrilloActual += 5;
            actualizarInterfazBrillo();
        });
        
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

        layoutEstadoRed = findViewById(R.id.layout_estado_red);
        imgIconoRed = findViewById(R.id.img_icono_red);
        textEstadoRed = findViewById(R.id.text_estado_red);

        verificarConexionInicial();
        monitorearCambiosRed();

        mDatabase = FirebaseDatabase.getInstance().getReference();
        String rawId = android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
        androidIdUnico = (rawId != null) ? rawId.toUpperCase() : "EQUIPO_DESCONOCIDO";
        verificarActivacionEquipo();

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        setDeCanalesFavoritos = new HashSet<>(prefs.getStringSet(KEY_FAVORITOS_SET, new HashSet<>()));
        nivelBrilloActual = prefs.getInt("nivel_brillo_manual", 100);
        aplicarBrilloVisual(nivelBrilloActual);
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
            // Configuramos el selector de pistas para forzar máxima calidad
            com.google.android.exoplayer2.trackselection.DefaultTrackSelector trackSelector = 
                new com.google.android.exoplayer2.trackselection.DefaultTrackSelector(this);
            trackSelector.setParameters(trackSelector.buildUponParameters()
                .setMaxVideoSizeSd() // Esto es un truco: le decimos que el máximo sea gigante para que elija HD
                .setForceHighestSupportedBitrate(true));

            DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                    .setBufferDurationsMs(20000, 60000, 2500, 5000).build();

            player = new ExoPlayer.Builder(this)
                    .setTrackSelector(trackSelector)
                    .setLoadControl(loadControl)
                    .build();

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
                    } else if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
                        // El video se detuvo por completo: Mostrar fondo pero NO los botones si hay menús abiertos
                        if (layoutFondoInicio != null) {
                            layoutFondoInicio.setVisibility(View.VISIBLE);
                            
                            boolean menusVisibles = (contenedorMenus != null && contenedorMenus.getVisibility() == View.VISIBLE) ||
                                                 (contenedorConfiguracion != null && contenedorConfiguracion.getVisibility() == View.VISIBLE);
                            
                            if (layoutBotonesInicio != null && !menusVisibles) {
                                layoutBotonesInicio.setVisibility(View.VISIBLE);
                                findViewById(R.id.btn_inicio_canales).requestFocus();
                            }
                        }
                    }
                }

                @Override
                public void onVideoSizeChanged(com.google.android.exoplayer2.video.VideoSize videoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        // ES VIDEO: Ocultar fondo y botones inmediatamente
                        if (layoutFondoInicio != null) layoutFondoInicio.setVisibility(View.GONE);
                        if (layoutBotonesInicio != null) layoutBotonesInicio.setVisibility(View.GONE);
                    } else {
                        // CARGANDO O AUDIO: No mostramos los botones todavía para evitar el "flash" entre canales
                    }
                }

                @Override
                public void onPlayerError(PlaybackException error) {
                    contadorErroresReproduccion++;
                    
                    if (contadorErroresReproduccion >= 5) {
                        reintentoHandler.removeCallbacksAndMessages(null);
                        
                        // Si falla el principal, cerramos todo incluyendo el PIP
                        cerrarMiniPlayer();

                        if (layoutFondoInicio != null) {
                            layoutFondoInicio.setVisibility(View.VISIBLE);
                            // Solo mostramos los botones y pedimos foco si NO hay un menú abierto
                            boolean menusAbiertos = (contenedorMenus != null && contenedorMenus.getVisibility() == View.VISIBLE) ||
                                                 (contenedorConfiguracion != null && contenedorConfiguracion.getVisibility() == View.VISIBLE);
                            
                            if (layoutBotonesInicio != null && !menusAbiertos) {
                                layoutBotonesInicio.setVisibility(View.VISIBLE);
                                findViewById(R.id.btn_inicio_canales).requestFocus();
                            }
                        }
                        Toast.makeText(MainActivity.this, "❌ No se pudo conectar tras 5 intentos.", Toast.LENGTH_LONG).show();
                        contadorErroresReproduccion = 0;
                        return;
                    }

                    Toast.makeText(MainActivity.this, "Señal débil. Reintentando " + contadorErroresReproduccion + "/5 en 20 seg...", Toast.LENGTH_SHORT).show();

                    if (player != null) {
                        reintentoHandler.removeCallbacksAndMessages(null);
                        reintentoHandler.postDelayed(() -> {
                            if (player != null) {
                                player.prepare();
                                player.play();
                            }
                        }, 20000); // 20 segundos entre reintentos
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
                // CERRAMOS el menú para ver el reproductor, pero NO limpiamos el texto del buscador
                if (contenedorMenus != null) {
                    contenedorMenus.setVisibility(View.GONE);
                }
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
            verificarActivacionEquipo();
            cargarCacheSiExiste();

            if (!urlsDeListasGuardadas.isEmpty()) {
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

    private void activarModoAjusteBrillo() {
        if (contenedorConfiguracion != null) contenedorConfiguracion.setVisibility(View.GONE);
        if (contenedorAjusteBrillo != null) {
            contenedorAjusteBrillo.setVisibility(View.VISIBLE);
            if (textValorBrillo != null) textValorBrillo.setText(nivelBrilloActual + "%");
            findViewById(R.id.btn_cerrar_brillo).requestFocus();
        }
    }

    private void cerrarAjusteBrillo() {
        if (contenedorAjusteBrillo != null) contenedorAjusteBrillo.setVisibility(View.GONE);
        // Al cerrar, nos quedamos directamente en la reproducción
    }

    private void aplicarBrilloVisual(int nivel) {
        if (overlayBrillo != null) {
            // El nivel es de 0 (oscuro total) a 100 (brillo real)
            // La opacidad del overlay es inversa: 1.0 es negro total, 0.0 es nada.
            float opacidad = (100 - nivel) / 100f;
            // Limitamos a 0.8 para que no quede la pantalla 100% negra por error
            if (opacidad > 0.8f) opacidad = 0.8f; 
            overlayBrillo.setAlpha(opacidad);
        }
    }

    private void actualizarInterfazBrillo() {
        if (textValorBrillo != null) textValorBrillo.setText(nivelBrilloActual + "%");
        aplicarBrilloVisual(nivelBrilloActual);
        
        // Guardar preferencia
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt("nivel_brillo_manual", nivelBrilloActual)
                .apply();
    }

    private void iniciarActualizacionReloj() {
        relojHandler.post(new Runnable() {
            @Override
            public void run() {
                actualizarTextosPantalla();
                relojHandler.postDelayed(this, 30000);
            }
        });
    }

    private void actualizarTextosPantalla() {
        runOnUiThread(() -> {
            // 1. Actualizar Reloj y Clima
            if (textRelojDigital != null) {
                textRelojDigital.setVisibility(verReloj ? View.VISIBLE : View.GONE);
                java.text.SimpleDateFormat sdfHora = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
                String horaStr = sdfHora.format(new java.util.Date());
                
                if (verClima && temperaturaActual != null && !temperaturaActual.isEmpty()) {
                    textRelojDigital.setText(horaStr + " | " + temperaturaActual);
                } else {
                    textRelojDigital.setText(horaStr);
                }
            }
            
            // 2. Actualizar Ciudad
            if (textCiudadClima != null) {
                if (verCiudad && ciudadActual != null && !ciudadActual.isEmpty()) {
                    textCiudadClima.setVisibility(View.VISIBLE);
                    textCiudadClima.setText(ciudadActual);
                } else {
                    textCiudadClima.setVisibility(View.GONE);
                }
            }

            // 3. Actualizar Fecha
            java.text.SimpleDateFormat sdfFecha = new java.text.SimpleDateFormat("dd/MM/yy", java.util.Locale.getDefault());
            String fechaStr = sdfFecha.format(new java.util.Date());
            if (textFechaDerecha != null) textFechaDerecha.setText(fechaStr);
            if (textFechaBloque != null) textFechaBloque.setText(fechaStr);
        });
    }

    private void cargarPreferenciasVisualizacion() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        verReloj = prefs.getBoolean("ver_reloj", true);
        verClima = prefs.getBoolean("ver_clima", true);
        verCiudad = prefs.getBoolean("ver_ciudad", true);
        verFecha = prefs.getBoolean("ver_fecha", true);
        verPronostico = prefs.getBoolean("ver_pronostico_v2", false);
        alineacionBloqueActual = prefs.getInt("alineacion_bloque_v3", 0);
        tamanioPronosticoActual = prefs.getInt("tamanio_pronostico_v1", 1);
        
        // Aplicar posición inicial después de un pequeño retraso para asegurar que las vistas existan
        new Handler(Looper.getMainLooper()).postDelayed(this::aplicarPosicionVisual, 500);
    }

    private void aplicarPosicionVisual() {
        View layoutBloque = findViewById(R.id.layout_info_bloque);
        if (layoutBloque == null || textFechaDerecha == null || textFechaBloque == null) return;

        FrameLayout.LayoutParams paramsBloque = (FrameLayout.LayoutParams) layoutBloque.getLayoutParams();
        FrameLayout.LayoutParams paramsFechaDer = (FrameLayout.LayoutParams) textFechaDerecha.getLayoutParams();
        LinearLayout container = (LinearLayout) layoutBloque;

        int gravBloque, gravTexto, gravFechaDer;
        boolean esRepartido = (alineacionBloqueActual == 0 || alineacionBloqueActual == 4);

        switch (alineacionBloqueActual) {
            case 0: // ABAJO - REPARTIDO
                gravBloque = android.view.Gravity.BOTTOM | android.view.Gravity.START;
                gravFechaDer = android.view.Gravity.BOTTOM | android.view.Gravity.END;
                gravTexto = android.view.Gravity.START;
                break;
            case 1: // ABAJO - IZQUIERDA
                gravBloque = android.view.Gravity.BOTTOM | android.view.Gravity.START;
                gravFechaDer = gravBloque; gravTexto = android.view.Gravity.START;
                break;
            case 2: // ABAJO - CENTRO
                gravBloque = android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL;
                gravFechaDer = gravBloque; gravTexto = android.view.Gravity.CENTER_HORIZONTAL;
                break;
            case 3: // ABAJO - DERECHA
                gravBloque = android.view.Gravity.BOTTOM | android.view.Gravity.END;
                gravFechaDer = gravBloque; gravTexto = android.view.Gravity.END;
                break;
            case 4: // ARRIBA - REPARTIDO
                gravBloque = android.view.Gravity.TOP | android.view.Gravity.START;
                gravFechaDer = android.view.Gravity.TOP | android.view.Gravity.END;
                gravTexto = android.view.Gravity.START;
                break;
            case 5: // ARRIBA - IZQUIERDA
                gravBloque = android.view.Gravity.TOP | android.view.Gravity.START;
                gravFechaDer = gravBloque; gravTexto = android.view.Gravity.START;
                break;
            case 6: // ARRIBA - CENTRO
                gravBloque = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
                gravFechaDer = gravBloque; gravTexto = android.view.Gravity.CENTER_HORIZONTAL;
                break;
            case 7: // ARRIBA - DERECHA
                gravBloque = android.view.Gravity.TOP | android.view.Gravity.END;
                gravFechaDer = gravBloque; gravTexto = android.view.Gravity.END;
                break;
            default:
                gravBloque = android.view.Gravity.BOTTOM | android.view.Gravity.START;
                gravFechaDer = android.view.Gravity.BOTTOM | android.view.Gravity.END;
                gravTexto = android.view.Gravity.START;
                break;
        }

        paramsBloque.gravity = gravBloque;
        paramsFechaDer.gravity = gravFechaDer;
        layoutBloque.setLayoutParams(paramsBloque);
        textFechaDerecha.setLayoutParams(paramsFechaDer);
        container.setGravity(gravTexto);

        // Controlar qué fecha se ve y visibilidad general
        textFechaBloque.setVisibility((verFecha && !esRepartido) ? View.VISIBLE : View.GONE);
        textFechaDerecha.setVisibility((verFecha && esRepartido) ? View.VISIBLE : View.GONE);

        // Alinear los textos internamente
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof TextView) ((TextView) child).setGravity(gravTexto);
        }
    }

    private void iniciarMotorTemporizadores() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        horaApagadoProgramado = prefs.getString("hora_apagado_fija", "23:00");
        modoApagadoProgramadoActivo = prefs.getBoolean("modo_apagado_fijo_activo", false);
        diasApagadoProgramado = prefs.getStringSet("dias_apagado_fijo", new HashSet<>(java.util.Arrays.asList("1","2","3","4","5","6","7")));

        temporizadorRunnable = new Runnable() {
            @Override
            public void run() {
                revisarTemporizadores();
                temporizadorHandler.postDelayed(this, 60000); // Revisar cada 1 minuto
            }
        };
        temporizadorHandler.post(temporizadorRunnable);
    }

    private void revisarTemporizadores() {
        // 1. Revisar Sleep Rápido
        if (sleepMinutosRestantes > 0) {
            sleepMinutosRestantes--;
            if (sleepMinutosRestantes <= 0) {
                cerrarAppPorTemporizador("Sleep cumplido");
                return;
            } else if (sleepMinutosRestantes <= 2) {
                Toast.makeText(this, "💤 Sleep: La app se cerrará en " + sleepMinutosRestantes + " min.", Toast.LENGTH_SHORT).show();
            }
        }

        // 2. Revisar Apagado Programado
        if (modoApagadoProgramadoActivo) {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            int diaHoy = cal.get(java.util.Calendar.DAY_OF_WEEK); // 1:Dom, 2:Lun...
            String horaActual = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(cal.getTime());

            if (diasApagadoProgramado.contains(String.valueOf(diaHoy)) && horaActual.equals(horaApagadoProgramado)) {
                cerrarAppPorTemporizador("Horario programado cumplido");
            }
        }
    }

    private void cerrarAppPorTemporizador(String motivo) {
        Toast.makeText(this, "⏰ Apagado automático: " + motivo, Toast.LENGTH_LONG).show();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            finishAffinity();
        }, 3000);
    }

    private void mostrarMenuTemporizadores() {
        String[] opciones = {
            "💤 Sleep Rápido (Cuenta regresiva)",
            "⏰ Apagado Programado (Rutina Diaria)",
            "❌ Desactivar todos los temporizadores"
        };

        new AlertDialog.Builder(this)
            .setTitle("⏲️ Temporizadores de Apagado")
            .setItems(opciones, (dialog, which) -> {
                if (which == 0) mostrarSubmenuSleepRapido();
                else if (which == 1) mostrarSubmenuApagadoProgramado();
                else {
                    sleepMinutosRestantes = 0;
                    modoApagadoProgramadoActivo = false;
                    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("modo_apagado_fijo_activo", false).apply();
                    Toast.makeText(this, "Temporizadores desactivados", Toast.LENGTH_SHORT).show();
                }
            })
            .show();
    }

    private void mostrarSubmenuSleepRapido() {
        String[] tiempos = {"15 Minutos", "30 Minutos", "45 Minutos", "60 Minutos", "90 Minutos", "120 Minutos"};
        int[] valores = {15, 30, 45, 60, 90, 120};

        new AlertDialog.Builder(this)
            .setTitle("💤 Seleccionar tiempo de Sleep")
            .setItems(tiempos, (dialog, which) -> {
                sleepMinutosRestantes = valores[which];
                Toast.makeText(this, "💤 La app se cerrará en " + sleepMinutosRestantes + " minutos", Toast.LENGTH_LONG).show();
            })
            .setNegativeButton("Atrás", (d, w) -> mostrarMenuTemporizadores())
            .show();
    }

    private void mostrarSubmenuApagadoProgramado() {
        String estado = modoApagadoProgramadoActivo ? "✅ ACTIVO" : "❌ DESACTIVADO";
        String[] opciones = {
            "Estado: " + estado,
            "Hora de cierre: " + horaApagadoProgramado,
            "Seleccionar días"
        };

        new AlertDialog.Builder(this)
            .setTitle("⏰ Rutina de Apagado")
            .setItems(opciones, (dialog, which) -> {
                if (which == 0) {
                    modoApagadoProgramadoActivo = !modoApagadoProgramadoActivo;
                    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("modo_apagado_fijo_activo", modoApagadoProgramadoActivo).apply();
                    mostrarSubmenuApagadoProgramado();
                } else if (which == 1) {
                    mostrarDialogoConfigurarHora();
                } else if (which == 2) {
                    mostrarDialogoSeleccionarDias();
                }
            })
            .setPositiveButton("LISTO", null)
            .setNegativeButton("ATRÁS", (d, w) -> mostrarMenuTemporizadores())
            .show();
    }

    private void mostrarDialogoConfigurarHora() {
        String[] partes = horaApagadoProgramado.split(":");
        final int[] h = {Integer.parseInt(partes[0])};
        final int[] m = {Integer.parseInt(partes[1])};

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("⏰ Configurar Hora de Cierre");

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setGravity(android.view.Gravity.CENTER);
        mainLayout.setPadding(40, 40, 40, 40);
        mainLayout.setBackgroundColor(android.graphics.Color.BLACK);

        LinearLayout pickerLayout = new LinearLayout(this);
        pickerLayout.setOrientation(LinearLayout.HORIZONTAL);
        pickerLayout.setGravity(android.view.Gravity.CENTER);

        // Estilo común para HORA y MINUTOS
        final TextView textH = crearTextoAjustable(String.format(java.util.Locale.getDefault(), "%02d", h[0]));
        final TextView textSeparador = new TextView(this);
        textSeparador.setText(":");
        textSeparador.setTextSize(60);
        textSeparador.setTextColor(android.graphics.Color.WHITE);
        final TextView textM = crearTextoAjustable(String.format(java.util.Locale.getDefault(), "%02d", m[0]));

        // Lógica de Teclas para HORA
        textH.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    h[0] = (h[0] + 1) % 24;
                    textH.setText(String.format(java.util.Locale.getDefault(), "%02d", h[0]));
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    h[0] = (h[0] - 1 + 24) % 24;
                    textH.setText(String.format(java.util.Locale.getDefault(), "%02d", h[0]));
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    textM.requestFocus();
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    textM.requestFocus();
                    return true;
                }
            }
            return false;
        });

        // Lógica de Teclas para MINUTOS
        textM.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    m[0] = (m[0] + 5) % 60;
                    textM.setText(String.format(java.util.Locale.getDefault(), "%02d", m[0]));
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    m[0] = (m[0] - 5 + 60) % 60;
                    textM.setText(String.format(java.util.Locale.getDefault(), "%02d", m[0]));
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    textH.requestFocus();
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    // Al dar OK, va al botón de abajo
                    return false; // Deja que el sistema mueva el foco al primer botón
                }
            }
            return false;
        });

        pickerLayout.addView(textH);
        pickerLayout.addView(textSeparador);
        pickerLayout.addView(textM);
        mainLayout.addView(pickerLayout);

        TextView hint = new TextView(this);
        hint.setText("Flechas: Ajustar y Mover | OK: Siguiente");
        hint.setGravity(android.view.Gravity.CENTER);
        hint.setTextColor(android.graphics.Color.GRAY);
        hint.setPadding(0, 20, 0, 0);
        mainLayout.addView(hint);

        builder.setView(mainLayout);
        builder.setPositiveButton("GUARDAR", (dialog, which) -> {
            horaApagadoProgramado = String.format(java.util.Locale.getDefault(), "%02d:%02d", h[0], m[0]);
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("hora_apagado_fija", horaApagadoProgramado).apply();
            Toast.makeText(this, "Horario guardado: " + horaApagadoProgramado, Toast.LENGTH_SHORT).show();
            mostrarSubmenuApagadoProgramado();
        });
        builder.setNegativeButton("CANCELAR", (d, w) -> mostrarSubmenuApagadoProgramado());
        
        final AlertDialog dialog = builder.create();
        dialog.show();

        // Forzamos el foco inicial
        textH.post(() -> textH.requestFocus());
    }

    private TextView crearTextoAjustable(String inicial) {
        TextView tv = new TextView(this);
        tv.setText(inicial);
        tv.setTextSize(60);
        tv.setTextColor(android.graphics.Color.YELLOW);
        tv.setFocusable(true);
        tv.setFocusableInTouchMode(true);
        tv.setPadding(20, 10, 20, 10);
        
        // Efecto visual cuando tiene el foco
        tv.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                tv.setBackgroundColor(android.graphics.Color.parseColor("#33FFFFFF"));
                tv.setTextColor(android.graphics.Color.CYAN);
            } else {
                tv.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                tv.setTextColor(android.graphics.Color.YELLOW);
            }
        });
        return tv;
    }

    private android.widget.Button crearBotonAjuste(String texto, View.OnClickListener click) {
        android.widget.Button btn = new android.widget.Button(this);
        btn.setText(texto);
        btn.setTextSize(20);
        btn.setFocusable(true);
        btn.setOnClickListener(click);
        return btn;
    }

    private LinearLayout crearColumnaAjuste(android.widget.Button up, android.widget.Button down) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(up);
        col.addView(down);
        return col;
    }

    private void mostrarDialogoSeleccionarDias() {
        String[] nombresDias = {"Domingo", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado"};
        boolean[] seleccionados = new boolean[7];
        for (int i = 0; i < 7; i++) {
            seleccionados[i] = diasApagadoProgramado.contains(String.valueOf(i + 1));
        }

        new AlertDialog.Builder(this)
            .setTitle("Seleccionar días activos")
            .setMultiChoiceItems(nombresDias, seleccionados, (dialog, which, isChecked) -> {
                String diaId = String.valueOf(which + 1);
                if (isChecked) diasApagadoProgramado.add(diaId);
                else diasApagadoProgramado.remove(diaId);
            })
            .setPositiveButton("Guardar", (dialog, which) -> {
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putStringSet("dias_apagado_fijo", diasApagadoProgramado).apply();
                mostrarSubmenuApagadoProgramado();
            })
            .show();
    }

    private void mostrarMenuAjustesPantalla() {
        String[] nombresPosiciones = {
            "ABAJO - REPARTIDO", "ABAJO - IZQUIERDA", "ABAJO - CENTRO", "ABAJO - DERECHA",
            "ARRIBA - REPARTIDO", "ARRIBA - IZQUIERDA", "ARRIBA - CENTRO", "ARRIBA - DERECHA"
        };
        String[] nombresTamanios = {"CHICO", "MEDIANO", "GRANDE"};

        String[] opciones = {
                (verReloj ? "✅" : "❌") + " Ver Reloj",
                (verClima ? "✅" : "❌") + " Ver Clima",
                (verCiudad ? "✅" : "❌") + " Ver Ciudad",
                (verFecha ? "✅" : "❌") + " Ver Fecha",
                (verPronostico ? "✅" : "❌") + " Ver Pronóstico",
                "📐 Tamaño Pronóstico: " + nombresTamanios[tamanioPronosticoActual],
                "📍 Ubicación: " + nombresPosiciones[alineacionBloqueActual]
        };

        new AlertDialog.Builder(this)
                .setTitle("📺 Ajustes de Pantalla")
                .setItems(opciones, (dialog, which) -> {
                    SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
                    switch (which) {
                        case 0: verReloj = !verReloj; editor.putBoolean("ver_reloj", verReloj); break;
                        case 1: verClima = !verClima; editor.putBoolean("ver_clima", verClima); break;
                        case 2: verCiudad = !verCiudad; editor.putBoolean("ver_ciudad", verCiudad); break;
                        case 3: verFecha = !verFecha; editor.putBoolean("ver_fecha", verFecha); break;
                        case 4: 
                            verPronostico = !verPronostico; 
                            editor.putBoolean("ver_pronostico_v2", verPronostico);
                            break;
                        case 5:
                            mostrarSubmenuTamanioPronostico();
                            return;
                        case 6:
                            mostrarSubmenuUbicacion();
                            return; 
                    }
                    editor.apply();
                    aplicarPosicionVisual();
                    actualizarTextosPantalla();
                    mostrarMenuAjustesPantalla(); 
                })
                .setPositiveButton("LISTO", null)
                .show();
    }

    private void mostrarSubmenuTamanioPronostico() {
        String[] nombresTamanios = {"CHICO", "MEDIANO", "GRANDE"};

        new AlertDialog.Builder(this)
                .setTitle("📐 Seleccionar Tamaño")
                .setItems(nombresTamanios, (dialog, which) -> {
                    tamanioPronosticoActual = which;
                    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            .edit().putInt("tamanio_pronostico_v1", tamanioPronosticoActual).apply();
                    
                    // Forzar actualización inmediata del clima para ver el cambio de tamaño
                    actualizarClima(); 
                    mostrarMenuAjustesPantalla();
                })
                .setNegativeButton("ATRÁS", (dialog, which) -> mostrarMenuAjustesPantalla())
                .show();
    }

    private void mostrarSubmenuUbicacion() {
        String[] nombresPosiciones = {
            "ABAJO - REPARTIDO", "ABAJO - IZQUIERDA", "ABAJO - CENTRO", "ABAJO - DERECHA",
            "ARRIBA - REPARTIDO", "ARRIBA - IZQUIERDA", "ARRIBA - CENTRO", "ARRIBA - DERECHA"
        };

        new AlertDialog.Builder(this)
                .setTitle("📍 Seleccionar Ubicación")
                .setItems(nombresPosiciones, (dialog, which) -> {
                    alineacionBloqueActual = which;
                    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            .edit().putInt("alineacion_bloque_v3", alineacionBloqueActual).apply();
                    aplicarPosicionVisual();
                    mostrarMenuAjustesPantalla(); // Volver al menú anterior
                })
                .setNegativeButton("ATRÁS", (dialog, which) -> mostrarMenuAjustesPantalla())
                .show();
    }

    private void verificarPermisosUbicacion() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION}, 100);
        } else {
            actualizarClima();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            actualizarClima();
        } else {
            // Si el usuario rechaza, intentamos por IP de todos modos
            actualizarClima();
        }
    }

    private void actualizarClima() {
        new Thread(() -> {
            try {
                // PASO 1: Obtener Ciudad y Coordenadas por IP
                java.net.URL urlIp = new java.net.URL("http://ip-api.com/json");
                java.net.HttpURLConnection connIp = (java.net.HttpURLConnection) urlIp.openConnection();
                connIp.setRequestMethod("GET");
                connIp.setConnectTimeout(8000);
                
                java.io.BufferedReader readerIp = new java.io.BufferedReader(new java.io.InputStreamReader(connIp.getInputStream()));
                StringBuilder resIp = new StringBuilder();
                String lineIp;
                while ((lineIp = readerIp.readLine()) != null) resIp.append(lineIp);
                readerIp.close();
                
                org.json.JSONObject jsonIp = new org.json.JSONObject(resIp.toString());
                ciudadActual = jsonIp.getString("city").toUpperCase();
                double lat = jsonIp.getDouble("lat");
                double lon = jsonIp.getDouble("lon");

                // PASO 2: Obtener Clima Actual + Pronóstico 5 días (Open-Meteo)
                java.net.URL urlW = new java.net.URL("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon + "&current_weather=true&daily=weathercode,temperature_2m_max,temperature_2m_min&timezone=auto");
                java.net.HttpURLConnection connW = (java.net.HttpURLConnection) urlW.openConnection();
                connW.setRequestMethod("GET");
                connW.setConnectTimeout(8000);

                java.io.BufferedReader readerW = new java.io.BufferedReader(new java.io.InputStreamReader(connW.getInputStream()));
                StringBuilder resW = new StringBuilder();
                String lineW;
                while ((lineW = readerW.readLine()) != null) resW.append(lineW);
                readerW.close();

                org.json.JSONObject jsonW = new org.json.JSONObject(resW.toString());
                
                // Procesar Clima Actual
                org.json.JSONObject current = jsonW.getJSONObject("current_weather");
                double tempActual = current.getDouble("temperature");
                int codeActual = current.getInt("weathercode");
                
                // Obtener Máx/Mín de HOY (índice 0 del array daily)
                org.json.JSONObject daily = jsonW.getJSONObject("daily");
                
                temperaturaActual = Math.round(tempActual) + "°C " + obtenerEmojiClimaMeteo(codeActual);
                
                // Procesar Pronóstico Extendido
                org.json.JSONArray dailyTimes = daily.getJSONArray("time");
                org.json.JSONArray dailyCodes = daily.getJSONArray("weathercode");
                org.json.JSONArray dailyMaxs = daily.getJSONArray("temperature_2m_max");
                org.json.JSONArray dailyMins = daily.getJSONArray("temperature_2m_min");

                runOnUiThread(() -> {
                    actualizarTextosPantalla();
                    actualizarInterfazPronostico(dailyTimes, dailyCodes, dailyMaxs, dailyMins);
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
            
            // Reintentar cada 1 hora
            relojHandler.postDelayed(this::actualizarClima, 3600000);
        }).start();
    }

    private void actualizarInterfazPronostico(org.json.JSONArray times, org.json.JSONArray codes, org.json.JSONArray maxs, org.json.JSONArray mins) {
        LinearLayout layoutPronostico = findViewById(R.id.layout_pronostico_extendido);
        if (layoutPronostico == null) return;
        
        layoutPronostico.removeAllViews();
        layoutPronostico.setVisibility(verPronostico ? View.VISIBLE : View.GONE);

        // Nombres de los días abreviados
        String[] diasNombres = {"Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb"};
        java.util.Calendar cal = java.util.Calendar.getInstance();

        try {
            // Empezamos desde i=0 para mostrar desde HOY
            for (int i = 0; i < 5; i++) {
                String dateStr = times.getString(i);
                int code = codes.getInt(i);
                int max = (int) Math.round(maxs.getDouble(i));
                int min = (int) Math.round(mins.getDouble(i));

                // Obtener nombre del día
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
                java.util.Date date = sdf.parse(dateStr);
                if (date != null) cal.setTime(date);
                
                // Si es el índice 0, ponemos "HOY", si no, el día abreviado
                String nombreDia = (i == 0) ? "HOY" : diasNombres[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1];

                // Crear Bloque de Día
                LinearLayout dayBlock = new LinearLayout(this);
                dayBlock.setOrientation(LinearLayout.VERTICAL);
                dayBlock.setGravity(android.view.Gravity.CENTER);
                dayBlock.setPadding(20, 10, 20, 10);
                
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, 
                    ViewGroup.LayoutParams.WRAP_CONTENT
                );
                lp.setMargins(10, 0, 10, 0);
                dayBlock.setLayoutParams(lp);

                // Línea 1: Icono + Día
                int sizeUpper = (tamanioPronosticoActual == 0) ? 11 : (tamanioPronosticoActual == 2) ? 18 : 14;
                int sizeLower = (tamanioPronosticoActual == 0) ? 9 : (tamanioPronosticoActual == 2) ? 15 : 12;

                TextView textUpper = new TextView(this);
                textUpper.setText(obtenerEmojiClimaMeteo(code) + " " + nombreDia.toUpperCase());
                textUpper.setTextColor(android.graphics.Color.WHITE);
                textUpper.setTextSize(sizeUpper);
                textUpper.setShadowLayer(3, 1, 1, android.graphics.Color.BLACK);
                dayBlock.addView(textUpper);

                // Línea 2: Temperaturas
                TextView textLower = new TextView(this);
                textLower.setText(min + "/" + max);
                textLower.setTextColor(android.graphics.Color.parseColor("#BBBBBB"));
                textLower.setTextSize(sizeLower);
                textLower.setShadowLayer(2, 1, 1, android.graphics.Color.BLACK);
                dayBlock.addView(textLower);

                layoutPronostico.addView(dayBlock);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String obtenerEmojiClimaMeteo(int code) {
        // Códigos WMO (Open-Meteo)
        if (code == 0) return "☀️"; // Despejado
        if (code <= 3) return "⛅"; // Parcialmente nublado
        if (code <= 48) return "🌫️"; // Niebla
        if (code <= 67) return "🌧️"; // Lluvia
        if (code <= 77) return "❄️"; // Nieve
        if (code <= 82) return "🌧️"; // Chubascos
        if (code <= 99) return "⛈️"; // Tormenta
        return "☁️";
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
            // ELIMINADA la línea que forzaba la visibilidad del fondo aquí
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
        if (contenedorMiniPlayer != null && contenedorMiniPlayer.getVisibility() == View.VISIBLE) {
            if (playerMini != null) {
                playerMini.stop();
                canalMiniReproduciendo = null;
            }
            contenedorMiniPlayer.setVisibility(View.GONE);
            Toast.makeText(this, "✖ Mini Pantalla Cerrada", Toast.LENGTH_SHORT).show();
        }
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
        layout.setPadding(30, 5, 30, 5);

        final TextView textAvisoLegal = new TextView(this);
        textAvisoLegal.setText("Esta aplicación es solo un reproductor de listas .m3u / .m3u8. BKO IPTV PLAYER no proporciona listas ni direcciones url. Para su funcionamiento debe ingresar la url de uso legal proporcionada por su proveedor o una lista propia.\n\nDescargo de Responsabilidad: \"no nos responsabilizamos por el uso indebido de esta aplicación\"");
        textAvisoLegal.setTextColor(android.graphics.Color.GRAY);
        textAvisoLegal.setTextSize(10);
        textAvisoLegal.setLineSpacing(0, 0.9f); // Achicar entrelineas del texto legal
        textAvisoLegal.setPadding(10, 0, 10, 5);
        layout.addView(textAvisoLegal);

        final TextView labelNombre = new TextView(this);
        labelNombre.setText("Titulo:");
        labelNombre.setTextColor(android.graphics.Color.BLACK);
        labelNombre.setTextSize(13);
        labelNombre.setPadding(10, 0, 0, 0);
        layout.addView(labelNombre);

        final EditText inputNombre = new EditText(this);
        inputNombre.setHint(androidIdUnico);
        inputNombre.setHintTextColor(android.graphics.Color.LTGRAY);
        inputNombre.setPadding(10, 5, 10, 5); // Compactar el cuadro de texto
        inputNombre.setFocusable(true);
        inputNombre.setFocusableInTouchMode(true);
        inputNombre.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_NEXT);
        inputNombre.setSingleLine(true);
        layout.addView(inputNombre);

        final TextView labelUrl = new TextView(this);
        labelUrl.setText("Direccion:");
        labelUrl.setTextColor(android.graphics.Color.BLACK);
        labelUrl.setTextSize(13);
        labelUrl.setPadding(10, 0, 0, 0);
        layout.addView(labelUrl);

        final EditText inputUrl = new EditText(this);
        inputUrl.setHint("https://...");
        inputUrl.setHintTextColor(android.graphics.Color.LTGRAY);
        inputUrl.setPadding(10, 5, 10, 5); // Compactar el cuadro de texto
        inputUrl.setFocusable(true);
        inputUrl.setFocusableInTouchMode(true);
        inputUrl.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        inputUrl.setSingleLine(true);
        layout.addView(inputUrl);

        // Envolvemos todo en un ScrollView para Android TV
        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(layout);
        builder.setView(scrollView);

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
        if (esObligatoria) {
            builder.setNegativeButton("IR AL INICIO", (dialog, which) -> {
                // Al cancelar la carga obligatoria, forzamos la vista del banner de inicio
                if (layoutFondoInicio != null) layoutFondoInicio.setVisibility(View.VISIBLE);
                if (layoutBotonesInicio != null) {
                    layoutBotonesInicio.setVisibility(View.VISIBLE);
                    findViewById(R.id.btn_inicio_canales).requestFocus();
                }
                dialog.dismiss();
            });
        } else {
            builder.setNegativeButton("VOLVER", (dialog, which) -> mostrarPanelAdministradorListas());
        }
        dialogoConfiguracionActual = builder.create();
        dialogoConfiguracionActual.show();

        // Hacer el cartel más ancho (3/4 de la pantalla)
        if (dialogoConfiguracionActual.getWindow() != null) {
            android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(metrics);
            int width = (int) (metrics.widthPixels * 0.75);
            dialogoConfiguracionActual.getWindow().setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
        }

        // Forzar que el foco vaya al primer campo y abrir teclado
        inputNombre.requestFocus();
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
                // Al abrir el menú, ocultamos los botones de inicio para que no roben el foco
                if (layoutBotonesInicio != null) layoutBotonesInicio.setVisibility(View.GONE);
                
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
        
        // Si no hay video reproduciéndose, devolvemos la visibilidad a los botones de inicio
        if (player != null && !player.isPlaying()) {
            if (layoutBotonesInicio != null) {
                layoutBotonesInicio.setVisibility(View.VISIBLE);
                findViewById(R.id.btn_inicio_canales).requestFocus();
            }
        }
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
                "🔄 Actualizar Canales",
                "📺 Ajustes de Pantalla",
                "⏲️ Temporizadores de Apagado",
                "☀ Ajuste de Brillo",
                "🔞 Control Parental (Adultos)",
                "🎮 Guía de Controles",
                "ℹ️ Acerca de"
        };

        String[] descripciones = {
                "Añade, edita o elimina tus listas M3U.",
                "Forzar descarga de canales desde el servidor.",
                "Ubicación y visibilidad de reloj y clima.",
                "Sleep rápido y apagado programado diario.",
                "Controla la intensidad de luz de la pantalla.",
                "Configura la clave para contenido sensible.",
                "Aprende cómo navegar y usar la app.",
                "Información de la versión 3.5"
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
                        alternarMenuConfiguracion();
                    } else {
                        Toast.makeText(this, "No hay una lista activa para actualizar", Toast.LENGTH_SHORT).show();
                    }
                    break;
                case 2:
                    mostrarMenuAjustesPantalla();
                    break;
                case 3:
                    mostrarMenuTemporizadores();
                    break;
                case 4:
                    activarModoAjusteBrillo();
                    break;
                case 5:
                    verificarClaveAdultos();
                    break;
                case 6:
                    mostrarGuiaControles();
                    break;
                case 7:
                    String androidId = android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("BKO IPTV 3.5")
                            .setMessage("Versión 3.5\n\nID DE EQUIPO: " + androidId.toUpperCase() + "\n\nDesarrollador: zepoldesings@gmail.com\n\nZepol Desings (Arg)\n\n© 2026 Todos los derechos reservados")
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
        boolean configVisible = (contenedorConfiguracion != null && contenedorConfiguracion.getVisibility() == View.VISIBLE);
        boolean inicioVisible = (layoutBotonesInicio != null && layoutBotonesInicio.getVisibility() == View.VISIBLE);
        boolean brilloVisible = (contenedorAjusteBrillo != null && contenedorAjusteBrillo.getVisibility() == View.VISIBLE);
        boolean miniVisible = (contenedorMiniPlayer != null && contenedorMiniPlayer.getVisibility() == View.VISIBLE);

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (brilloVisible) {
                cerrarAjusteBrillo();
                return true;
            }
            if (miniVisible) {
                cerrarMiniPlayer();
                return true;
            }
            if (menusVisibles || configVisible) {
                limpiarBuscadorOcultarMenus();
                return true;
            } else if (layoutFondoInicio != null && layoutFondoInicio.getVisibility() != View.VISIBLE) {
                // Si hay video, volvemos al fondo de inicio con botones
                if (player != null) player.stop();
                
                // Solo llamamos a cerrarMiniPlayer si realmente está visible
                cerrarMiniPlayer();
                
                layoutFondoInicio.setVisibility(View.VISIBLE);
                if (layoutBotonesInicio != null) {
                    layoutBotonesInicio.setVisibility(View.VISIBLE);
                    findViewById(R.id.btn_inicio_canales).requestFocus();
                }
                return true;
            } else {
                mostrarDialogoSalir();
                return true;
            }
        }

        if (!menusVisibles && !configVisible && !inicioVisible && !brilloVisible) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                cambiarCanalAnterior(player);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                cambiarCanalSiguiente(player);
                return true;
            }
        }

        if (brilloVisible) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (nivelBrilloActual > 10) nivelBrilloActual -= 5;
                actualizarInterfazBrillo();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (nivelBrilloActual < 100) nivelBrilloActual += 5;
                actualizarInterfazBrillo();
                return true;
            }
        }

        if (!menusVisibles && !configVisible && !inicioVisible && !brilloVisible) {
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

    private void verificarActivacionEquipo() {
        if (mDatabase == null) return;

        mDatabase.child("clientes").child(androidIdUnico).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // Limpiamos cualquier escucha de cuenta anterior antes de procesar el cambio
                    removerEscuchaDeCuenta();

                    Boolean equipoActivo = snapshot.child("activo").getValue(Boolean.class);
                    String cuentaPadre = snapshot.child("pertenece_a").getValue(String.class);
                    String urlIndividual = snapshot.child("url_lista").getValue(String.class);

                    // LÓGICA INTELIGENTE PARA PLAY STORE Y ADMIN
                    if (equipoActivo == null || !equipoActivo) {
                        equipoActivadoRemotamente = false;
                        if ((cuentaPadre == null || cuentaPadre.isEmpty()) && (urlIndividual == null || urlIndividual.isEmpty())) {
                            verificarSiMostrarConfiguracionObligatoria();
                        } else {
                            bloquearPorEquipoInactivo();
                        }
                        return;
                    }

                    if (cuentaPadre != null && !cuentaPadre.isEmpty()) {
                        verificarReglasDeCuenta(cuentaPadre);
                    } else {
                        procesarActivacionIndividual(snapshot);
                    }
                } else {
                    registrarNuevoEquipoAutomatico();
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                verificarSiMostrarConfiguracionObligatoria();
            }
        });
    }

    private void removerEscuchaDeCuenta() {
        if (currentAccountRef != null && accountListener != null) {
            currentAccountRef.removeEventListener(accountListener);
            currentAccountRef = null;
            accountListener = null;
        }
    }

    private void bloquearPorEquipoInactivo() {
        runOnUiThread(() -> {
            // Detener reproductores inmediatamente
            if (player != null) player.stop();
            if (playerMini != null) playerMini.stop();
            listaGlobalCanales.clear();
            listaFiltradaCanales.clear();
            
            if (dialogoConfiguracionActual != null && dialogoConfiguracionActual.isShowing()) return;

            new AlertDialog.Builder(this)
                .setTitle("⚠️ ACCESO DENEGADO")
                .setMessage("Este dispositivo no está autorizado o ha sido desactivado.\n\nConsulte con su proveedor.")
                .setCancelable(false)
                .setPositiveButton("IR AL INICIO", (d, w) -> {
                    if (layoutFondoInicio != null) layoutFondoInicio.setVisibility(View.VISIBLE);
                    if (layoutBotonesInicio != null) {
                        layoutBotonesInicio.setVisibility(View.VISIBLE);
                        findViewById(R.id.btn_inicio_canales).requestFocus();
                    }
                })
                .setNegativeButton("SALIR", (d, w) -> finishAffinity())
                .show();
        });
    }

    private void procesarActivacionIndividual(DataSnapshot snapshot) {
        Boolean activado = snapshot.child("activo").getValue(Boolean.class);
        String urlPremium = snapshot.child("url_lista").getValue(String.class);

        if (activado != null && activado) {
            equipoActivadoRemotamente = true;
            if (dialogoConfiguracionActual != null && dialogoConfiguracionActual.isShowing()) {
                dialogoConfiguracionActual.dismiss();
            }
            if (urlPremium != null && !urlPremium.isEmpty()) {
                manejarCargaDeLista(urlPremium);
            }
        } else {
            equipoActivadoRemotamente = false;
            verificarSiMostrarConfiguracionObligatoria();
        }
    }

    private void verificarReglasDeCuenta(String nombreCuenta) {
        currentAccountRef = mDatabase.child("cuentas").child(nombreCuenta);
        accountListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot accountSnapshot) {
                if (accountSnapshot.exists()) {
                    Boolean cuentaActiva = accountSnapshot.child("activo").getValue(Boolean.class);
                    String urlCuenta = accountSnapshot.child("url_lista").getValue(String.class);
                    Integer limiteEquipos = accountSnapshot.child("limite").getValue(Integer.class);
                    
                    if (cuentaActiva == null || !cuentaActiva) {
                        bloquearPorCuentaInactiva();
                        return;
                    }

                    mDatabase.child("clientes").orderByChild("pertenece_a").equalTo(nombreCuenta)
                        .addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot clientesSnapshot) {
                                List<String> listaIds = new ArrayList<>();
                                for (DataSnapshot ds : clientesSnapshot.getChildren()) {
                                    listaIds.add(ds.getKey());
                                }
                                Collections.sort(listaIds);
                                
                                int miPosicion = listaIds.indexOf(androidIdUnico);
                                int limiteReal = (limiteEquipos != null) ? limiteEquipos : 1;

                                if (miPosicion < limiteReal) {
                                    equipoActivadoRemotamente = true;
                                    if (dialogoConfiguracionActual != null && dialogoConfiguracionActual.isShowing()) {
                                        dialogoConfiguracionActual.dismiss();
                                    }
                                    manejarCargaDeLista(urlCuenta);
                                } else {
                                    bloquearPorExcesoDePantallas(limiteReal);
                                }
                            }

                            @Override
                            public void onCancelled(DatabaseError error) {}
                        });
                } else {
                    equipoActivadoRemotamente = false;
                    verificarSiMostrarConfiguracionObligatoria();
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {}
        };
        currentAccountRef.addValueEventListener(accountListener);
    }

    private void manejarCargaDeLista(String url) {
        if (url == null || url.isEmpty()) {
            // Si la URL es vacía, detenemos todo y limpiamos
            if (player != null) player.stop();
            if (playerMini != null) playerMini.stop();
            listaGlobalCanales.clear();
            listaFiltradaCanales.clear();
            
            // Volvemos a mostrar el cartel de inicio
            if (layoutFondoInicio != null) layoutFondoInicio.setVisibility(View.VISIBLE);
            if (layoutBotonesInicio != null) layoutBotonesInicio.setVisibility(View.VISIBLE);
            
            verificarSiMostrarConfiguracionObligatoria();
            return;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String urlCache = prefs.getString(KEY_CACHE_URL_ORIGEN, "");
        if (!url.equals(urlCache) || listaGlobalCanales.isEmpty()) {
            urlListaActualEnUso = url;
            cargarListaDesdeUrl(url);
            Toast.makeText(MainActivity.this, "✅ ACCESO PREMIUM ACTIVADO", Toast.LENGTH_LONG).show();
        }
    }

    private void bloquearPorCuentaInactiva() {
        runOnUiThread(() -> {
            // Detener reproductores inmediatamente
            if (player != null) player.stop();
            if (playerMini != null) playerMini.stop();
            listaGlobalCanales.clear();
            listaFiltradaCanales.clear();
            
            new AlertDialog.Builder(this)
                .setTitle("⚠️ CUENTA SUSPENDIDA")
                .setMessage("Su abono ha expirado o la cuenta ha sido desactivada.\n\nContacte a su proveedor para renovar el servicio.")
                .setCancelable(false)
                .setPositiveButton("SALIR", (d, w) -> finishAffinity())
                .show();
        });
    }

    private void bloquearPorExcesoDePantallas(int limite) {
        runOnUiThread(() -> {
            // Detener reproductores inmediatamente
            if (player != null) player.stop();
            if (playerMini != null) playerMini.stop();
            listaGlobalCanales.clear();
            listaFiltradaCanales.clear();

            new AlertDialog.Builder(this)
                .setTitle("🚫 LÍMITE DE PANTALLAS")
                .setMessage("Esta cuenta ya está siendo usada en " + limite + " equipos.\n\nCierre la app en otro dispositivo o amplíe su plan de abono.")
                .setCancelable(false)
                .setPositiveButton("IR AL INICIO", (d, w) -> {
                    equipoActivadoRemotamente = false;
                    if (layoutFondoInicio != null) layoutFondoInicio.setVisibility(View.VISIBLE);
                    if (layoutBotonesInicio != null) {
                        layoutBotonesInicio.setVisibility(View.VISIBLE);
                        // Forzamos el foco en el botón de canales para que el control remoto funcione
                        findViewById(R.id.btn_inicio_canales).requestFocus();
                    }
                })
                .show();
        });
    }

    private void registrarNuevoEquipoAutomatico() {
        String modeloEquipo = android.os.Build.MANUFACTURER.toUpperCase() + " " + android.os.Build.MODEL;
        mDatabase.child("clientes").child(androidIdUnico).child("nombre").setValue(modeloEquipo + " (Nuevo)");
        mDatabase.child("clientes").child(androidIdUnico).child("activo").setValue(false);
        mDatabase.child("clientes").child(androidIdUnico).child("url_lista").setValue("");
        mDatabase.child("clientes").child(androidIdUnico).child("pertenece_a").setValue("");
        mDatabase.child("clientes").child(androidIdUnico).child("timestamp_registro").setValue(ServerValue.TIMESTAMP);
        equipoActivadoRemotamente = false;
        verificarSiMostrarConfiguracionObligatoria();
    }

    private void verificarConexionInicial() {
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        android.net.NetworkInfo activeNetwork = (cm != null) ? cm.getActiveNetworkInfo() : null;
        boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();

        if (isConnected) {
            String nombreRed = "Internet";
            try {
                if (activeNetwork.getType() == android.net.ConnectivityManager.TYPE_WIFI) {
                    android.net.wifi.WifiManager wifiManager = (android.net.wifi.WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                    if (wifiManager != null) {
                        android.net.wifi.WifiInfo info = wifiManager.getConnectionInfo();
                        if (info != null && info.getSSID() != null && !info.getSSID().equals("<unknown ssid>")) {
                            nombreRed = info.getSSID().replace("\"", "");
                        } else {
                            nombreRed = "WiFi";
                        }
                    }
                } else if (activeNetwork.getType() == android.net.ConnectivityManager.TYPE_ETHERNET) {
                    nombreRed = "Cable LAN";
                }
            } catch (Exception e) {
                nombreRed = "Conectado";
            }
            mostrarNotificacionRed(true, "Conectado a " + nombreRed);
        } else {
            mostrarNotificacionRed(false, "Sin conexión a Internet");
        }
    }

    private void monitorearCambiosRed() {
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            cm.registerDefaultNetworkCallback(new android.net.ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(android.net.Network network) {
                    runOnUiThread(() -> {
                        mostrarNotificacionRed(true, "Conexión restablecida");
                    });
                }

                @Override
                public void onLost(android.net.Network network) {
                    runOnUiThread(() -> {
                        mostrarNotificacionRed(false, "Sin conexión a Internet");
                    });
                }
            });
        }
    }

    private void mostrarNotificacionRed(boolean conectado, String mensaje) {
        if (layoutEstadoRed == null || imgIconoRed == null || textEstadoRed == null) return;

        layoutEstadoRed.setVisibility(View.VISIBLE);
        textEstadoRed.setText(mensaje);
        
        if (conectado) {
            imgIconoRed.setColorFilter(android.graphics.Color.GREEN);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (layoutEstadoRed != null) layoutEstadoRed.setVisibility(View.GONE);
            }, 5000);
        } else {
            imgIconoRed.setColorFilter(android.graphics.Color.RED);
        }
    }

    private void verificarSiMostrarConfiguracionObligatoria() {
        // Solo mostramos el cartel si:
        // 1. Firebase terminó de cargar y dice que NO es premium.
        // 2. No hay listas locales guardadas.
        // 3. El diálogo no está ya abierto.
        if (!equipoActivadoRemotamente && urlsDeListasGuardadas.isEmpty()) {
            if (dialogoConfiguracionActual == null || !dialogoConfiguracionActual.isShowing()) {
                solicitarNuevaLista(true);
            }
        }
    }

    private void mostrarDialogoSalir() {
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
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            boolean menusVisibles = (contenedorMenus != null && contenedorMenus.getVisibility() == View.VISIBLE);
            boolean configVisible = (contenedorConfiguracion != null && contenedorConfiguracion.getVisibility() == View.VISIBLE);
            boolean inicioVisible = (layoutBotonesInicio != null && layoutBotonesInicio.getVisibility() == View.VISIBLE);

            if (!yaSeEjecutoLargoOk && !menusVisibles && !configVisible && !inicioVisible) {
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
        if (urlM3u == null || urlM3u.isEmpty()) return;
        
        runOnUiThread(() -> Toast.makeText(this, "⌛ CARGANDO LISTA, POR FAVOR ESPERE...", Toast.LENGTH_LONG).show());

        // Convertir automáticamente si es un enlace de Google Drive
        final String urlFinal = urlM3u.contains("drive.google.com") ? convertirEnlaceGoogleDriveADirecto(urlM3u) : urlM3u;

        new Thread(() -> {
            try {
                URL url = new URL(urlFinal);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0");

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String linea;

                String nombreCanal = "Canal Libre";
                String grupoCanal = "Otros";
                String urlLogo = "";
                String ultimaLicenciaDRM = "";

                List<CanalEstructura> listaTemporal = new ArrayList<>();
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

                        listaTemporal.add(nuevoCanal);
                        if (!grupoCanal.isEmpty()) setDeGruposUnicos.add(grupoCanal);

                        nombreCanal = "Canal Libre";
                        grupoCanal = "Otros";
                        urlLogo = "";
                        ultimaLicenciaDRM = "";
                    }
                }
                reader.close();
                connection.disconnect();

                // LÓGICA DE AUTO-DERIVACIÓN (PUENTE)
                if (listaTemporal.size() == 1) {
                    String urlUnica = listaTemporal.get(0).urlStream;
                    if (urlUnica.contains(".m3u") || urlUnica.contains("export=download") || urlUnica.contains("drive.google.com")) {
                        cargarListaDesdeUrl(urlUnica);
                        return;
                    }
                }

                if (!listaTemporal.isEmpty()) {
                    Collections.sort(listaTemporal, new Comparator<CanalEstructura>() {
                        @Override
                        public int compare(CanalEstructura c1, CanalEstructura c2) {
                            return c1.nombreOrdenado.compareTo(c2.nombreOrdenado);
                        }
                    });

                    // Guardar en cache antes de mostrar
                    guardarCacheCanales(listaTemporal, urlM3u);

                    List<String> gruposOrdenados = new ArrayList<>(setDeGruposUnicos);
                    Collections.sort(gruposOrdenados, String.CASE_INSENSITIVE_ORDER);

                    runOnUiThread(() -> {
                        procesarListaTerminada(listaTemporal, gruposOrdenados);
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

    private void procesarListaTerminada(List<CanalEstructura> lista, List<String> gruposOrdenados) {
        listaGlobalCanales.clear();
        listaGlobalCanales.addAll(lista);
        
        listaDeGruposVisibles.clear();
        listaDeGruposVisibles.add("[ TODOS LOS CANALES ]");
        listaDeGruposVisibles.add("⭐ [ FAVORITOS ]");
        
        for (String g : gruposOrdenados) {
            if (mostrarContenidoAdulto || !esGrupoAdulto(g)) {
                listaDeGruposVisibles.add(g);
            }
        }

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
            reproducirCanalAlInicio(canalAIniciar);
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
    }

    private void reproducirCanalAlInicio(CanalEstructura canal) {
        reproducirCanalEstable(canal);
        
        // Timeout de 8 segundos para el canal inicial
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (player != null && player.getPlaybackState() != Player.STATE_READY && player.getPlaybackState() != Player.STATE_BUFFERING) {
                if (layoutFondoInicio != null && layoutFondoInicio.getVisibility() == View.VISIBLE) {
                    Toast.makeText(this, "⚠️ Canal no disponible, elija otro", Toast.LENGTH_LONG).show();
                    alternarMenuCanales();
                }
            }
        }, 8000);
    }

    private void guardarCacheCanales(List<CanalEstructura> lista, String urlOrigen) {
        try {
            JSONArray array = new JSONArray();
            for (CanalEstructura c : lista) {
                JSONObject obj = new JSONObject();
                obj.put("u", c.urlStream);
                obj.put("l", c.licenseKey);
                obj.put("n", c.nombreCanal);
                obj.put("g", c.grupoCanal);
                obj.put("i", c.urlLogo);
                obj.put("t", c.tipoMime);
                array.put(obj);
            }
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_CACHE_CANALES, array.toString())
                    .putString(KEY_CACHE_URL_ORIGEN, urlOrigen)
                    .apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void cargarCacheSiExiste() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_CACHE_CANALES, "");
        if (json.isEmpty()) return;

        try {
            JSONArray array = new JSONArray(json);
            List<CanalEstructura> lista = new ArrayList<>();
            Set<String> grupos = new HashSet<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                CanalEstructura c = new CanalEstructura();
                c.urlStream = obj.getString("u");
                c.licenseKey = obj.optString("l", "");
                c.nombreCanal = obj.getString("n");
                c.grupoCanal = obj.getString("g");
                c.urlLogo = obj.optString("i", "");
                c.tipoMime = obj.optString("t", null);
                
                String limpio = c.nombreCanal.toLowerCase().replaceAll("^[^a-zA-Z0-9áéíóúñ]+", "").trim();
                c.nombreOrdenado = limpio.isEmpty() ? c.nombreCanal.toLowerCase() : limpio;
                
                lista.add(c);
                grupos.add(c.grupoCanal);
            }
            
            List<String> gruposOrdenados = new ArrayList<>(grupos);
            Collections.sort(gruposOrdenados, String.CASE_INSENSITIVE_ORDER);
            
            procesarListaTerminada(lista, gruposOrdenados);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Al regresar del standby, si había un canal sintonizado, lo reiniciamos
        if (player != null && canalActualReproduciendo != null && !player.isPlaying()) {
            reproducirCanalEstable(canalActualReproduciendo, player);
        }
        if (playerMini != null && canalMiniReproduciendo != null) {
            reproducirCanalEstable(canalMiniReproduciendo, playerMini);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // CRÍTICO PARA TV: Liberar el decodificador de hardware inmediatamente
        // para evitar que se bloquee el sistema al apagar el televisor.
        if (player != null) {
            player.stop();
            player.clearMediaItems();
        }
        if (playerMini != null) {
            playerMini.stop();
            playerMini.clearMediaItems();
        }
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