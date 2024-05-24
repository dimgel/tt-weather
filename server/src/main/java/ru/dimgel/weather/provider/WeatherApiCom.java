package ru.dimgel.weather.provider;

import java.util.Vector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.dimgel.weather.Period;
import ru.dimgel.weather.Response;


// I probably should have called this WeatherApiComProvider... but that's long and ugly, and we're already inside `package provider`.
@Service
public class WeatherApiCom implements Provider {

	// This is not service dependency, so I'll afford myself to omit constructor.
	@Value("${WeatherApiCom.apiKey}")
	private String apiKey;


	// Private fields, getters, setters... This is just local DTO.
	public static class MyResponse {

		// {"location":{
		//     "name":"Москва",           <--- this is my request (in Russian)
		//     "region":"Moscow City",    <--- ???
		//     "country":"Россия",
		//     ... (tz, lat, long, etc.)
		// }
		public static class Location {
			public String region;
		}

		public static class Forecast {
			public static class ForecastDay {
				public static class Day {
					public static class Condition {
						public String text;
					}
					public Double avgtemp_c;
					public Double maxwind_kph;
					public Condition condition;
				}
				public String date;
				public Day day;
			}
			public Vector<ForecastDay> forecastday;
		}
		public Location location;
		public Forecast forecast;
	}

	@Override
	public Mono<Response.ByProvider> query(String city, Period period) {
		return WebClient.create("https://api.weatherapi.com")
				.get()
				.uri(b -> b
						.path("v1/forecast.json")
						.queryParam("key", apiKey)
						.queryParam("q", city)
						.queryParam("days", period == Period.WEEK ? 7 : 1)
						.queryParam("aqi", "no")
						.queryParam("alerts", "no")
						.build()
				)
				.retrieve()
				.onRawStatus(
						st -> st != 200,
						re -> // TODO Couldn't get response body here.
						      Mono.error(new Exception("Got unexpected HTTP status " + re.statusCode().value()))
				)
				// Debug: get raw body as string: https://stackoverflow.com/a/66244859/4247442
//				.toEntity(String.class)
//				.map(re -> {
//					System.out.format("raw body: %s\n", re.getBody());
//					var stub = new Response.ByProvider();
//					stub.error = "Debugging...";
//					return stub;
//				});
				.bodyToMono(MyResponse.class)
				.map(r -> {
					if (r.location == null || r.location.region == null || r.forecast == null || r.forecast.forecastday == null) {
						throw new RuntimeException("Unknown response format.");
					}
					var x = new Response.ByProvider();
					x.city = r.location.region;
					x.data = new Vector<>();
					for (var d : r.forecast.forecastday) {
						if (d == null || d.date == null || d.day == null) {
							throw new RuntimeException("Unknown response format.");
						}
						var row = new Response.Row();
						row.date = d.date;
						// Don't want to trouble myself with floats & roundings.
						// Dealing with nulls (i.e. optional values, I just don't want Optional here) because it's part of the task.
						if (d.day.avgtemp_c != null) {
							row.temperature = (int) (double) d.day.avgtemp_c;
						}
						if (d.day.maxwind_kph != null) {
							row.wind = (int) (d.day.maxwind_kph / 3.6);
						}
						if (d.day.condition != null && d.day.condition.text != null) {
							row.condition = d.day.condition.text;
						}
						x.data.add(row);
					}
					return x;
				})
				.onErrorResume(e -> {
					// Debugging.
					System.err.println("Error: " + e.getMessage());
					e.printStackTrace(System.err);

					var x = new Response.ByProvider();
					x.error = e.getMessage();
					return Mono.just(x);
				});
	}
}
