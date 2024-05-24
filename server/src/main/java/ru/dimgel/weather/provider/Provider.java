package ru.dimgel.weather.provider;

import java.util.Vector;
import reactor.core.publisher.Mono;
import ru.dimgel.weather.Period;
import ru.dimgel.weather.Response;


public interface Provider {

	Mono<Response.ByProvider> query(String city, Period period);
}
