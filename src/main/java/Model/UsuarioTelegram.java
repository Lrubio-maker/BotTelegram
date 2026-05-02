package Model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "usuarios_bot") // Así se llamará la tabla en MySQL
public class UsuarioTelegram {

    @Id // Esta será la llave primaria (El Chat ID es único)
    private Long chatId;
    
    private String nombre;
    private String estado; // Guardaremos "APROBADO" o "PENDIENTE"

    // Constructores
    public UsuarioTelegram() {}

    public UsuarioTelegram(Long chatId, String nombre, String estado) {
        this.chatId = chatId;
        this.nombre = nombre;
        this.estado = estado;
    }

    // Getters y Setters (Obligatorios para Hibernate)
    public Long getChatId() { return chatId; }
    public void setChatId(Long chatId) { this.chatId = chatId; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}