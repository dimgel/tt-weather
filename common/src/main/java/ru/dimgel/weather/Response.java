package ru.dimgel.weather;

import java.util.HashMap;
import java.util.Vector;


public class Response {

	public static class Row {
		/** "YYYY-MM-DD" */
		public String date;

		/** Optional, day's average, rounded, Celsius. */
		public Integer temperature;

		/** Optional, day's average, rounded, meters per second. */
		public Integer wind;

		/** Optional. */
		public String condition;
	}


	public static class ByProvider {
		public String error;
		public String city;
		public Vector<Row> data;
	}


	// We'll return internal errors as JSON, too.
	public String error;

	public String city;

	// I could place provider name inside Row and sort rows by date -- so same dates from different providers go together.
	// But duplicating provider name in each row consumes traffic. Also, if client prefers one provider over another, they prefer it for all rows.
	public HashMap<String, ByProvider> byProvider;
}
