package jopera.util;

import java.io.StringReader;
import java.util.Base64;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

public class JsonUtils {
	private static Gson gson = new Gson();

	public static <T> String ObjectToString(T t) {
		return gson.toJson(t);
	}

	public static byte[] toBytes(String s) {
		byte[] encode = Base64.getEncoder().encode(s.getBytes());
		return encode;
	}

	public static <T> T StringToObject(String jsonString, Class<T> tt) {
	   	JsonReader reader = new JsonReader(new StringReader(jsonString));
	   	reader.setLenient(true);
		return gson.fromJson(reader, tt);
	}
}