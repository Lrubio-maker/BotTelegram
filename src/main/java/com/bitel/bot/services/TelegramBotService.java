package com.bitel.bot.services;

import com.bitel.bot.interfaces.UsuarioTelegramRepository;
import com.bitel.bot.model.UsuarioTelegram;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class TelegramBotService extends TelegramLongPollingBot {

    @Autowired
    private UsuarioTelegramRepository usuarioRepo;

    @Autowired
    private OpManagerService opManagerService;

    private final long ADMIN_CHAT_ID = 1486270596L;
    private final String PALABRA_CLAVE = "bitel2026";

    private Set<Long> usuariosAprobados = new HashSet<>();
    private Set<Long> usuariosPendientes = new HashSet<>();

    @PostConstruct
    public void init() {
        usuariosAprobados.add(ADMIN_CHAT_ID);
        
        List<UsuarioTelegram> aprobados = usuarioRepo.findByEstado("APROBADO");
        for (UsuarioTelegram u : aprobados) {
            usuariosAprobados.add(u.getChatId());
            System.out.println("✅ Usuario aprobado cargado: " + u.getChatId() + " - " + u.getNombre());
        }
        
        List<UsuarioTelegram> pendientes = usuarioRepo.findByEstado("PENDIENTE");
        for (UsuarioTelegram u : pendientes) {
            usuariosPendientes.add(u.getChatId());
            System.out.println("⏳ Usuario pendiente cargado: " + u.getChatId() + " - " + u.getNombre());
        }
        
        System.out.println("📊 Total aprobados: " + usuariosAprobados.size());
        System.out.println("📊 Total pendientes: " + usuariosPendientes.size());
    }

    @Override
    public String getBotToken() {
        return "8781421375:AAEr7yl_ffd-dHMlnQVLz-rBz7v08WhwvaU";
    }

    @Override
    public String getBotUsername() {
        return "Bitel_Corp_bot";
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasCallbackQuery()) {
            manejarClicDeBoton(update);
            return;
        }

        if (update.hasMessage() && update.getMessage().hasText()) {
            long chatId = update.getMessage().getChatId();
            String texto = update.getMessage().getText();
            String nombre = update.getMessage().getFrom().getFirstName();

            if (usuariosAprobados.contains(chatId)) {
                if (texto.equalsIgnoreCase("/PROCURADURIA") || texto.equalsIgnoreCase("/status")) {
                    String dispositivos = opManagerService.obtenerDispositivosActivos();
                    enviarMensaje(chatId, dispositivos);
                    return;
                }
                
                if (texto.equalsIgnoreCase("/todos")) {
                    String todos = opManagerService.obtenerTodosLosDispositivos();
                    enviarMensaje(chatId, todos);
                    return;
                }

                if (texto.equalsIgnoreCase("/testadmin")) {
                    enviarMensaje(ADMIN_CHAT_ID, "🔧 Test: Si ves esto, el admin puede recibir mensajes. Chat ID del solicitante: " + chatId);
                    enviarMensaje(chatId, "✅ Mensaje de prueba enviado al admin");
                    return;
                }

                enviarMensaje(chatId, "✅ Comando autorizado: " + texto);
                return;
            }

            if (usuariosPendientes.contains(chatId)) {
                enviarMensaje(chatId, "⏳ Tu solicitud aún está pendiente de revisión. Por favor espera a que el administrador te acepte.");
                return;
            }

            if (texto.equals(PALABRA_CLAVE)) {
                if (usuarioRepo.existsById(chatId)) {
                    UsuarioTelegram existente = usuarioRepo.findById(chatId).get();
                    if ("APROBADO".equals(existente.getEstado())) {
                        usuariosAprobados.add(chatId);
                        enviarMensaje(chatId, "✅ Ya estás aprobado. Bienvenido de nuevo!");
                        return;
                    }
                    if ("PENDIENTE".equals(existente.getEstado())) {
                        usuariosPendientes.add(chatId);
                        enviarMensaje(chatId, "⏳ Tu solicitud sigue pendiente. Espera la confirmación.");
                        return;
                    }
                }

                UsuarioTelegram nuevoUsuario = new UsuarioTelegram(chatId, nombre, "PENDIENTE");
                usuarioRepo.save(nuevoUsuario);
                usuariosPendientes.add(chatId);
                
                enviarMensaje(chatId, "✅ Contraseña correcta. Solicitud enviada al administrador. Espera la confirmación.");
                
                boolean notificado = notificarAdministrador(chatId, nombre, update.getMessage().getFrom().getUserName());
                
                if (!notificado) {
                    System.err.println("❌ ERROR: No se pudo notificar al administrador");
                }
                return;
            }

            enviarMensaje(chatId, "⛔ Acceso denegado. Este es un bot privado. Ingresa la palabra clave para solicitar acceso.");
        }
    }

    private boolean notificarAdministrador(long solicitanteId, String nombre, String username) {
        try {
            SendMessage mensaje = new SendMessage();
            mensaje.setChatId(String.valueOf(ADMIN_CHAT_ID));
            
            String infoUsuario = "🔔 NUEVA SOLICITUD DE ACCESO\n" +
                    "Nombre: " + nombre + "\n" +
                    "Alias: @" + (username != null ? username : "Sin alias") + "\n" +
                    "Chat ID: " + solicitanteId;
            mensaje.setText(infoUsuario);

            InlineKeyboardMarkup tecladoInline = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> filasBotones = new ArrayList<>();
            List<InlineKeyboardButton> fila1 = new ArrayList<>();

            InlineKeyboardButton btnAceptar = new InlineKeyboardButton();
            btnAceptar.setText("✅ Aceptar");
            btnAceptar.setCallbackData("ACEPTAR_" + solicitanteId);

            InlineKeyboardButton btnRechazar = new InlineKeyboardButton();
            btnRechazar.setText("❌ Rechazar");
            btnRechazar.setCallbackData("RECHAZAR_" + solicitanteId);

            fila1.add(btnAceptar);
            fila1.add(btnRechazar);
            filasBotones.add(fila1);
            tecladoInline.setKeyboard(filasBotones);
            mensaje.setReplyMarkup(tecladoInline);

            execute(mensaje);
            System.out.println("✅ Notificación enviada al admin para usuario: " + solicitanteId);
            return true;
            
        } catch (TelegramApiException e) {
            System.err.println("❌ ERROR al notificar admin: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void manejarClicDeBoton(Update update) {
        String datosDelBoton = update.getCallbackQuery().getData();
        String[] partes = datosDelBoton.split("_");
        String accion = partes[0];
        long usuarioId = Long.parseLong(partes[1]);

        if (accion.equals("ACEPTAR")) {
            usuariosPendientes.remove(usuarioId);
            usuariosAprobados.add(usuarioId);
            
            UsuarioTelegram usuario = usuarioRepo.findById(usuarioId).orElse(null);
            if (usuario != null) {
                usuario.setEstado("APROBADO");
                usuarioRepo.save(usuario);
                System.out.println("✅ Usuario " + usuarioId + " aprobado y guardado en MySQL");
            }
            
            enviarMensaje(ADMIN_CHAT_ID, "✅ Has APROBADO al usuario " + usuarioId);
            enviarMensaje(usuarioId, "🎉 ¡Felicidades! El administrador ha aprobado tu acceso. Ya puedes usar el bot corporativo.");
            
        } else if (accion.equals("RECHAZAR")) {
            usuariosPendientes.remove(usuarioId);
            
            UsuarioTelegram usuario = usuarioRepo.findById(usuarioId).orElse(null);
            if (usuario != null) {
                usuario.setEstado("RECHAZADO");
                usuarioRepo.save(usuario);
                System.out.println("❌ Usuario " + usuarioId + " rechazado y guardado en MySQL");
            }
            
            enviarMensaje(ADMIN_CHAT_ID, "❌ Has RECHAZADO al usuario " + usuarioId);
            enviarMensaje(usuarioId, "⛔ Tu solicitud de acceso ha sido denegada.");
        }
    }
private void enviarMensaje(long chatId, String texto) {
    int MAX_LENGTH = 3500;
    
    if (texto.length() <= MAX_LENGTH) {
        enviarMensajePlano(chatId, texto);
        return;
    }
    
    // Dividir por líneas para no cortar en medio de nada
    String[] lineas = texto.split("\n");
    StringBuilder parteActual = new StringBuilder();
    int numParte = 1;
    
    for (String linea : lineas) {
        if (parteActual.length() + linea.length() + 1 > MAX_LENGTH) {
            String header = "📄 Parte " + numParte + "\n\n";
            enviarMensajePlano(chatId, header + parteActual.toString());
            
            numParte++;
            parteActual = new StringBuilder();
            
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        parteActual.append(linea).append("\n");
    }
    
    if (parteActual.length() > 0) {
        String header = "📄 Parte " + numParte + "\n\n";
        enviarMensajePlano(chatId, header + parteActual.toString());
    }
}

private void enviarMensajePlano(long chatId, String texto) {
    SendMessage message = new SendMessage();
    message.setChatId(String.valueOf(chatId));
    message.setText(texto);
    // NO usar setParseMode - enviar como texto plano
    try {
        execute(message);
    } catch (TelegramApiException e) {
        System.err.println("❌ ERROR al enviar mensaje a " + chatId + ": " + e.getMessage());
    }
}
    private void enviarMensajeSimple(long chatId, String texto) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(texto);
        message.setParseMode("Markdown");
        try {
            execute(message);
        } catch (TelegramApiException e) {
            System.err.println("❌ ERROR al enviar mensaje a " + chatId + ": " + e.getMessage());
            try {
                message.setParseMode(null);
                message.setText(texto.replace("*", "").replace("`", ""));
                execute(message);
            } catch (TelegramApiException e2) {
                System.err.println("❌ ERROR definitivo: " + e2.getMessage());
            }
        }
    }
}