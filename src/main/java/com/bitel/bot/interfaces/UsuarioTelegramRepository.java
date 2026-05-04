package com.bitel.bot.interfaces;

import com.bitel.bot.model.UsuarioTelegram;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UsuarioTelegramRepository extends JpaRepository<UsuarioTelegram, Long> {
    List<UsuarioTelegram> findByEstado(String estado);
}