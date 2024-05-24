package ru.dimgel.weather;

import org.springframework.http.HttpStatus;


public class WebError extends RuntimeException {
	private final HttpStatus status;

	public WebError(HttpStatus status, String message) {
		super("Error " + status.value() + ": " + message);
		this.status = status;
	}

	public WebError(HttpStatus status, Throwable cause) {
		this(status, cause.getMessage());
	}

	HttpStatus getStatus() {
		return status;
	}
}
