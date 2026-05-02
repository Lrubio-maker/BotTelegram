package Interfaces;



import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import Model.UsuarioTelegram;

@Repository
public interface UsuarioTelegramRepository extends JpaRepository<UsuarioTelegram, Long> {
    // Spring crea el código automáticamente por detrás. ¡No tienes que escribir nada más aquí!
}