package com.bitel.bot.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class OpManagerService {

    private final WebClient webClient;
    private final String opManagerUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpManagerService() throws SSLException {
        
        this.opManagerUrl = "https://181.176.242.30";
        
        SslContext sslContext = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();

        HttpClient httpClient = HttpClient.create()
                .secure(spec -> spec.sslContext(sslContext));

        String cookieHeader = leerCookiesDeArchivo();
        String csrfToken = leerCsrfDeArchivo();

        System.out.println("🔍 Cookie cargada: " + (cookieHeader.length() > 50 ? cookieHeader.substring(0, 50) + "..." : cookieHeader));
        System.out.println("🔍 CSRF cargado: " + (csrfToken.length() > 30 ? csrfToken.substring(0, 30) + "..." : csrfToken));

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(5 * 1024 * 1024))
                .build();

        this.webClient = WebClient.builder()
                .baseUrl(opManagerUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .defaultHeader(HttpHeaders.COOKIE, cookieHeader)
                .defaultHeader("X-ZCSRF-TOKEN", csrfToken)
                .defaultHeader(HttpHeaders.ACCEPT, "application/json, text/javascript, */*; q=0.01")
                .defaultHeader(HttpHeaders.REFERER, opManagerUrl + "/apiclient/ember/index.jsp")
                .defaultHeader("X-Requested-With", "XMLHttpRequest")
                .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .defaultHeader(HttpHeaders.ACCEPT_ENCODING, "identity")
                .build();
    }

    private String leerCookiesDeArchivo() {
        try {
            Path path = Paths.get("opmanager_cookies.txt");
            if (Files.exists(path)) {
                String content = Files.readString(path).trim();
                System.out.println("✅ Cookies leídas desde archivo");
                return content;
            }
        } catch (IOException e) {
            System.err.println("❌ Error leyendo cookies: " + e.getMessage());
        }
        return "";
    }

    private String leerCsrfDeArchivo() {
        try {
            Path path = Paths.get("opmanager_csrf.txt");
            if (Files.exists(path)) {
                String content = Files.readString(path).trim();
                System.out.println("✅ CSRF leído desde archivo");
                return content;
            }
        } catch (IOException e) {
            System.err.println("❌ Error leyendo CSRF: " + e.getMessage());
        }
        return "";
    }

    public String obtenerDispositivosActivos() {
        String json = consultarOpManager("/client/api/json/v2/device/listDevices");
        if (json == null || json.startsWith("❌") || json.startsWith("⚠️")) return json;
        return parsearDispositivos(json, true);
    }

    public String obtenerTodosLosDispositivos() {
        String json = consultarOpManager("/client/api/json/v2/device/listDevices");
        if (json == null || json.startsWith("❌") || json.startsWith("⚠️")) return json;
        return parsearDispositivos(json, false);
    }

    private String consultarOpManager(String path) {
        try {
            System.out.println("🔍 Consultando: " + opManagerUrl + path);
            
            String jsonResponse = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(path)
                            .queryParam("isMapView", "false")
                            .queryParam("_search", "false")
                            .queryParam("rows", 100)
                            .queryParam("page", 1)
                            .queryParam("sortByColumn", "statusNum")
                            .queryParam("sortByType", "asc")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            System.out.println("✅ Respuesta length: " + (jsonResponse != null ? jsonResponse.length() : 0));
            System.out.println("✅ Preview: " + (jsonResponse != null ? jsonResponse.substring(0, Math.min(100, jsonResponse.length())) : "NULL"));
            
            if (jsonResponse == null || jsonResponse.isEmpty()) {
                return "⚠️ OpManager respondió vacío.\n\nLas cookies expiraron.\n\nPara renovar:\n1. Abre OpManager en navegador y haz login\n2. Abre DevTools (F12) → Network\n3. Recarga la página (F5)\n4. Click en petición 'listDevices' → Headers\n5. Copia Cookie y X-ZCSRF-TOKEN\n6. Pega en archivos opmanager_cookies.txt y opmanager_csrf.txt\n7. Reinicia el bot";
            }

            if (jsonResponse.trim().startsWith("<")) {
                return "⚠️ OpManager devolvió HTML (login page). Las cookies expiraron. Renueva las cookies siguiendo los pasos de arriba.";
            }

            if (jsonResponse.contains("\u001f")) {
                return "⚠️ La respuesta está comprimida (gzip). Intenta sin compresión.";
            }

            return jsonResponse;

        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            return "❌ Error al consultar OpManager: " + e.getMessage();
        }
    }

private String parsearDispositivos(String json, boolean soloActivos) {
    try {
        JsonNode root = objectMapper.readTree(json);
        JsonNode rows = root.path("rows");
        
        if (!rows.isArray() || rows.size() == 0) {
            return "📭 No hay dispositivos registrados en OpManager.";
        }

        StringBuilder resultado = new StringBuilder();
        
        if (soloActivos) {
            resultado.append("🟢 DISPOSITIVOS ACTIVOS (Normal)\n\n");
        } else {
            resultado.append("📊 TODOS LOS DISPOSITIVOS - BITEL\n\n");
        }

        int contadorActivos = 0;
        int contadorCriticos = 0;

        for (JsonNode device : rows) {
            String nombre = device.path("displayName").asText("Sin nombre");
            String ip = device.path("ipaddress").asText("Sin IP");
            String estado = device.path("statusStr").asText("Desconocido");
            String statusNum = device.path("statusNum").asText("0");
            String categoria = device.path("category").asText("Sin categoría");
            String tipo = device.path("type").asText("Sin tipo");
            String vendor = device.path("vendorName").asText("Desconocido");
            String tiempo = device.path("prettyTime").asText("N/A");
            String interfazCount = device.path("interfaceCount").asText("0");

            boolean esActivo = "5".equals(statusNum) || "Normal".equals(estado);

            if (soloActivos && !esActivo) {
                contadorCriticos++;
                continue;
            }

            if (esActivo) contadorActivos++;
            else contadorCriticos++;

            String emoji = esActivo ? "🟢" : "🔴";
            String estadoBanner = esActivo ? "ACTIVO" : "CRITICO";

            // Formato simple SOLO con emojis y texto plano
            resultado.append(emoji).append(" ").append(nombre).append(" - ").append(estadoBanner).append("\n");
            resultado.append("   IP: ").append(ip).append("\n");
            resultado.append("   Estado: ").append(estado).append("\n");
            resultado.append("   Categoria: ").append(categoria).append("\n");
            resultado.append("   Tipo: ").append(tipo).append("\n");
            resultado.append("   Vendor: ").append(vendor).append("\n");
            resultado.append("   Interfaces: ").append(interfazCount).append("\n");
            resultado.append("   Ultima vez: ").append(tiempo).append("\n\n");
        }

        resultado.append("═══════════════════════\n");
        resultado.append("📈 RESUMEN\n\n");
        resultado.append("🟢 Activos (Normal): ").append(contadorActivos).append("\n");
        resultado.append("🔴 Criticos: ").append(contadorCriticos).append("\n");
        resultado.append("📊 Total dispositivos: ").append(rows.size()).append("\n\n");

        if (contadorCriticos > 0) {
            resultado.append("⚠️ ALERTA: Hay ").append(contadorCriticos).append(" dispositivo(s) en estado critico que requieren atencion inmediata.\n");
        } else {
            resultado.append("✅ Todo esta operativo. No hay dispositivos criticos.\n");
        }

        return resultado.toString();

    } catch (Exception e) {
        return "❌ Error al parsear: " + e.getMessage();
    }
}
}