package ru.dimgel.weather.provider;

import java.util.Vector;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.dimgel.weather.Period;
import ru.dimgel.weather.Response;


@Service
public class WeatherYandexRu implements Provider {

	@Value("${WeatherYandexRu.apiKey}")
	private String apiKey;
	@Value("${WeatherYandexRu.geoApiKey}")
	private String geoApiKey;


	// Private fields, getters, setters... This is just local DTO.
	public static class MyGeoResponse {
		public static class Response {
			public static class GeoObjectCollectionClass {
				public static class FeatureMember {
					public static class GeoObjectClass {
						public static class PointClass {
							// Format: "longitude latitude"
							// E.g. for Moscow: "37.617698 55.755864"
							// NOTE: In web (maps.yandex.ru, maps.google.com) coords are usually specified in reverse order: latitude,longitude.
							public String pos;
						}
						public PointClass Point;
					}
					public GeoObjectClass GeoObject;
				}
				public Vector<FeatureMember> featureMember;
			}
			public GeoObjectCollectionClass GeoObjectCollection;
		}
		public Response response;
	}


	public static class MyResponse {

		// "geo_object":{
		//     "district":{"id":120540,"name":"Тверской район"},
		//     "locality":{"id":213,"name":"Москва"},             <--- ???
		//     "province":{"id":213,"name":"Москва"},
		//     "country":{"id":225,"name":"Россия"}
		// }
		public static class GeoObject {
			public static class Locality {
				public String name;
			}
			public Locality locality;
		}

		public static class Forecasts {
			public static class Parts {
				public static class DayShort {
					public Double temp;   // Celsius
					public Double wind_speed;   // m/s
					public String condition;
				}
				public DayShort day_short;
			}
			public String date;
			public Parts parts;
		}

		public GeoObject geo_object;
		public Vector<Forecasts> forecasts;
	}

	private static final Pattern regexGeoLatLon = Pattern.compile("^([^ ]+) ([^ ]+)$");

	@Override
	public Mono<Response.ByProvider> query(String city, Period period) {
		return WebClient.create("https://geocode-maps.yandex.ru")
				.get()
				.uri(b -> b
						.path("/1.x/")
						.queryParam("apikey", geoApiKey)
						.queryParam("geocode", city)
						.queryParam("format", "json")
						.build()
				)
				.retrieve()
				.onRawStatus(
						st -> st != 200,
						re -> // TODO Couldn't get response body here.
						      Mono.error(new Exception("Got unexpected HTTP status " + re.statusCode().value() + " from GEO request"))
				)
//				// Debugging: get raw body as string: https://stackoverflow.com/a/66244859/4247442
//				.toEntity(String.class)
//				.map(re -> {
//					System.out.format("raw body: %s\n", re.getBody());
//					var stub = new Response.ByProvider();
//					stub.error = "Debugging...";
//					return stub;
//				});
				.bodyToMono(MyGeoResponse.class)
				.flatMap(r -> {
					String lat = null;
					String lon = null;
					if (r.response != null && r.response.GeoObjectCollection != null &&
							r.response.GeoObjectCollection.featureMember != null && !r.response.GeoObjectCollection.featureMember.isEmpty()
					) {
						var r1 = r.response.GeoObjectCollection.featureMember.elementAt(0);
						if (r1 != null && r1.GeoObject != null && r1.GeoObject.Point != null && r1.GeoObject.Point.pos != null) {
							var m = regexGeoLatLon.matcher(r1.GeoObject.Point.pos);
							if (m.matches()) {
								lon = m.group(1);
								lat = m.group(2);
							}
						}
					}
					if (lat == null || lon == null) {
						throw new RuntimeException("Unknown GEO response format.");
					}
					// Debugging:
					System.out.format("WeatherYandexRu: GEO result: city \"%s\" ---> latitude \"%s\", longitude \"%s\"\n", city, lat, lon);


					// Second request -- inside Mono.flatMap().
					final String lat2 = lat;
					final String lon2 = lon;
					return WebClient.create("https://api.weather.yandex.ru")
							.get()
							.uri(b -> b
									.path("/v2/forecast")
									.queryParam("lat", lat2)
									.queryParam("lon", lon2)
									.queryParam("limit", period == Period.WEEK ? 7 : 1)
									.queryParam("hours", "false")
									.queryParam("extra", "false")
									.queryParam("lang", "ru_RU")
									.build()
							)
							.header("X-Yandex-Weather-Key", apiKey)
							.retrieve()
							.onRawStatus(
									st -> st != 200,
									re -> // TODO Couldn't get response body here.
									      Mono.error(new Exception("Got unexpected HTTP status " + re.statusCode().value()))
							)
//							// Debug: get raw body as string: https://stackoverflow.com/a/66244859/4247442
//							.toEntity(String.class)
//							.map(re -> {
//								System.out.format("raw body: %s\n", re.getBody());
//								var stub = new Response.ByProvider();
//								stub.error = "Debugging...";
//								return stub;
//							});
							.bodyToMono(MyResponse.class)
							.map(r2 -> {
								if (r2.geo_object == null || r2.geo_object.locality == null || r2.geo_object.locality.name == null ||
										r2.forecasts == null || r2.forecasts.isEmpty()) {
									throw new RuntimeException("Unknown response format.");
								}

								var x = new Response.ByProvider();
								x.city = r2.geo_object.locality.name;
								x.data = new Vector<>();
								for (var d : r2.forecasts) {
									if (d == null || d.date == null || d.parts == null || d.parts.day_short == null) {
										throw new RuntimeException("Unknown response format.");
									}
									var d2 = d.parts.day_short;

									var row = new Response.Row();
									row.date = d.date;
									// Don't want to trouble myself with floats & roundings.
									// Dealing with nulls (i.e. optional values, I just don't want Optional here) because it's part of the task.
									if (d2.temp != null) {
										row.temperature = (int) (double) d2.temp;
									}
									if (d2.wind_speed != null) {
										row.wind = (int) (double) d2.wind_speed;
									}
									if (d2.condition != null) {
										row.condition = d2.condition;
									}
									x.data.add(row);
								}

								// Debugging: testing that providers run simultaneously.
//								try {
//									Thread.sleep(3000);
//								} catch (InterruptedException e) {
//									throw new RuntimeException(e);
//								}
//								System.out.println("WeatherYandexRu done");

								return x;
							});
							// I hope .onErrorResume() is NOT needed here, because we're inside flatMap(),
							// so common .onErrorResume() below will do.

				})
				.onErrorResume(e -> {
					// Debugging.
					System.err.println("Error: " + e.getMessage());
					e.printStackTrace(System.err);

					var r = new Response.ByProvider();
					r.error = e.getMessage();
					return Mono.just(r);
				});
	}
}
