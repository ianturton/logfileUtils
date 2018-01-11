package com.astuntechnology.utilities;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URLDecoder;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.MissingOptionException;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;

public class ExtractJmeterParams {
	enum TYPE {
		APACHE, MICROSOFT;
	}

	/**
	 * Process an access log file to create input for a JMeter session
	 * 
	 * A bit like LogParser but portable
	 * 
	 * @param args
	 */

	private ArrayList<String> columns = new ArrayList<>();
	private HashMap<Calendar, HashMap<String, String>> results = new HashMap<>();
	private static SimpleDateFormat asdf = new SimpleDateFormat("dd/MMM/YYYY:HH:mm:ssZ");
	private static SimpleDateFormat msdf = new SimpleDateFormat("dd-MM-YYYY HH:mm:ss");
	private String seperator = "\t";
	private TYPE type = TYPE.APACHE;
	private String match = "";

	public static void main(String[] args) throws IOException, ParseException, org.apache.commons.cli.ParseException {
		Options options = new Options();
		options.addOption(Option.builder("f").longOpt("file").hasArgs().desc("log files to read in").required().build());
		options.addOption(Option.builder("o").longOpt("output").hasArg().desc("Output file (default StdOut)").build());
		options.addOption(Option.builder("c").longOpt("columns").hasArgs()
				.desc("space seperated list of parameters to extract from the url").required().build());
		options.addOption(Option.builder("s").longOpt("separator").hasArg()
				.desc("character to separate output columns (default tab)").build());
		options.addOption(Option.builder("m").longOpt("microsoft").desc("Is this a microsoft log file").build());
		options.addOption(Option.builder().longOpt("term").hasArg().desc("Word that must occur in url to be selected").build());
		CommandLineParser parser = new DefaultParser();
		CommandLine cmd = null;
		try {
			cmd = parser.parse(options, args);
		} catch (MissingOptionException e) {
			String header = "Extract paramters from a log file\n\n";
			String footer = "\nPlease report issues to Ian";

			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("ExtractJmeterParams", header, options, footer, true);
			System.exit(1);
		}
		ExtractJmeterParams me = new ExtractJmeterParams();
		if (cmd.hasOption("term")) {
			me.setMatch(cmd.getOptionValue("term"));
		}
		if (cmd.hasOption("m")) {
			me.setType(TYPE.MICROSOFT);
		}
		String[] cols = cmd.getOptionValues("c");
		String[] inputFiles = new String[] {};
		if (cmd.hasOption('f')) {
			inputFiles = cmd.getOptionValues("f");
		}
		if (cmd.hasOption('s')) {
			me.setSeperator(cmd.getOptionValue('s'));
		}
		PrintStream out = System.out;
		try {

			if (cmd.hasOption('o')) {
				String outputFile = cmd.getOptionValue("o");
				File outf = new File(outputFile);
				out = new PrintStream(new FileOutputStream(outf));
			}

			me.setColumns(cols);
			me.parseFile(inputFiles);

			me.print("URL",me.getMatch(),out);
		} finally {
			if (out != System.out) {
				out.close();
			}
		}

	}

	public void setColumns(String[] strings) {
		columns = new ArrayList<>();
		for (String col : strings) {
			columns.add(col.toUpperCase());
		}
	}

	public String[] getColumns() {
		return columns.toArray(new String[] {});
	}

	public HashMap<Calendar, HashMap<String, String>> getResults() {
		return results;
	}

