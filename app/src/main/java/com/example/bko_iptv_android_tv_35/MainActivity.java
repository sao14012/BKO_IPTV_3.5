package com.example.bko_iptv_android_tv_35;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
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
    private TextView textNombreListaCabecera;
    private EditText inputBuscadorTiempoReal;
    private View contenedorConfiguracion;
    private ListView listViewConfiguracion;
    private String nombreListaActualEnUso = "BKO IPTV";

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
    private long tiempoPresionadoDerecha = 0;
    private boolean yaSeEjecutoLargoDerecha = false;

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
        textNombreListaCabecera = findViewById(R.id.text_nombre_lista_cabecera);
        inputBuscadorTiempoReal = findViewById(R.id.input_buscador_canales);
        contenedorConfiguracion = findViewById(R.id.contenedor_configuracion);
        listViewConfiguracion = findViewById(R.id.list_view_configuracion);
        cargarOpcionesConfiguracion();

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

            listViewCanales.setOnItemClickListener((parent, view, position, id) -> {
                CanalEstructura seleccionado = listaFiltradaCanales.get(position);
                reproducirCanalEstable(seleccionado);
                limpiarBuscadorOcultarMenus();
            });

            listViewGrupos.setOnItemClickListener((parent, view, position, id) -> {
                if (inputBuscadorTiempoReal != null) inputBuscadorTiempoReal.setText("");
                grupoSeleccionadoActual = listaDeGruposVisibles.get(position);
                aplicarFiltroDeGrupo(grupoSeleccionadoActual);

                listViewGrupos.setVisibility(View.GONE);
                listViewCanales.setVisibility(View.VISIBLE);
                listViewCanales.requestFocus();
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

    private void reproducirCanalEstable(CanalEstructura canal) {
        if (player == null) return;

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
            builder.setNegativeButton("Cerrar", null);
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
        if (contenedorMenus != null) {
            if (contenedorMenus.getVisibility() == View.VISIBLE) {
                limpiarBuscadorOcultarMenus();
            } else {
                if (textNombreListaCabecera != null) {
                    textNombreListaCabecera.setText("LISTA: " + nombreListaActualEnUso.toUpperCase());
                }
                contenedorMenus.setVisibility(View.VISIBLE);
                listViewCanales.setVisibility(View.VISIBLE);
                listViewCanales.requestFocus();
            }
        }
    }

    private void limpiarBuscadorOcultarMenus() {
        if (inputBuscadorTiempoReal != null) inputBuscadorTiempoReal.setText("");
        if (contenedorMenus != null) contenedorMenus.setVisibility(View.GONE);
        if (listViewCanales != null) listViewCanales.setVisibility(View.GONE);
        if (listViewGrupos != null) listViewGrupos.setVisibility(View.GONE);

        // AGREGAR ESTA LÍNEA AQUÍ ABAJO:
        if (contenedorConfiguracion != null) contenedorConfiguracion.setVisibility(View.GONE);
    }

    private void alternarMenuConfiguracion() {
        if (contenedorConfiguracion == null) return;

        // Si el menú izquierdo está abierto, lo cerramos primero para no superponerlos
        if (contenedorMenus != null && contenedorMenus.getVisibility() == View.VISIBLE) {
            limpiarBuscadorOcultarMenus();
        }

        // Alternamos la visibilidad del menú de configuración
        if (contenedorConfiguracion.getVisibility() == View.VISIBLE) {
            contenedorConfiguracion.setVisibility(View.GONE);
        } else {
            contenedorConfiguracion.setVisibility(View.VISIBLE);
            if (listViewConfiguracion != null) {
                listViewConfiguracion.requestFocus(); // Le damos el foco al control remoto
            }
        }
    }

    private void cargarOpcionesConfiguracion() {
        if (listViewConfiguracion == null) return;

        // 1. Opciones del menú
        String[] opciones = {
                "🔗 Cambiar URL de la Lista",
                "🔄 Recargar Lista IPTV",
                "ℹ️ Información"
        };

        // 2. Adaptador forzando el texto a BLANCO para que no sea invisible
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<String>(
                this,
                android.R.layout.simple_list_item_1,
                opciones
        ) {
            @Override
            public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                android.view.View view = super.getView(position, convertView, parent);
                android.widget.TextView text = (android.widget.TextView) view.findViewById(android.R.id.text1);
                if (text != null) {
                    text.setTextColor(android.graphics.Color.WHITE);
                }
                return view;
            }
        };

        // 3. Asignar el adaptador
        listViewConfiguracion.setAdapter(adapter);

        // 4. Evento de click para los botones
        listViewConfiguracion.setOnItemClickListener((parent, view, position, id) -> {
            switch (position) {
                case 0:
                    android.widget.Toast.makeText(MainActivity.this, "Click en: Cambiar URL (Próximamente)", android.widget.Toast.LENGTH_SHORT).show();
                    break;
                case 1:
                    android.widget.Toast.makeText(MainActivity.this, "🔄 Recargando canales...", android.widget.Toast.LENGTH_SHORT).show();
                    limpiarBuscadorOcultarMenus();
                    cargarListaDesdeUrl("https://gitlab.com/mortal251/ssiptvarg/-/raw/main/primerajunta/cablearg.m3u?ref_type=heads");
                    break;
                case 2:
                    android.widget.Toast.makeText(MainActivity.this, "BKO IPTV v3.5 - Desarrollado para Android TV", android.widget.Toast.LENGTH_LONG).show();
                    break;
            }
        });
    } // <- CIERRA EL MÉTODO PERFECTAMENTE

        private void ejecutarFiltradoEnTiempoReal (String texto){
            String consulta = texto.trim().toLowerCase();
            listaFiltradaCanales.clear();

            if (consulta.isEmpty()) {
                if (textNombreListaCabecera != null) {
                    if (grupoSeleccionadoActual.equals("⭐ [ FAVORITOS ]")) {
                        textNombreListaCabecera.setText("SECCIÓN: FAVORITOS");
                    } else {
                        textNombreListaCabecera.setText("CATEGORÍA: " + grupoSeleccionadoActual.toUpperCase());
                    }
                }
                for (CanalEstructura canal : listaGlobalCanales) {
                    if (grupoSeleccionadoActual.equals("[ TODOS LOS CANALES ]")) {
                        listaFiltradaCanales.add(canal);
                    } else if (grupoSeleccionadoActual.equals("⭐ [ FAVORITOS ]")) {
                        if (setDeCanalesFavoritos.contains(canal.urlStream)) {
                            listaFiltradaCanales.add(canal);
                        }
                    } else if (canal.grupoCanal.equalsIgnoreCase(grupoSeleccionadoActual)) {
                        listaFiltradaCanales.add(canal);
                    }
                }
            } else {
                if (textNombreListaCabecera != null) {
                    textNombreListaCabecera.setText("BUSCANDO: " + consulta.toUpperCase());
                }
                for (CanalEstructura canal : listaGlobalCanales) {
                    if (canal.nombreCanal.toLowerCase().contains(consulta)) {
                        listaFiltradaCanales.add(canal);
                    }
                }
            }
            aplicarFiltroDirectoBuscador();
        }

        private void mostrarGuiaRapidaComandos () {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("📖 Guía de Comandos Remotos");

            StringBuilder mensaje = new StringBuilder();
            mensaje.append("• Click Corto OK: Abrir menú Canales\n");
            mensaje.append("• Click Largo OK: Panel Administrador de Listas\n\n");
            mensaje.append("PANTALLA COMPLETA (Viendo Tele):\n");
            mensaje.append("• Flecha Izquierda (Corto): Abrir menú Categorías\n");
            mensaje.append("• Flecha Izquierda (Largo): Abrir esta Guía de Comandos\n");
            mensaje.append("• Flecha Derecha (Corto): Entrar directo a sección Favoritos\n");
            mensaje.append("• Flecha Derecha (Largo): ⭐ AGREGAR/QUITAR canal de Favoritos\n");
            mensaje.append("• Flecha Arriba (Corto): Abrir canales de la sección actual\n");
            mensaje.append("• Flecha Arriba (Largo): 🔍 Hacer foco en el Buscador Directo\n");
            mensaje.append("• Flecha Abajo (Corto): Ver Info del canal en reproducción\n\n");
            mensaje.append("CON MENÚS ABIERTOS:\n");
            mensaje.append("• Flechas navegan de forma clásica. Flecha Atrás cierra los paneles.");

            builder.setMessage(mensaje.toString());
            builder.setPositiveButton("Entendido", null);
            builder.show();
        }

        private void alternarFavoritoCanalActual () {
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
            }
        }

        private void aplicarFiltroDirectoBuscador () {
            ArrayAdapter<CanalEstructura> adapter = new ArrayAdapter<CanalEstructura>(
                    this, android.R.layout.simple_list_item_1, listaFiltradaCanales) {
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    LinearLayout filaLayout;
                    if (convertView == null) {
                        filaLayout = new LinearLayout(getContext());
                        filaLayout.setOrientation(LinearLayout.HORIZONTAL);
                        filaLayout.setPadding(30, 35, 30, 35);
                        filaLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);

                        ImageView imgLogo = new ImageView(getContext());
                        imgLogo.setId(View.generateViewId());
                        LinearLayout.LayoutParams lpImg = new LinearLayout.LayoutParams(75, 75);
                        lpImg.rightMargin = 25;
                        imgLogo.setLayoutParams(lpImg);
                        filaLayout.addView(imgLogo);

                        TextView textNombre = new TextView(getContext());
                        textNombre.setId(View.generateViewId());
                        textNombre.setTextColor(android.graphics.Color.WHITE);
                        textNombre.setTextSize(16);
                        filaLayout.addView(textNombre);
                    } else {
                        filaLayout = (LinearLayout) convertView;
                    }

                    ImageView logoView = filaLayout.findViewById(filaLayout.getChildAt(0).getId());
                    TextView nameView = filaLayout.findViewById(filaLayout.getChildAt(1).getId());

                    CanalEstructura actual = getItem(position);
                    nameView.setText(actual.nombreCanal);

                    if (setDeCanalesFavoritos.contains(actual.urlStream)) {
                        nameView.setText("⭐ " + actual.nombreCanal);
                    }

                    if (actual.urlLogo != null && !actual.urlLogo.isEmpty()) {
                        logoView.setVisibility(View.VISIBLE);
                        logoView.setTag(actual.urlLogo);
                        new Thread(() -> {
                            try {
                                java.io.InputStream is = new java.net.URL((String) logoView.getTag()).openStream();
                                android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeStream(is);
                                runOnUiThread(() -> {
                                    if (logoView.getTag().equals(actual.urlLogo))
                                        logoView.setImageBitmap(bmp);
                                });
                            } catch (Exception ignored) {
                            }
                        }).start();
                    } else {
                        logoView.setImageResource(android.R.drawable.ic_menu_slideshow);
                    }
                    return filaLayout;
                }
            };
            listViewCanales.setAdapter(adapter);
        }

        private void aplicarFiltroDeGrupo (String group){
            listaFiltradaCanales.clear();

            for (CanalEstructura canal : listaGlobalCanales) {
                if (group.equals("[ TODOS LOS CANALES ]")) {
                    listaFiltradaCanales.add(canal);
                } else if (group.equals("⭐ [ FAVORITOS ]")) {
                    if (setDeCanalesFavoritos.contains(canal.urlStream)) {
                        listaFiltradaCanales.add(canal);
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
                        filaLayout.setPadding(30, 35, 30, 35);
                        filaLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);

                        ImageView imgLogo = new ImageView(getContext());
                        imgLogo.setId(View.generateViewId());
                        LinearLayout.LayoutParams lpImg = new LinearLayout.LayoutParams(75, 75);
                        lpImg.rightMargin = 25;
                        imgLogo.setLayoutParams(lpImg);
                        filaLayout.addView(imgLogo);

                        TextView textNombre = new TextView(getContext());
                        textNombre.setId(View.generateViewId());
                        textNombre.setTextColor(android.graphics.Color.WHITE);
                        textNombre.setTextSize(16);
                        filaLayout.addView(textNombre);
                    } else {
                        filaLayout = (LinearLayout) convertView;
                    }

                    ImageView logoView = filaLayout.findViewById(filaLayout.getChildAt(0).getId());
                    TextView nameView = filaLayout.findViewById(filaLayout.getChildAt(1).getId());

                    CanalEstructura actual = getItem(position);
                    nameView.setText(actual.nombreCanal);

                    if (setDeCanalesFavoritos.contains(actual.urlStream)) {
                        nameView.setText("⭐ " + actual.nombreCanal);
                    }

                    if (actual.urlLogo != null && !actual.urlLogo.isEmpty()) {
                        logoView.setVisibility(View.VISIBLE);
                        logoView.setTag(actual.urlLogo);
                        new Thread(() -> {
                            try {
                                java.io.InputStream is = new java.net.URL((String) logoView.getTag()).openStream();
                                android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeStream(is);
                                runOnUiThread(() -> {
                                    if (logoView.getTag().equals(actual.urlLogo))
                                        logoView.setImageBitmap(bmp);
                                });
                            } catch (Exception ignored) {
                                runOnUiThread(() -> logoView.setImageResource(android.R.drawable.ic_menu_slideshow));
                            }
                        }).start();
                    } else {
                        logoView.setImageResource(android.R.drawable.ic_menu_slideshow);
                    }

                    return filaLayout;
                }
            };

            listViewCanales.setAdapter(adapter);
        }

        private void mostrarCartelConfirmarSalida () {
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

        // 1. Si los menús están abiertos, permitimos que la tecla ATRÁS los cierre
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (menusVisibles) {
                limpiarBuscadorOcultarMenus();
                return true;
            }
        }

        // 2. Si los menús están OCULTOS (viendo tele a pantalla completa)
        if (!menusVisibles) {
            // Manejo del botón OK (Pulsación Larga en vivo)
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (event.getRepeatCount() == 0) {
                        tiempoPresionadoOk = System.currentTimeMillis();
                        yaSeEjecutoLargoOk = false;
                    } else {
                        // Si mantiene presionado por más de 1 segundo (1000 ms)
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

            // Bloqueamos las flechas para que no hagan nada raro a pantalla completa
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                    keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                return true;
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        boolean menusVisibles = (contenedorMenus != null && contenedorMenus.getVisibility() == View.VISIBLE);

        if (!menusVisibles) {
            // Manejo del botón OK al soltarlo (Clic Corto)
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                long duracionClick = System.currentTimeMillis() - tiempoPresionadoOk;
                tiempoPresionadoOk = 0;

                if (!yaSeEjecutoLargoOk && duracionClick < 800) {
                    // ACCIÓN: Pulsación Corta (Abre el menú izquierdo de canales)
                    alternarMenuCanales();
                }
                return true;
            }
        }

        return super.onKeyUp(keyCode, event);
    }
        private void cargarListaDesdeUrl (String urlM3u){
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
                        });
                    }
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Error de red al procesar la lista.", Toast.LENGTH_LONG).show();
                        mostrarPanelAdministradorListas();
                    });
                }
            }).start(); // <- ESTA LLAVE Y PARÉNTESIS CIERRAN EL THREAD
        } // <- ESTA LLAVE CIERRA EL MÉTODO cargarListaDesdeUrl

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
} // <- FIN DEFINITIVO DE TODO EL ARCHIVO