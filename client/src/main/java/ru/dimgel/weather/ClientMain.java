package ru.dimgel.weather;


import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;


public class ClientMain {

	public static void main(String[] args) {
		// Parse command line args.
		// I could make these Optional-s, it would make WebClient uri builder a little nicer, but that would be overhead.
		String city = null;
		// Don't need enum here, need just String for WebClient URI builder.
		String period = null;

		for (var a: args) {
			// Would be simpler for both coder and user to pass just number of days...
			if ("day".equals(a) || "week".equals(a)) {
				if (period != null) {
					throw new RuntimeException("Period (day|week) is specified multiple times.");
				}
				period = a;
			} else {
				if (city != null) {
					throw new RuntimeException("City is specified multiple times.");
				}
				city = a;
			}
		}

		// Build & send request.
		// Vars used in lambdas must be final:
		final var city2 = city;
		final var period2 = period;

		WebClient.create("http://localhost:8080")
				.get()
				.uri(b -> {
					b.path("/");
					if (city2 != null) {
						b.queryParam("city", city2);
					}
					if (period2 != null) {
						b.queryParam("period", period2);
					}
					return b.build();
				})
				.retrieve()
				.onRawStatus(s -> s == 404, re -> {
					System.err.println("Error 404: Requested city not found");
					return Mono.empty();
				})
				.onRawStatus(s -> s >= 400, re -> {
					var i = re.statusCode().value();
					// TODO !!! block() inside worker thread won't work.
					System.err.format("Error %d: %s\n%s\n", i, HttpStatus.valueOf(i).getReasonPhrase(), re.bodyToMono(String.class).block());
					return Mono.empty();
				})
				.onRawStatus(s -> s != 200, re -> {
					System.err.format("Error: Got unexpected HTTP status %d\n", re.statusCode().value());
					return Mono.empty();
				})
				.bodyToMono(Response.class)
				.onErrorResume(e -> {
					System.err.format("Error: %s\n", e.getMessage());
					e.printStackTrace(System.err);
					return Mono.empty();
				})
				.subscribe(r -> {
					System.out.format("City: %s\n", r.city);
					for (var entry : r.byProvider.entrySet()) {
						System.out.format("Provider: %s\n", entry.getKey());
						Response.ByProvider p = entry.getValue();
						if (p.error != null && !p.error.isEmpty()) {
							System.out.format("    Error: %s\n", p.error);
						} else {
							System.out.format("    City: %s\n    Data:\n", p.city);
							for (var row : p.data) {
								System.out.format("        %s   %3s °C   %2s m/s   %s\n",
										row.date,
										row.temperature != null ? String.valueOf(row.temperature) : "---",
										row.wind != null ? String.valueOf(row.wind) : "--",
										row.condition != null ? row.condition : "-----"
								);
							}
						}
					}
				});
				// TODO !!! It stalls for few seconds here, probably waiting for WebFlux's background threads to stop.
				//          How to I force it to stop RIGHT after .subscribe() has completed?
	}
}
