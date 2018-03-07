package com.astuntechnology.utilities;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;

import com.astuntechnology.utilities.ExtractJmeterParams.TYPE;

public class ExtractJmeterParamsTest {
	private static File good;
	private static File ms;

	@BeforeClass
	static public void setup() {
		good = urlToFile(ExtractJmeterParamsTest.class.getResource("test.txt"));
		ms = urlToFile(ExtractJmeterParamsTest.class.getResource("iss-log.log"));
	}

	@Test
	public void testSetColumns() {
		ExtractJmeterParams extractor = new ExtractJmeterParams();
		String[] expects = new String[] { "ian", "fred", "bert" };
		extractor.setColumns(expects);
		String[] observed = extractor.getColumns();
		String[] expected = new String[] { "IAN", "FRED", "BERT" };
		assertArrayEquals(expected, observed);
	}

	@Test
	public void testGetResults() throws FileNotFoundException, IOException, ParseException {
		ExtractJmeterParams extractor = new ExtractJmeterParams();
		extractor.parseFile(new String[] {good.getAbsolutePath()});
		Map<Calendar, HashMap<String, String>> res = extractor.getResults();
		assertNotNull(res);
	}

	@Test
	public void testParseFile() throws FileNotFoundException, IOException, ParseException {
		ExtractJmeterParams extractor = new ExtractJmeterParams();
		extractor.parseFile(new String[] {good.getAbsolutePath()});

	}

	@Test
	public void testParseMSUrls() throws FileNotFoundException, IOException, ParseException {
		ExtractJmeterParams extractor = new ExtractJmeterParams();
		File badMS = urlToFile(ExtractJmeterParamsTest.class.getResource("ms-bad.txt"));
		extractor.setType(TYPE.MICROSOFT);
		extractor.parseFile(new String[] {badMS.getAbsolutePath()});

	}
	
	@Test
	public void testSetSeperator() throws FileNotFoundException, IOException, ParseException {
		ExtractJmeterParams extractor = new ExtractJmeterParams();

		extractor.setSeperator(";");
		extractor.setColumns(new String[] { "LAYERS", "HEIGHT", "WIDTH", "BBOX" });
		String obs = extractor.getSeperator();
		assertEquals(";", obs);
		extractor.parseFile(new String[] {good.getAbsolutePath()});
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		PrintStream out = new PrintStream(bytes);
		extractor.print(out);
		byte[] b = bytes.toByteArray();
		ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(b);
		BufferedReader r = new BufferedReader(new InputStreamReader(byteArrayInputStream), 32);
		String header = r.readLine();
		assertEquals("URL;LAYERS;HEIGHT;WIDTH;BBOX;", header);
		extractor.setColumns(new String[] { "LAYERS", "HEIGHT", "WIDTH", "BBOX", "URL" });
		extractor.parseFile(new String[] {good.getAbsolutePath()});
		bytes = new ByteArrayOutputStream();
		out = new PrintStream(bytes);
		extractor.print(out);
		b = bytes.toByteArray();
		byteArrayInputStream = new ByteArrayInputStream(b);
		r = new BufferedReader(new InputStreamReader(byteArrayInputStream), 32);
		header = r.readLine();
		assertEquals("LAYERS;HEIGHT;WIDTH;BBOX;URL;", header);

	}
	private static SimpleDateFormat sdf = new SimpleDateFormat("dd/MMM/YYYY:HH:mm:ss.SSSZ");
	@Test
	public void testMicrosoft() throws FileNotFoundException, IOException, ParseException {
		ExtractJmeterParams extractor = new ExtractJmeterParams();
		extractor.setType(TYPE.MICROSOFT);
		extractor.setSeperator(";");
		extractor.setColumns(new String[] { "serviceAction", "transparent", "requesttype", "Basemap" });
		String obs = extractor.getSeperator();
		assertEquals(";", obs);
		extractor.setMatch("print");
		extractor.parseFile(new String[] {ms.getAbsolutePath()});
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		PrintStream out = new PrintStream(bytes);
		extractor.print(out);

		byte[] b = bytes.toByteArray();
		ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(b);
		BufferedReader r = new BufferedReader(new InputStreamReader(byteArrayInputStream), 32);
		String header = r.readLine();
		assertEquals("URL;SERVICEACTION;TRANSPARENT;REQUESTTYPE;BASEMAP;", header);
		
		
		// serviceAction=ZoomToLocation&transparent=true&requesttype=Map&mapID=-1&Basemap=Essex%2Fbase_ADS_BandW&mapSource=Essex%2FECCGIS_Highways&layers=ecc_additions&northing=205195.6463425&easting=571411.246595&mapWidth=1670&mapHeight=932&mapResolution=0.25&mapScale=708.6618&units=m&zoom=417&maxExtent=1-1-700000-1300000&restrictedExtent=530000-170000-630000-250000&projection=EPSG%3A27700&dataLayerName=iSharemaps+Data+Layer
		extractor.setColumns(new String[] { "requesttype", "Basemap", "mapSource", "layers", "northing", "easting",
				"mapWidth", "mapHeight", "mapResolution", "mapScale", "zoom", "maxExtent", "restrictedExtent" });
		
		extractor.setMatch("print");
		extractor.parseFile(new String[] {ms.getAbsolutePath()});
		/*Map<Calendar, HashMap<String, String>> res = extractor.getResults();
		for(Calendar k:res.keySet()) {
			System.out.println(sdf.format(k.getTime())+" : "+res.get(k));
		}*/
		bytes = new ByteArrayOutputStream();
		out = new PrintStream(bytes);
		extractor.print(out);
		//extractor.print();
		b = bytes.toByteArray();
		byteArrayInputStream = new ByteArrayInputStream(b);
		r = new BufferedReader(new InputStreamReader(byteArrayInputStream), 32);
		header = r.readLine();
		assertEquals(
				"URL;REQUESTTYPE;BASEMAP;MAPSOURCE;LAYERS;NORTHING;EASTING;MAPWIDTH;MAPHEIGHT;MAPRESOLUTION;MAPSCALE;ZOOM;MAXEXTENT;RESTRICTEDEXTENT;",
				header);
		String line = "";
		int count = 0;
		while((line=r.readLine())!=null) {
			count++;
			//System.out.println(line);
		}
		assertEquals(5, count);
		
		extractor.setExclude("jpg");
		extractor.parseFile(new String[] {ms.getAbsolutePath()});
		/*Map<Calendar, HashMap<String, String>> res = extractor.getResults();
		for(Calendar k:res.keySet()) {
			System.out.println(sdf.format(k.getTime())+" : "+res.get(k));
		}*/
		bytes = new ByteArrayOutputStream();
		out = new PrintStream(bytes);
		extractor.print(out);
		//extractor.print();
		b = bytes.toByteArray();
		byteArrayInputStream = new ByteArrayInputStream(b);
		r = new BufferedReader(new InputStreamReader(byteArrayInputStream), 32);
		header = r.readLine();
		assertEquals(
				"URL;REQUESTTYPE;BASEMAP;MAPSOURCE;LAYERS;NORTHING;EASTING;MAPWIDTH;MAPHEIGHT;MAPRESOLUTION;MAPSCALE;ZOOM;MAXEXTENT;RESTRICTEDEXTENT;",
				header);
		line = "";
		count = 0;
		while((line=r.readLine())!=null) {
			count++;
			System.out.println(line);
		}
		assertEquals(4, count);;

	}

