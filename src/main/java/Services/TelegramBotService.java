package Services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import Interfaces.UsuarioTelegramRepository;
import Model.UsuarioTelegram;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;



@Service
public class TelegramBotService extends TelegramLongPollingBot {
 
	// Inyectamos la conexión a MySQL que creaste en el paso anterior
    @Autowired
    private UsuarioTelegramRepository usuarioRepo;
	
	// ==========================================
    // 1. CONFIGURACIÓN DE SEGURIDAD (¡MODIFICA ESTO!)
    // ==========================================
    private final long ADMIN_CHAT_ID = 1486270596L; // Pon TU verdadero Chat ID aquí (con la L al final)
    private final String PALABRA_CLAVE = "bitel2026"; // La contraseña secreta que deben escribir
    // 2. Inyectamos los valores directamente en los métodos
    
    
 // 2. Bases de datos temporales (En memoria)
    // Agregamos tu ID automáticamente a los aprobados para que tú siempre tengas acceso
    private Set<Long> usuariosAprobados = new HashSet<>(Set.of(ADMIN_CHAT_ID)); 
    private Set<Long> usuariosPendientes = new HashSet<>();
    
    
    
    @Override
    public String getBotToken() {
        // Pega tu token exacto aquí dentro de las comillas
        return "8781421375:AAEr7yl_ffd-dHMlnQVLz-rBz7v08WhwvaU"; // Reemplaza con tu token completo
    }

    
    @Override
    public String getBotUsername() {
        // El nombre de tu bot que vi en tu captura
        return "Bitel_Corp_bot";
    }
    
    
    @Override
    public void onUpdateReceived(Update update) {
        // ==========================================
        // CASO A: El Administrador hizo clic en un botón
        // ==========================================
        if (update.hasCallbackQuery()) {
            manejarClicDeBoton(update);
            return; // Cortamos la ejecución aquí
        }
     // ==========================================
        // CASO B: Alguien envió un mensaje de texto
        // ==========================================
        if (update.hasMessage() && update.getMessage().hasText()) {
            long chatId = update.getMessage().getChatId();
            String texto = update.getMessage().getText();
            String nombre = update.getMessage().getFrom().getFirstName();

            // 1. ¿El usuario ya está aprobado?
            if (usuariosAprobados.contains(chatId)) {
                enviarMensaje(chatId, "✅ Has enviado un comando autorizado: " + texto);
                return;
            }

            // 2. ¿El usuario está en lista de espera?
            if (usuariosPendientes.contains(chatId)) {
                enviarMensaje(chatId, "⏳ Tu solicitud aún está pendiente de revisión. Por favor espera a que el administrador te acepte.");
                return;
            }

            // 3. Es un desconocido y descubrió la palabra clave
            if (texto.equals(PALABRA_CLAVE)) {
            	// Lo guardamos en MySQL con estado PENDIENTE
                UsuarioTelegram nuevoUsuario = new UsuarioTelegram(chatId, nombre, "PENDIENTE");
                usuarioRepo.save(nuevoUsuario);
                
                //usuariosPendientes.add(chatId); // Lo metemos a la lista de espera
                enviarMensaje(chatId, "✅ Contraseña correcta. Solicitud enviada al administrador. Espera la confirmación.");
                notificarAdministrador(chatId, nombre, update.getMessage().getFrom().getUserName());
                return;
            }

            // 4. Es un desconocido y escribió cualquier otra cosa
            enviarMensaje(chatId, "⛔ Acceso denegado. Este es un bot privado. Ingresa la palabra clave para solicitar acceso.");
        }
    }
 // ==========================================
    // MÉTODO: Enviar alerta con botones al Administrador
    // ==========================================
    private void notificarAdministrador(long solicitanteId, String nombre, String username) {
        SendMessage mensaje = new SendMessage();
        mensaje.setChatId(String.valueOf(ADMIN_CHAT_ID)); // Te lo enviamos a TI
        
        String infoUsuario = "🔔 NUEVA SOLICITUD DE ACCESO\n" +
                             "Nombre: " + nombre + "\n" +
                             "Alias: @" + (username != null ? username : "Sin alias") + "\n" +
                             "Chat ID: " + solicitanteId;
        mensaje.setText(infoUsuario);

        // Crear el teclado con botones
        InlineKeyboardMarkup tecladoInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> filasBotones = new ArrayList<>();
        List<InlineKeyboardButton> fila1 = new ArrayList<>();

        // Botón Aceptar (En los datos ocultos metemos el ID del solicitante)
        InlineKeyboardButton btnAceptar = new InlineKeyboardButton();
        btnAceptar.setText("✅ Aceptar");
        btnAceptar.setCallbackData("ACEPTAR_" + solicitanteId); 

        // Botón Rechazar
        InlineKeyboardButton btnRechazar = new InlineKeyboardButton();
        btnRechazar.setText("❌ Rechazar");
        btnRechazar.setCallbackData("RECHAZAR_" + solicitanteId);

        fila1.add(btnAceptar);
        fila1.add(btnRechazar);
        filasBotones.add(fila1);
        tecladoInline.setKeyboard(filasBotones);
        mensaje.setReplyMarkup(tecladoInline);

        try { execute(mensaje); } catch (TelegramApiException e) { e.printStackTrace(); }
    }

    // ==========================================
    // MÉTODO: Procesar el clic del Administrador
    // ==========================================
    private void manejarClicDeBoton(Update update) {
        String datosDelBoton = update.getCallbackQuery().getData(); // Ejemplo: "ACEPTAR_987654321"
        
        // Separamos la acción (ACEPTAR) del ID (987654321)
        String[] partes = datosDelBoton.split("_");
        String accion = partes[0];
        long usuarioId = Long.parseLong(partes[1]);

        if (accion.equals("ACEPTAR")) {
            usuariosPendientes.remove(usuarioId);
            usuariosAprobados.add(usuarioId); // Le damos paso libre
            
            enviarMensaje(ADMIN_CHAT_ID, "✅ Has APROBADO al usuario " + usuarioId);
            enviarMensaje(usuarioId, "🎉 ¡Felicidades! El administrador ha aprobado tu acceso. Ya puedes usar el bot corporativo.");
            
        } else if (accion.equals("RECHAZAR")) {
            usuariosPendientes.remove(usuarioId); // Lo sacamos de espera, pero no lo aprobamos
            
            enviarMensaje(ADMIN_CHAT_ID, "❌ Has RECHAZADO al usuario " + usuarioId);
            enviarMensaje(usuarioId, "⛔ Tu solicitud de acceso ha sido denegada.");
        }
    }

    // ==========================================
    // MÉTODO BÁSICO: Enviar texto simple
    // ==========================================
    private void enviarMensaje(long chatId, String texto) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(texto);
        try { execute(message); } catch (TelegramApiException e) { e.printStackTrace(); }
    }
}