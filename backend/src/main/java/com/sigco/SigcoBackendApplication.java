package com.sigco;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SigcoBackendApplication {

	/**
	 * Punto de arranque del sistema: al ejecutar el backend, esta es la primera
	 * instrucción que corre. Spring se encarga de levantar el resto (la conexión a
	 * la base, la API y la seguridad).
	 */
	public static void main(String[] args) {
		SpringApplication.run(SigcoBackendApplication.class, args);
	}

}