	@Test
	public void testTimings() throws FileNotFoundException, IOException, ParseException {
		ExtractJmeterParams extractor = new ExtractJmeterParams();
		extractor.setType(TYPE.MICROSOFT);
		extractor.setSeperator(";");
		extractor.setColumns(new String[] { "serviceAction", "transparent", "requesttype", "Basemap" });
		String obs = extractor.getSeperator();
		assertEquals(";", obs);
		extractor.setMatch("print");
		extractor.setExclude("css|jpg|png|gif");
		extractor.parseFile(new String[] {ms.getAbsolutePath()});
		extractor.getTimings();
	}
	/**
	 * Takes a URL and converts it to a File. The attempts to deal with Windows
	 * UNC format specific problems, specifically files located on network
	 * shares and different drives.
	 * <p>
	 * If the URL.getAuthority() returns null or is empty, then only the url's
	 * path property is used to construct the file. Otherwise, the authority is
	 * prefixed before the path.
	 * <p>
	 * It is assumed that url.getProtocol returns "file".
	 * <p>
	 * Authority is the drive or network share the file is located on. Such as
	 * "C:", "E:", "\\fooServer"
	 * 
	 * @param url
	 *            a URL object that uses protocol "file"
	 * @return a File that corresponds to the URL's location
	 */
	public static File urlToFile(URL url) {
		if (!"file".equals(url.getProtocol())) {
			return null; // not a File URL
		}
		String string = url.toString();
		if (url.getQuery() != null) {
			string = string.substring(0, string.indexOf("?"));
		}
		if (string.contains("+")) {
			// this represents an invalid URL created using either
			// file.toURL(); or
			// file.toURI().toURL() on a specific version of Java 5 on Mac
			string = string.replace("+", "%2B");
		}
		try {
			string = URLDecoder.decode(string, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException("Could not decode the URL to UTF-8 format", e);
		}
		String path3;
		String simplePrefix = "file:/";
		String standardPrefix = "file://";
		if (IS_WINDOWS_OS && string.startsWith(standardPrefix)) {
			// win32: host/share reference. Keep the host slashes.
			path3 = string.substring(standardPrefix.length() - 2);
			File f = new File(path3);
			if (!f.exists()) {
				// Make path relative to be backwards compatible.
				path3 = path3.substring(2, path3.length());
			}
		} else if (string.startsWith(standardPrefix)) {
			path3 = string.substring(standardPrefix.length());
		} else if (string.startsWith(simplePrefix)) {
			path3 = string.substring(simplePrefix.length() - 1);
		} else {
			String auth = url.getAuthority();
			String path2 = url.getPath().replace("%20", " ");
			if (auth != null && !auth.equals("")) {
				path3 = "//" + auth + path2;
			} else {
				path3 = path2;
			}
		}
		return new File(path3);
	}

	/**
	 * Are we running on Windows?
	 */
	static final boolean IS_WINDOWS_OS = System.getProperty("os.name").toUpperCase().contains("WINDOWS");

}
