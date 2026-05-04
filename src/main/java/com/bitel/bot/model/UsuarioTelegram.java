package com.bitel.bot.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "usuarios_bot")
public class UsuarioTelegram {

    @Id
    private Long chatId;
    private String nombre;
    private String estado;

    public UsuarioTelegram() {}

    public UsuarioTelegram(Long chatId, String nombre, String estado) {
        this.chatId = chatId;
        this.nombre = nombre;
        this.estado = estado;
    }

    public Long getChatId() { return chatId; }
    public void setChatId(Long chatId) { this.chatId = chatId; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}