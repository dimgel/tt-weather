package ru.dimgel.weather;

import org.springframework.http.HttpStatus;


public class WebError extends RuntimeException {

	public WebError(HttpStatus status, String message) {
		super("Error " + status.value() + ": " + message);
	}

	public WebError(HttpStatus status, Throwable cause) {
		this(status, cause.getMessage());
	}
}
