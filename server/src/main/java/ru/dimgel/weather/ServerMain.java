package ru.dimgel.weather;

import java.util.HashMap;
import java.util.Vector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;
import ru.dimgel.weather.provider.Provider;


@SpringBootApplication
//@RestController
@Controller
public class ServerMain {

	public static void main(String[] args) {
		SpringApplication.run(ServerMain.class, args);
	}


	private final Vector<Provider> useProviders = new Vector<>();

	@Value("${weather.defaultCity}")
	private String defaultCity;

	public ServerMain(Provider[] providers, @Value("${weather.provider}") String useProvider) {
		boolean useAll = "all".equals(useProvider);
		for (var p : providers) {
			if (useAll || p.getClass().getSimpleName().equals(useProvider)) {
				this.useProviders.add(p);
			}
		}
		if (useProviders.isEmpty()) {
			// I just don't want to write "throws InvalidArgumentException" at method's signature.
			// For discussion: (1) Scala does not require "throws" clause; (2) I never needed more than 4 exception classes.
			throw new RuntimeException("Unknown weather.provider=" + useProvider + " in application.properties");
		}
	}


	// Don't know yet how it's done best way:
	// - While googling, I saw special WebFlux-aware request & response classes, but then lost them.
	// - Then I saw example of web method returning Mono<>, so let's try to do it this way. ALL asynchronously...
	@GetMapping("/")
	public Mono<ResponseEntity<Response>> hello(
			@RequestParam(value = "city", defaultValue = "") String city,
			@RequestParam(value = "period", defaultValue = "day") String period
	) {
		// Debugging: testing that providers run simultaneously.
//		System.out.println("Started");

		// ...even incorporate param checks into async processing. The point is to handle all errors uniformly in the end.
		record Params0(String city, String period) {}
		record Params(String city, Period period) {}

		// I used to split long function chains to explicitly type-check intermediate results.
		Mono<Params> mParams = Mono.just(new Params0(city, period))
				.map(p0 -> {
					var city2 = (p0.city != null && !p0.city.isEmpty()) ? p0.city : defaultCity;
					if (city2 == null || city2.isEmpty()) {
						throw new RuntimeException("weather.defaultCity is not specified in server's application.properties.");
					}

					Period period2;
					try {
						// @RequestParam/defaultValue also applies to empty value, so we're good here.
						period2 = Period.valueOf(p0.period.toUpperCase());
					} catch (Throwable e) {
						throw new WebError(HttpStatus.BAD_REQUEST, e);
					}

					return new Params(city2, period2);
				});

		// Need to pass Params around, because response contains `city`.
		// Didn't flatten T2<T2<P,S>,R> into T3<P,S,R> for niceness -- because who cares.
		Flux<Tuple2<Tuple2<Params, String /*providerName*/>, Response.ByProvider>> fResultsByProvider = Flux.fromIterable(useProviders)

				// How to zip Flux with Mono: https://stackoverflow.com/a/61708600/4247442
				.zipWith(mParams.cache().repeat())

				.flatMap(t2 -> {
					var provider = t2.getT1();
					var params = t2.getT2();
					return Mono
							.just(Tuples.of(params, provider.getClass().getSimpleName()))
							.zipWith(provider.query(params.city(), params.period()));
				});

		return fResultsByProvider
				.collectList()
				.map(l -> {
					var r = new Response();

					// TODO getFirst() doesn't compile: "cannot find symbol: method getFirst()". WTF?!
					// Anyway, list should be non-empty, because useProviders is non-empty. And NPE is just exception, too; it will be caught.
//					var params = l.getFirst().getT1().getT1();
					var params = l.get(0).getT1().getT1();
					r.city = params.city();

					r.byProvider = new HashMap<>();
					for (var li : l) {
						var providerName = li.getT1().getT2();
						var providerResponse = li.getT2();
						r.byProvider.put(providerName, providerResponse);
					}
					return r;
				})
				.map(r -> {
					return ResponseEntity
									.status(HttpStatus.OK)
									.contentType(MediaType.APPLICATION_JSON)
									.body(r);
						}
				)
				.onErrorResume(e -> {
					// Debugging.
					System.err.println("Error: " + e.getMessage());
					e.printStackTrace(System.err);

					var r = new Response();
					r.error = e.getMessage();
					return Mono.just(ResponseEntity
							.status(e instanceof WebError ? HttpStatus.BAD_REQUEST : HttpStatus.INTERNAL_SERVER_ERROR)
							.contentType(MediaType.APPLICATION_JSON)
							.body(r)
					);
				});
	}
}
