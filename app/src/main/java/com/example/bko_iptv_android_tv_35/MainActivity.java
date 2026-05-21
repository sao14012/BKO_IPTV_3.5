package com.example.bko_iptv_android_tv_35;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.KeyEvent;
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
    private ListView listViewCanales;
    private ListView listViewGrupos;
    private ExoPlayer player;
    private ImageView imagenSplash;

    private LinearLayout contenedorMenus;
    private LinearLayout contenedorConfiguracion;
    private ListView listViewConfiguracion;

    private TextView textNombreListaCabecera;
    private EditText inputBuscadorTiempoReal;
    private String nombreListaActualEnUso = "BKO IPTV";

    private static class CanalEstructura {
        String urlStream;
        String licenseKey;
        String TEXT_DESTAQUE_AZUL = "#0057B8"; // Azul de alto contraste para Android TV
        String nombreCanal;
        String nombreOrdenado;
        String groupCanal;
        String urlLogo;
        String tipoMime;
    }

    private List<CanalEstructura> listaGlobalCanales = new ArrayList<>();
    private List<CanalEstructura> listaFiltradaCanales = new ArrayList<>();
    private List<String> listaDeGruposVisibles = new ArrayList<>();
    private String grupoSeleccionadoActual = "[ TODOS LOS CANALES ]";

    private boolean ocultarCanalesAdultos = false;
    private String pinControlParental = "0000";

    private static final String PREFS_NAME = "BkoPrefsPro";
    private static final String KEY_LISTAS_JSON = "listas_iptv_pro_json";
    private static final String KEY_ULTIMA_URL = "ultima_url_sintonizada";
    private static final String KEY_FAVORITOS_SET = "favoritos_canales_urls";
    private static final String KEY_PIN_PARENTAL = "pin_control_parental_v3";
    private static final String KEY_OCULTAR_ADULTOS = "ocultar_adultos_bool";

    private List<String> nombresDeListasGuardadas = new ArrayList<>();
    private List<String> urlsDeListasGuardadas = new ArrayList<>();
    private Set<String> setDeCanalesFavoritos = new HashSet<>();

    private final Handler reintentoHandler = new Handler(Looper.getMainLooper());
    private String urlListaActualEnUso = "";

    private long tiempoPresionadoOk = 0;
    private boolean yaSeEjecutoLargoOk = false;
    private long tiempoPresionadoArriba = 0;
    private boolean yaSeEjecutoLargoArriba = false;
    private long tiempoPresionadoIzquierda = 0;
    private boolean yaSeEjecutoLargoIzquierda = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        playerView = findViewById(R.id.player_view_firme);
        listViewCanales = findViewById(R.id.list_view_canales);
        listViewGrupos = findViewById(R.id.list_view_grupos);
        imagenSplash = findViewById(R.id.imagen_splash);

        contenedorMenus = findViewById(R.id.contenedor_menus);
        contenedorConfiguracion = findViewById(R.id.contenedor_configuracion);
        listViewConfiguracion = findViewById(R.id.list_view_configuracion);

        textNombreListaCabecera = findViewById(R.id.text_nombre_lista_cabecera);
        inputBuscadorTiempoReal = findViewById(R.id.input_buscador_canales);

        if (inputBuscadorTiempoReal != null) {
            inputBuscadorTiempoReal.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEARCH || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    v.clearFocus();
                    return true;
                }
                return false;
            });
        }
        playerView.setKeepScreenOn(true);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        setDeCanalesFavoritos = prefs.getStringSet(KEY_FAVORITOS_SET, new HashSet<>());
        pinControlParental = prefs.getString(KEY_PIN_PARENTAL, "0000");
        ocultarCanalesAdultos = prefs.getBoolean(KEY_OCULTAR_ADULTOS, false);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (imagenSplash != null) imagenSplash.setVisibility(View.GONE);
        }, 3000);

        try {
            DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                    .setBufferDurationsMs(15000, 50000, 2500, 5000).build();

            player = new ExoPlayer.Builder(this).setLoadControl(loadControl).build();
            playerView.setPlayer(player);
            playerView.setUseController(false);
            player.setRepeatMode(Player.REPEAT_MODE_OFF);

            player.addListener(new Player.Listener() {
                @Override
                public void onPlayerError(PlaybackException error) {
                    Throwable cause = error.getCause();
                    String msg = cause != null ? cause.getMessage() : error.getErrorCodeName();
                    Toast.makeText(MainActivity.this, "Fallo de señal (" + msg + "). Reconectando...", Toast.LENGTH_SHORT).show();

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

            playerView.setOnClickListener(v -> alternarMenuCanales());
            playerView.setOnLongClickListener(v -> {
                mostrarMenuDerechoNativo();
                return true;
            });

            listViewCanales.setOnItemClickListener((parent, view, position, id) -> {
                CanalEstructura seleccionado = listaFiltradaCanales.get(position);
                if (seleccionado.nombreCanal.equals("⬅️ VOLVER A GRUPOS")) {
                    listViewCanales.setVisibility(View.GONE);
                    listViewGrupos.setVisibility(View.VISIBLE);
                    listViewGrupos.requestFocus();
                    return;
                }

                if (esCanalAdulto(seleccionado)) {
                    solicitarPinAcceso(seleccionado);
                } else {
                    reproducirCanalEstable(seleccionado);
                    limpiarBuscadorOcultarMenus();
                }
            });

            listViewGrupos.setOnItemClickListener((parent, view, position, id) -> {
                if (inputBuscadorTiempoReal != null) inputBuscadorTiempoReal.setText("");
                grupoSeleccionadoActual = listaDeGruposVisibles.get(position);
                aplicarFiltroDeGrupo(grupoSeleccionadoActual);

                listViewGrupos.setVisibility(View.GONE);
                listViewCanales.setVisibility(View.VISIBLE);
                listViewCanales.requestFocus();
            });

            listViewConfiguracion.setOnItemClickListener((parent, view, position, id) -> {
                if (position == 0) {
                    mostrarPanelAdministradorListas();
                } else if (position == 1) {
                    alternarFavoritoCanalActual();
                    mostrarMenuDerechoNativo();
                } else if (position == 2) {
                    alternarOcultarAdultos();
                } else if (position == 3) {
                    configurarNuevoPinParental();
                }
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

    private boolean esCanalAdulto(CanalEstructura canal) {
        if (canal.groupCanal == null) return false;
        String grupo = canal.groupCanal.toLowerCase();
        String nombre = canal.nombreCanal.toLowerCase();
        return grupo.contains("adultos") || grupo.contains("xxx") || grupo.contains("18+") ||
                nombre.contains("xxx") || nombre.contains("adultos");
    }

    private void solicitarPinAcceso(CanalEstructura canal) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🔒 Control Parental");
        builder.setMessage("Ingrese el PIN de acceso para este canal:");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        builder.setView(input);

        builder.setPositiveButton("Ingresar", (dialog, which) -> {
            String pinIngresado = input.getText().toString();
            if (pinIngresado.equals(pinControlParental)) {
                reproducirCanalEstable(canal);
                limpiarBuscadorOcultarMenus();
            } else {
                Toast.makeText(MainActivity.this, "PIN Incorrecto", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancelar", null);
        builder.show();
    }

    private void mostrarMenuDerechoNativo() {
        if (contenedorMenus != null) contenedorMenus.setVisibility(View.GONE);

        String textoAdultos = ocultarCanalesAdultos ? "🔓 Mostrar Canales Adultos" : "🔒 Ocultar Canales Adultos";
        String[] opciones = {
                "📺 Administrar Listas M3U",
                "⭐ Favorito Canal Actual",
                textoAdultos,
                "🔑 Cambiar PIN Parental"
        };

        ArrayAdapter<String> adapterConfig = new ArrayAdapter<String>(
                this, android.R.layout.simple_list_item_1, opciones) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView text = view.findViewById(android.R.id.text1);

                text.setTextSize(14);
                text.setPadding(25, 30, 25, 30);

                view.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                text.setTextColor(android.graphics.Color.WHITE);

                view.setOnFocusChangeListener((v, hasFocus) -> {
                    if (hasFocus) {
                        v.setBackgroundColor(android.graphics.Color.parseColor("#0057B8"));
                        text.setTextColor(android.graphics.Color.WHITE);
                    } else {
                        v.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                        text.setTextColor(android.graphics.Color.WHITE);
                    }
                });

                return view;
            }
        };

        listViewConfiguracion.setAdapter(adapterConfig);
        if (contenedorConfiguracion != null) {
            contenedorConfiguracion.setVisibility(View.VISIBLE);
            listViewConfiguracion.requestFocus();
        }
    }

    private void alternarOcultarAdultos() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🔒 Confirmar PIN Parental");
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        builder.setView(input);

        builder.setPositiveButton("Verificar", (dialog, which) -> {
            if (input.getText().toString().equals(pinControlParental)) {
                ocultarCanalesAdultos = !ocultarCanalesAdultos;
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_OCULTAR_ADULTOS, ocultarCanalesAdultos).apply();
                Toast.makeText(this, ocultarCanalesAdultos ? "Canales XXX ocultados" : "Canales XXX visibles", Toast.LENGTH_SHORT).show();

                cargarListaDesdeUrl(urlListaActualEnUso);
                mostrarMenuDerechoNativo();
            } else {
                Toast.makeText(this, "PIN Incorrecto", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancelar", null);
        builder.show();
    }

    private void configurarNuevoPinParental() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🔑 Cambiar PIN");
        builder.setMessage("Ingrese el PIN actual:");
        final EditText inputActual = new EditText(this);
        inputActual.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        builder.setView(inputActual);

        builder.setPositiveButton("Siguiente", (dialog, which) -> {
            if (inputActual.getText().toString().equals(pinControlParental)) {
                AlertDialog.Builder builderNuevo = new AlertDialog.Builder(this);
                builderNuevo.setTitle("Nuevo PIN");
                final EditText inputNuevo = new EditText(this);
                inputNuevo.setInputType(InputType.TYPE_CLASS_NUMBER);
                builderNuevo.setView(inputNuevo);
                builderNuevo.setPositiveButton("Guardar", (d, w) -> {
                    String nuevo = inputNuevo.getText().toString().trim();
                    if (nuevo.length() >= 4) {
                        pinControlParental = nuevo;
                        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_PIN_PARENTAL, nuevo).apply();
                        Toast.makeText(this, "PIN Parental Actualizado", Toast.LENGTH_SHORT).show();
                        mostrarMenuDerechoNativo();
                    } else {
                        Toast.makeText(this, "El PIN debe tener mínimo 4 dígitos", Toast.LENGTH_SHORT).show();
                    }
                });
                builderNuevo.show();
            } else {
                Toast.makeText(this, "PIN Incorrecto", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancelar", null);
        builder.show();
    }

    private void reproducirCanalEstable(CanalEstructura canal) {
        if (player == null) return;

        DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory();
        dataSourceFactory.setUserAgent("Mozilla/5.0");

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
        List<String> opcionesMenu = new ArrayList<>(nombresDeListasGuardadas);
        opcionesMenu.add("➕ Agregar Nueva Lista...");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Administrador de Listas IPTV");
        builder.setCancelable(!urlListaActualEnUso.isEmpty());

        String[] items = opcionesMenu.toArray(new String[0]);
        builder.setItems(items, (dialog, index) -> {
            if (index == nombresDeListasGuardadas.size()) {
                solicitarNuevaLista(false);
            } else {
                mostrarOpcionesDeListaEspecifica(index);
            }
        });

        if (!urlListaActualEnUso.isEmpty()) {
            builder.setNegativeButton("Volver", (dialog, which) -> mostrarMenuDerechoNativo());
        }
        builder.show();
    }

    private void mostrarOpcionesDeListaEspecifica(int posicionLista) {
        String nombre = nombresDeListasGuardadas.get(posicionLista);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Opciones: " + nombre);
        String[] opciones = {"📺 Conectar / Reproducir", "✏️ Editar Nombre o URL", "❌ Borrar Lista"};

        builder.setItems(opciones, (dialog, opcionElegida) -> {
            if (opcionElegida == 0) {
                String urlSeleccionada = urlsDeListasGuardadas.get(posicionLista);
                urlListaActualEnUso = urlSeleccionada;
                nombreListaActualEnUso = nombresDeListasGuardadas.get(posicionLista);
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_ULTIMA_URL, urlSeleccionada).apply();
                cargarListaDesdeUrl(urlSeleccionada);
                if (contenedorConfiguracion != null) contenedorConfiguracion.setVisibility(View.GONE);
            } else if (opcionElegida == 1) {
                formularioEditarLista(posicionLista);
            } else if (opcionElegida == 2) {
                ejecutarPrimerAvisoBorrar(posicionLista);
            }
        });
        builder.setNegativeButton("Volver", (dialog, which) -> mostrarPanelAdministradorListas());
        builder.show();
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
        builder.setTitle("🛑 CONFIRMACIÓN FINAL");
        builder.setPositiveButton("SÍ, ELIMINAR", (dialog, which) -> {
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
        builder.setNegativeButton("NO", (dialog, which) -> mostrarPanelAdministradorListas());
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
        layout.addView(inputNombre);

        final EditText inputUrl = new EditText(this);
        inputUrl.setHint("URL m3u");
        layout.addView(inputUrl);

        builder.setView(layout);

        builder.setPositiveButton("Guardar", (dialog, which) -> {
            String nombre = inputNombre.getText().toString().trim();
            String urlStr = inputUrl.getText().toString().trim();

            if (!nombre.isEmpty() && !urlStr.isEmpty()) {
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
        if (contenedorConfiguracion != null) contenedorConfiguracion.setVisibility(View.GONE);
        if (contenedorMenus != null) {
            if (contenedorMenus.getVisibility() == View.VISIBLE) {
                limpiarBuscadorOcultarMenus();
            } else {
                if (textNombreListaCabecera != null) {
                    textNombreListaCabecera.setText("LISTA: " + nombreListaActualEnUso.toUpperCase());
                }
                contenedorMenus.setVisibility(View.VISIBLE);
                listViewGrupos.setVisibility(View.VISIBLE);
                listViewCanales.setVisibility(View.GONE);
                listViewGrupos.requestFocus();
            }
        }
    }

    private void limpiarBuscadorOcultarMenus() {
        if (inputBuscadorTiempoReal != null) inputBuscadorTiempoReal.setText("");
        if (contenedorMenus != null) contenedorMenus.setVisibility(View.GONE);
        if (contenedorConfiguracion != null) contenedorConfiguracion.setVisibility(View.GONE);
        listViewCanales.setVisibility(View.GONE);
        listViewGrupos.setVisibility(View.GONE);
    }

    private void ejecutarFiltradoEnTiempoReal(String texto) {
        String consulta = texto.trim().toLowerCase();
        listaFiltradaCanales.clear();

        if (consulta.isEmpty()) {
            aplicarFiltroDeGrupo(grupoSeleccionadoActual);
            return;
        }

        listViewGrupos.setVisibility(View.GONE);
        listViewCanales.setVisibility(View.VISIBLE);

        CanalEstructura celdaAtras = new CanalEstructura();
        celdaAtras.nombreCanal = "⬅️ VOLVER A GRUPOS";
        celdaAtras.groupCanal = "";
        listaFiltradaCanales.add(celdaAtras);

        if (textNombreListaCabecera != null) {
            textNombreListaCabecera.setText("BUSCANDO: " + consulta.toUpperCase());
        }

        for (CanalEstructura canal : listaGlobalCanales) {
            if (ocultarCanalesAdultos && esCanalAdulto(canal)) continue;
            if (canal.nombreCanal.toLowerCase().contains(consulta)) {
                listaFiltradaCanales.add(canal);
            }
        }
        aplicarFiltroDirectoBuscador();
    }

    private void mostrarGuiaRapidaComandos() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("📖 Guía de Comandos Remotos");

        StringBuilder mensaje = new StringBuilder();
        mensaje.append("• Click Corto OK: Abrir menú de Canales\n");
        mensaje.append("• Click Largo OK: ⚙️ Panel de Configuración Completo\n\n");
        mensaje.append("PANTALLA COMPLETA:\n");
        mensaje.append("• Flecha Izquierda (Corto): Categorías\n");
        mensaje.append("• Flecha Izquierda (Largo): Ver esta Guía de Comandos\n");
        mensaje.append("• Flecha Derecha (Corto): Entrar a Favoritos de una\n");
        mensaje.append("• Flecha Arriba (Corto): Listado del grupo actual\n");
        mensaje.append("• Flecha Arriba (Largo): 🔍 Ir directo al Buscador\n");
        mensaje.append("• Flecha Abajo (Corto): Info del canal activo");

        builder.setMessage(mensaje.toString());
        builder.setPositiveButton("Entendido", null);
        builder.show();
    }

    private void alternarFavoritoCanalActual() {
        if (player != null && player.getCurrentMediaItem() != null) {
            String urlIdActual = player.getCurrentMediaItem().mediaId;
            CharSequence tituloCanal = player.getCurrentMediaItem().mediaMetadata.title;

            if (setDeCanalesFavoritos.contains(urlIdActual)) {
                setDeCanalesFavoritos.remove(urlIdActual);
                Toast.makeText(this, "❌ Quitado de Favoritos: " + tituloCanal, Toast.LENGTH_SHORT).show();
            } else {
                setDeCanalesFavoritos.add(urlIdActual);
                Toast.makeText(this, "⭐ Agregado a Favoritos: " + tituloCanal, Toast.LENGTH_SHORT).show();
            }

            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putStringSet(KEY_FAVORITOS_SET, setDeCanalesFavoritos).apply();

            aplicarFiltroDeGrupo(grupoSeleccionadoActual);
        } else {
            Toast.makeText(this, "No hay canales reproduciéndose", Toast.LENGTH_SHORT).show();
        }
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
                    filaLayout.setPadding(20, 25, 20, 25);
                    filaLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);

                    ImageView imgLogo = new ImageView(getContext());
                    imgLogo.setId(View.generateViewId());
                    LinearLayout.LayoutParams lpImg = new LinearLayout.LayoutParams(55, 55);
                    lpImg.rightMargin = 15;
                    imgLogo.setLayoutParams(lpImg);
                    filaLayout.addView(imgLogo);

                    TextView textNombre = new TextView(getContext());
                    textNombre.setId(View.generateViewId());
                    textNombre.setTextColor(android.graphics.Color.WHITE);
                    textNombre.setTextSize(14);
                    filaLayout.addView(textNombre);
                } else {
                    filaLayout = (LinearLayout) convertView;
                }

                ImageView logoView = filaLayout.findViewById(filaLayout.getChildAt(0).getId());
                TextView nameView = filaLayout.findViewById(filaLayout.getChildAt(1).getId());

                CanalEstructura actual = getItem(position);
                nameView.setText(actual.nombreCanal);
                nameView.setTextSize(14);

                if (actual.nombreCanal.equals("⬅️ VOLVER A GRUPOS")) {
                    logoView.setVisibility(View.GONE);
                    nameView.setTextColor(android.graphics.Color.parseColor("#00FF00"));
                } else {
                    nameView.setTextColor(android.graphics.Color.WHITE);
                    if (setDeCanalesFavoritos.contains(actual.urlStream)) {
                        nameView.setText("⭐ " + actual.nombreCanal);
                    }

                    logoView.setVisibility(View.VISIBLE);
                    if (actual.urlLogo != null && !actual.urlLogo.isEmpty()) {
                        Glide.with(getContext())
                                .load(actual.urlLogo)
                                .placeholder(android.R.drawable.ic_menu_slideshow)
                                .error(android.R.drawable.ic_menu_slideshow)
                                .into(logoView);
                    } else {
                        logoView.setImageResource(android.R.drawable.ic_menu_slideshow);
                    }
                }

                filaLayout.setOnFocusChangeListener((v, hasFocus) -> {
                    if (hasFocus) {
                        v.setBackgroundColor(android.graphics.Color.parseColor("#0057B8"));
                        nameView.setTextColor(android.graphics.Color.WHITE);
                    } else {
                        v.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                        if (actual.nombreCanal.equals("⬅️ VOLVER A GRUPOS")) {
                            nameView.setTextColor(android.graphics.Color.parseColor("#00FF00"));
                        } else {
                            nameView.setTextColor(android.graphics.Color.WHITE);
                        }
                    }
                });

                return filaLayout;
            }
        };
        listViewCanales.setAdapter(adapter);
    }

    private void aplicarFiltroDeGrupo(String group) {
        listaFiltradaCanales.clear();

        CanalEstructura celdaAtras = new CanalEstructura();
        celdaAtras.nombreCanal = "⬅️ VOLVER A GRUPOS";
        celdaAtras.groupCanal = "";
        listaFiltradaCanales.add(celdaAtras);

        if (textNombreListaCabecera != null) {
            if (group.equals("⭐ [ FAVORITOS ]")) {
                textNombreListaCabecera.setText("SECCIÓN: FAVORITOS");
            } else {
                textNombreListaCabecera.setText("CATEGORÍA: " + group.toUpperCase());
            }
        }

        for (CanalEstructura canal : listaGlobalCanales) {
            if (ocultarCanalesAdultos && esCanalAdulto(canal)) continue;

            if (group.equals("[ TODOS LOS CANALES ]")) {
                listaFiltradaCanales.add(canal);
            } else if (group.equals("⭐ [ FAVORITOS ]")) {
                if (setDeCanalesFavoritos.contains(canal.urlStream)) {
                    listaFiltradaCanales.add(canal);
                }
            } else if (canal.groupCanal != null && canal.groupCanal.equalsIgnoreCase(group)) {
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
                    filaLayout.setPadding(20, 25, 20, 25);
                    filaLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);

                    ImageView imgLogo = new ImageView(getContext());
                    imgLogo.setId(View.generateViewId());
                    LinearLayout.LayoutParams lpImg = new LinearLayout.LayoutParams(55, 55);
                    lpImg.rightMargin = 15;
                    imgLogo.setLayoutParams(lpImg);
                    filaLayout.addView(imgLogo);

                    TextView textNombre = new TextView(getContext());
                    textNombre.setId(View.generateViewId());
                    textNombre.setTextColor(android.graphics.Color.WHITE);
                    textNombre.setTextSize(14);
                    filaLayout.addView(textNombre);
                } else {
                    filaLayout = (LinearLayout) convertView;
                }

                ImageView logoView = filaLayout.findViewById(filaLayout.getChildAt(0).getId());
                TextView nameView = filaLayout.findViewById(filaLayout.getChildAt(1).getId());

                CanalEstructura actual = getItem(position);
                nameView.setText(actual.nombreCanal);
                nameView.setTextSize(14);

                if (actual.nombreCanal.equals("⬅️ VOLVER A GRUPOS")) {
                    logoView.setVisibility(View.GONE);
                    nameView.setTextColor(android.graphics.Color.parseColor("#00FF00"));
                } else {
                    nameView.setTextColor(android.graphics.Color.WHITE);
                    if (setDeCanalesFavoritos.contains(actual.urlStream)) {
                        nameView.setText("⭐ " + actual.nombreCanal);
                    }

                    logoView.setVisibility(View.VISIBLE);
                    if (actual.urlLogo != null && !actual.urlLogo.isEmpty()) {
                        Glide.with(getContext())
                                .load(actual.urlLogo)
                                .placeholder(android.R.drawable.ic_menu_slideshow)
                                .error(android.R.drawable.ic_menu_slideshow)
                                .into(logoView);
                    } else {
                        logoView.setImageResource(android.R.drawable.ic_menu_slideshow);
                    }
                }

                filaLayout.setOnFocusChangeListener((v, hasFocus) -> {
                    if (hasFocus) {
                        v.setBackgroundColor(android.graphics.Color.parseColor("#0057B8"));
                        nameView.setTextColor(android.graphics.Color.WHITE);
                    } else {
                        v.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                        if (actual.nombreCanal.equals("⬅️ VOLVER A GRUPOS")) {
                            nameView.setTextColor(android.graphics.Color.parseColor("#00FF00"));
                        } else {
                            nameView.setTextColor(android.graphics.Color.WHITE);
                        }
                    }
                });

                return filaLayout;
            }
        };

        listViewCanales.setAdapter(adapter);
    }

    private void mostrarCartelConfirmarSalida() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Cerrar Aplicación");
        builder.setMessage("¿Desea cerrar la app?");
        builder.setPositiveButton("SÍ", (dialog, which) -> finish());
        builder.setNegativeButton("NO", null);
        builder.show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean menusVisibles = (contenedorMenus != null && contenedorMenus.getVisibility() == View.VISIBLE);
        boolean configVisible = (contenedorConfiguracion != null && contenedorConfiguracion.getVisibility() == View.VISIBLE);

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (configVisible) {
                contenedorConfiguracion.setVisibility(View.GONE);
                return true;
            }
            if (menusVisibles) {
                if (listViewCanales.getVisibility() == View.VISIBLE && listViewGrupos.getVisibility() == View.GONE) {
                    listViewCanales.setVisibility(View.GONE);
                    listViewGrupos.setVisibility(View.VISIBLE);
                    listViewGrupos.requestFocus();
                } else {
                    limpiarBuscadorOcultarMenus();
                }
                return true;
            } else {
                mostrarCartelConfirmarSalida();
                return true;
            }
        }

        if (!menusVisibles && !configVisible) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (tiempoPresionadoIzquierda == 0) {
                    tiempoPresionadoIzquierda = System.currentTimeMillis();
                    yaSeEjecutoLargoIzquierda = false;
                }
                if (!yaSeEjecutoLargoIzquierda && (System.currentTimeMillis() - tiempoPresionadoIzquierda) > 800) {
                    yaSeEjecutoLargoIzquierda = true;
                    mostrarGuiaRapidaComandos();
                }
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (tiempoPresionadoArriba == 0) {
                    tiempoPresionadoArriba = System.currentTimeMillis();
                    yaSeEjecutoLargoArriba = false;
                }
                if (!yaSeEjecutoLargoArriba && (System.currentTimeMillis() - tiempoPresionadoArriba) > 800) {
                    yaSeEjecutoLargoArriba = true;

                    if (textNombreListaCabecera != null) textNombreListaCabecera.setText("BUSCAR CANAL");
                    if (contenedorMenus != null) contenedorMenus.setVisibility(View.VISIBLE);
                    listViewGrupos.setVisibility(View.GONE);
                    listViewCanales.setVisibility(View.VISIBLE);

                    if (inputBuscadorTiempoReal != null) inputBuscadorTiempoReal.requestFocus();
                }
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (player != null && player.getCurrentMediaItem() != null) {
                    CharSequence tituloCanal = player.getCurrentMediaItem().mediaMetadata.title;
                    Toast.makeText(this, "📺 Sintonizado: " + tituloCanal + "\n📌 Guía de programación (EPG) no disponible", Toast.LENGTH_LONG).show();
                }
                return true;
            }
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (tiempoPresionadoOk == 0) {
                tiempoPresionadoOk = System.currentTimeMillis();
                yaSeEjecutoLargoOk = false;
            }
            if (!yaSeEjecutoLargoOk && (System.currentTimeMillis() - tiempoPresionadoOk) > 800) {
                if (!menusVisibles && !configVisible) {
                    yaSeEjecutoLargoOk = true;
                    mostrarMenuDerechoNativo();
                }
            }
            return true;
        }

        if (menusVisibles && !configVisible) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && listViewGrupos.getVisibility() == View.VISIBLE) {
                listViewGrupos.setVisibility(View.GONE);
                listViewCanales.setVisibility(View.VISIBLE);
                listViewCanales.requestFocus();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && listViewCanales.getVisibility() == View.VISIBLE) {
                listViewCanales.setVisibility(View.GONE);
                listViewGrupos.setVisibility(View.VISIBLE);
                listViewGrupos.requestFocus();
                return true;
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        boolean menusVisibles = (contenedorMenus != null && contenedorMenus.getVisibility() == View.VISIBLE);
        boolean configVisible = (contenedorConfiguracion != null && contenedorConfiguracion.getVisibility() == View.VISIBLE);

        if (configVisible) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                int pos = listViewConfiguracion.getSelectedItemPosition();
                if (pos != ListView.INVALID_POSITION) {
                    listViewConfiguracion.performItemClick(listViewConfiguracion.getSelectedView(), pos, listViewConfiguracion.getItemIdAtPosition(pos));
                }
                return true;
            }
            return super.onKeyUp(keyCode, event);
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && !menusVisibles) {
            long duracionClick = System.currentTimeMillis() - tiempoPresionadoIzquierda;
            tiempoPresionadoIzquierda = 0;
            if (!yaSeEjecutoLargoIzquierda && duracionClick < 800) {
                if (textNombreListaCabecera != null) {
                    textNombreListaCabecera.setText("LISTA: " + nombreListaActualEnUso.toUpperCase());
                }
                if (inputBuscadorTiempoReal != null) inputBuscadorTiempoReal.setText("");
                contenedorMenus.setVisibility(View.VISIBLE);
                listViewCanales.setVisibility(View.GONE);
                listViewGrupos.setVisibility(View.VISIBLE);
                listViewGrupos.requestFocus();
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && !menusVisibles) {
            grupoSeleccionadoActual = "⭐ [ FAVORITOS ]";
            aplicarFiltroDeGrupo(grupoSeleccionadoActual);
            if (textNombreListaCabecera != null) {
                textNombreListaCabecera.setText("SECCIÓN: FAVORITOS");
            }
            if (inputBuscadorTiempoReal != null) inputBuscadorTiempoReal.setText("");
            contenedorMenus.setVisibility(View.VISIBLE);
            listViewGrupos.setVisibility(View.GONE);
            listViewCanales.setVisibility(View.VISIBLE);
            listViewCanales.requestFocus();
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_UP && !menusVisibles) {
            long duracionClick = System.currentTimeMillis() - tiempoPresionadoArriba;
            tiempoPresionadoArriba = 0;
            if (!yaSeEjecutoLargoArriba && duracionClick < 800) {
                if (textNombreListaCabecera != null) {
                    textNombreListaCabecera.setText("CATEGORÍA: " + grupoSeleccionadoActual.toUpperCase());
                }
                if (inputBuscadorTiempoReal != null) inputBuscadorTiempoReal.setText("");
                contenedorMenus.setVisibility(View.VISIBLE);
                listViewGrupos.setVisibility(View.GONE);
                listViewCanales.setVisibility(View.VISIBLE);
                listViewCanales.requestFocus();
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            long duracionClick = System.currentTimeMillis() - tiempoPresionadoOk;
            tiempoPresionadoOk = 0;
            if (!yaSeEjecutoLargoOk && duracionClick < 800) {
                if (!menusVisibles) {
                    alternarMenuCanales();
                } else {
                    if (listViewGrupos.hasFocus()) {
                        int pos = listViewGrupos.getSelectedItemPosition();
                        if (pos != ListView.INVALID_POSITION) listViewGrupos.performItemClick(listViewGrupos.getSelectedView(), pos, listViewGrupos.getItemIdAtPosition(pos));
                    } else if (listViewCanales.hasFocus()) {
                        int pos = listViewCanales.getSelectedItemPosition();
                        if (pos != ListView.INVALID_POSITION) listViewCanales.performItemClick(listViewCanales.getSelectedView(), pos, listViewCanales.getItemIdAtPosition(pos));
                    }
                }
            }
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void cargarListaDesdeUrl(String urlM3u) {
        new Thread(() -> {
            try {
                URL url = new URL(urlM3u);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0");

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String linea;

                String nombreCanal = "Canal Libre";
                String groupCanal = "Otros";
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
                            if (finLogo != -1) urlLogo = linea.substring(inicioLogo, finLogo).trim();
                        }

                        if (linea.contains("group-title=\"")) {
                            int inicio = linea.indexOf("group-title=\"") + 13;
                            int fin = linea.indexOf("\"", inicio);
                            if (fin != -1) groupCanal = linea.substring(inicio, fin).trim();
                        } else {
                            groupCanal = "Otros";
                        }

                        int comaIndex = linea.lastIndexOf(",");
                        if (comaIndex != -1) {
                            nombreCanal = linea.substring(comaIndex + 1).trim();
                        } else {
                            nombreCanal = "Canal Sin Nombre";
                        }
                    } else if (linea.startsWith("http://") || linea.startsWith("https://")) {

                        String tipoMime = MimeTypes.APPLICATION_M3U8;
                        if (linea.contains("/mpegts") || linea.endsWith(".ts") || linea.contains(".ts?") || linea.contains(".mpd")) {
                            tipoMime = MimeTypes.APPLICATION_MPD;
                        }

                        CanalEstructura nuevoCanal = new CanalEstructura();
                        nuevoCanal.urlStream = linea;
                        nuevoCanal.licenseKey = ultimaLicenciaDRM;
                        nuevoCanal.nombreCanal = nombreCanal;
                        nuevoCanal.groupCanal = groupCanal;
                        nuevoCanal.urlLogo = urlLogo;
                        nuevoCanal.tipoMime = tipoMime;

                        String limpio = nombreCanal.toLowerCase()
                                .replaceAll("^[^a-zA-Z0-9áéíóúñ]+", "")
                                .trim();
                        nuevoCanal.nombreOrdenado = limpio.isEmpty() ? nombreCanal.toLowerCase() : limpio;

                        boolean filtrarAdulto = ocultarCanalesAdultos && esCanalAdulto(nuevoCanal);

                        if (!filtrarAdulto) {
                            listaGlobalCanales.add(nuevoCanal);
                            if (!groupCanal.isEmpty()) setDeGruposUnicos.add(groupCanal);
                        }

                        nombreCanal = "Canal Libre";
                        groupCanal = "Otros";
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

                    listaDeGruposVisibles.clear();
                    listaDeGruposVisibles.add("[ TODOS LOS CANALES ]");
                    listaDeGruposVisibles.add("⭐ [ FAVORITOS ]");
                    listaDeGruposVisibles.addAll(setDeGruposUnicos);

                    runOnUiThread(() -> {
                        grupoSeleccionadoActual = "[ TODOS LOS CANALES ]";
                        aplicarFiltroDeGrupo(grupoSeleccionadoActual);

                        if (!listaGlobalCanales.isEmpty()) {
                            reproducirCanalEstable(listaGlobalCanales.get(0));
                        }

                        ArrayAdapter<String> adapterGrupos = new ArrayAdapter<String>(
                                MainActivity.this, android.R.layout.simple_list_item_1, listaDeGruposVisibles) {
                            @Override
                            public View getView(int position, View convertView, ViewGroup parent) {
                                View view = super.getView(position, convertView, parent);
                                TextView text = view.findViewById(android.R.id.text1);
                                text.setTextSize(13);
                                text.setPadding(30, 40, 30, 40);

                                String itemText = getItem(position);

                                int colorBase = android.graphics.Color.parseColor("#FFD700");
                                if (itemText.contains("FAVORITOS")) {
                                    colorBase = android.graphics.Color.parseColor("#00FFFF");
                                } else if (itemText.contains("TODOS")) {
                                    colorBase = android.graphics.Color.parseColor("#FFFFFF");
                                }

                                text.setTextColor(colorBase);
                                view.setBackgroundColor(android.graphics.Color.TRANSPARENT);

                                final int colorOriginal = colorBase;
                                view.setOnFocusChangeListener((v, hasFocus) -> {
                                    if (hasFocus) {
                                        v.setBackgroundColor(android.graphics.Color.parseColor("#0057B8"));
                                        text.setTextColor(android.graphics.Color.WHITE);
                                    } else {
                                        v.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                                        text.setTextColor(colorOriginal);
                                    }
                                });

                                return view;
                            }
                        };
                        listViewGrupos.setAdapter(adapterGrupos);
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
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        reintentoHandler.removeCallbacksAndMessages(null);
        if (player != null) {
            player.release();
            player = null;
        }
    }
}