	public void parseFile(String[] inputFiles) throws FileNotFoundException, IOException, ParseException {

		for (String inputFile : inputFiles) {
			int lineNo=0;
			try (BufferedReader reader = new BufferedReader(new FileReader(new File(inputFile)))) {
				String line;
				Pattern pURL;
				Pattern pTime;
				DateFormat sdf;
				if (type.equals(TYPE.APACHE)) {
					pURL = Pattern.compile("^.*\"GET (.+) HTTP.*$");
					pTime = Pattern.compile("^.*\\[(.+)\\].*$");
					sdf = asdf;
				} else {
					pURL = Pattern.compile("^.*GET (.+?) (.+?) \\d{2,4} .*$");
					// 2017-01-13 08:34:18
					pTime = Pattern.compile("^([\\d-]+ [\\d:]+) .*$");
					sdf = msdf;
				}

				while ((line = reader.readLine()) != null) {
					lineNo++;
					if (line.startsWith("#")) {
						// Microsoft IIS includes "comments" in the file!
						continue;
					}
					HashMap<String, String> params = new HashMap<>();

					Matcher d = pTime.matcher(line);
					Calendar date = Calendar.getInstance();
					if (d.matches()) {
						String dateString = null;
						try {
						dateString = d.group(1);
						if (type.equals(TYPE.APACHE)) {
							dateString = dateString.replaceAll(" ", "");
						}
						date.setTime(sdf.parse(dateString));
						} catch (java.text.ParseException e) {
							System.err.println("Unparsable date: "+dateString+" at line no:"+lineNo+"\n"+line);
							continue;
						}
					}
					Matcher m = pURL.matcher(line);
					if (m.matches()) {
						String query = null;
						try {
							String url;
							if(TYPE.APACHE==type) {
								url=m.group(1);
							}else if(TYPE.MICROSOFT==type) {
								if(m.group(2).equalsIgnoreCase("-")) {
									url = m.group(1);
								}else {
									url = m.group(1)+"?"+m.group(2);
								}
							} else {
								throw new IllegalStateException("Unknown type set");
							}
							query = URLDecoder.decode(url, "UTF-8");
						} catch (IllegalArgumentException e) {
							System.err.println("Unparsable URL: "+query+" at line no:"+lineNo+"\n"+line);
							continue;
						}
						// System.out.println(query);
						String[] parts = query.split("&|;|\\?| ");
						boolean first = true;
						for (String part : parts) {
							if (first) {
								first = false;
								params.put("URL", part);
								if (!columns.contains("URL")) {
									columns.add(0, "URL");
								}
							}
							String[] bits = part.split("=");
							if (bits.length > 1) {
								params.put(bits[0].toUpperCase(), bits[1]);
							} else {
								if(bits.length==1)
									params.put(bits[0], null);
							}
						}
						while (results.containsKey(date)) {// separate the
															// requests
															// (assume no more
															// than
															// 1000 reqs per s)
							date.add(Calendar.MILLISECOND, 1);
						}
						results.put(date, params);

					}
				}
			}
		}
	}

	public void print() {
		print(null, null, System.out);
	}

	public void print(PrintStream out) {
		print(null, null, out);
	}

	public void print(String col, String val) {
		print(col, val, System.out);
	}

	public void print(String column, String value, PrintStream out) {
		String colm = column == null ? null : column.toUpperCase();
		String val = value == null ? null : value.toLowerCase();

		for (String c : getColumns()) {
			out.print(c + seperator);
		}
		out.println();
		for (Calendar d : results.keySet()) {
			HashMap<String, String> params = results.get(d);
			// System.out.println(d+":"+params);
			if (colm != null) {
				if (val != null) {
					if (params.containsKey(colm) && params.get(colm).toLowerCase().contains(val)) {
						for (String col : getColumns()) {
							if (params.containsKey(col)) {
								out.print(params.get(col) + seperator);
							} else {
								out.print(seperator);
							}
						}
						out.println();
					}
				} else {
					if (params.containsKey(colm)) {
						for (String col : getColumns()) {
							if (params.containsKey(col)) {
								out.print(params.get(col) + seperator);
							} else {
								out.print(seperator);
							}
						}
						out.println();
					}
				}
			} else {

				for (String col : getColumns()) {
					if (params.containsKey(col)) {
						out.print(params.get(col) + seperator);
					} else {
						out.print(seperator);
					}
				}
				out.println();

			}
		}
	}

	public String getSeperator() {
		return seperator;
	}

	public void setSeperator(String seperator) {
		this.seperator = seperator;
	}

	public TYPE getType() {
		return type;
	}

	public void setType(TYPE type) {
		this.type = type;
	}

	public String getMatch() {
		return match;
	}

	public void setMatch(String match) {
		this.match = match;
	}

}
